const api = require('./services/index')
const apiConfig = require('./config/api')
const { countWorkOrderBadge } = require('./utils/work-order-badge')
const { hasPermission, canViewWorkOrders } = require('./utils/permission')
const {
  getDisplayBadges,
  dismissTabBadge,
  clearBadgeDismissState,
} = require('./utils/tab-badge')

App({
  globalData: {
    user: null,
    permissions: [],
    unreadNotifications: 0,
    unackAlarms: 0,
    pendingWorkOrders: 0,
    badgeDismissedAt: {},
    tabBarComponent: null,
  },

  onLaunch() {
    if (apiConfig.mockMode === 'openapi') {
      console.warn('[power-inspection] OpenAPI Mock 模式：请求', apiConfig.baseUrl)
    } else {
      console.info('[power-inspection] 真实后端模式：', apiConfig.baseUrl)
    }
    const { invalidateSessionIfApiBaseChanged } = require('./utils/request')
    if (invalidateSessionIfApiBaseChanged()) {
      console.warn('[power-inspection] API 地址已变更，已清除旧登录状态')
    }
    this.restoreSession()
  },

  reloadVisiblePages() {
    getCurrentPages().forEach((page) => {
      if (page && typeof page.load === 'function') {
        Promise.resolve(page.load()).catch(() => {})
      }
    })
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
  },

  restoreSession() {
    const session = api.getSession()
    if (!session) return
    const { persistSessionUser } = require('./utils/session-user')
    persistSessionUser(session.user, session.permissions)
    const { ensureSessionFresh } = require('./utils/request')
    ensureSessionFresh()
      .then(() => api.refreshMe())
      .then((fresh) => {
        if (fresh) {
          this.applySession(fresh, { reloadPages: false, relaunch: true })
        } else if (api.getSession()) {
          this.refreshBadges()
          this.scheduleEnterMainApp()
        }
        if (!api.getSession()) {
          this.clearUser({ redirect: true })
        }
      })
      .catch(() => {
        if (!api.getSession()) {
          this.clearUser({ redirect: true })
          return
        }
        this.refreshBadges()
        this.scheduleEnterMainApp()
      })
  },

  scheduleEnterMainApp(url) {
    const run = () => this.enterMainApp(url)
    if (this._enterMainScheduled) return
    this._enterMainScheduled = true
    const done = () => { this._enterMainScheduled = false }
    if (typeof wx.nextTick === 'function') {
      wx.nextTick(() => run().finally(done))
    } else {
      setTimeout(() => run().finally(done), 100)
    }
  },

  applySession(session, options = {}) {
    if (!session?.user || !Array.isArray(session.permissions) || !session.permissions.length) {
      this.clearUser()
      return
    }
    const prevRole = this.globalData.user?.role
    const { normalizeRole, persistSessionUser } = require('./utils/session-user')
    const user = persistSessionUser(session.user, session.permissions)
    this.globalData.user = user
    this.globalData.permissions = session.permissions
    if (!options.skipBadges) {
      this.refreshBadges()
    }
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
    this.refreshCurrentInlineTabBar()
    if (options.reloadPages === true) {
      this.reloadVisiblePages()
    }
    const roleChanged = prevRole && normalizeRole(prevRole) !== user.role
    if (options.relaunch || roleChanged) {
      this.relaunchToRoleHome(options.landingUrl)
    }
  },

  relaunchToRoleHome(url) {
    if (!this.globalData.user) return Promise.resolve()
    const { enterMainApp } = require('./config/tab-bar')
    const { getRoleLandingPath } = require('./utils/role-landing')
    const { resolveSession } = require('./utils/session-user')
    const { role, permissions } = resolveSession()
    return enterMainApp(
      url || getRoleLandingPath(role),
      permissions,
      role,
    )
  },

  enterMainApp(url) {
    if (!this.globalData.user) return Promise.resolve()
    const pages = getCurrentPages()
    const route = pages[pages.length - 1]?.route || ''
    if (!route.startsWith('pages/auth/login')) return Promise.resolve()
    const { enterMainApp } = require('./config/tab-bar')
    const { getRoleLandingPath } = require('./utils/role-landing')
    return enterMainApp(
      url || getRoleLandingPath(this.globalData.user.role),
      this.globalData.permissions,
      this.globalData.user.role,
    )
  },

  registerTabBar(component) {
    this.globalData.tabBarComponent = component
    this.syncAllTabBarBadges()
  },

  dismissTabBadge(key) {
    dismissTabBadge(this.globalData, key)
    this.syncAllTabBarBadges()
  },

  getTabBarBadgeCounts() {
    return getDisplayBadges(this.globalData)
  },

  refreshCurrentInlineTabBar() {
    try {
      const pages = getCurrentPages()
      const page = pages[pages.length - 1]
      page?.selectComponent?.('#inlineTabBar')?.refresh?.()
    } catch {
      // ignore
    }
  },

  async refreshBadges() {
    const user = this.globalData.user
    if (!user) {
      this.clearTabBarBadges()
      return
    }
    const shouldLoadWorkOrders = canViewWorkOrders(user.role, this.globalData.permissions)
    const needAlarms = user.role !== 'DISPATCHER'
    try {
      const [ntf, alarms, orders] = await Promise.all([
        api.getNotifications(user.id),
        needAlarms ? api.getAlarms() : Promise.resolve([]),
        shouldLoadWorkOrders ? api.getWorkOrders() : Promise.resolve([]),
      ])
      this.globalData.unreadNotifications = ntf.filter((n) => !n.read).length
      this.globalData.unackAlarms = alarms.filter((a) => !a.acknowledged).length
      this.globalData.pendingWorkOrders = countWorkOrderBadge(orders, user, this.globalData.permissions)
      this.syncAllTabBarBadges()
    } catch (e) {
      console.warn('refreshBadges', e)
    }
  },

  syncAllTabBarBadges() {
    this.applyTabBarBadges()
    if (typeof wx.nextTick === 'function') {
      wx.nextTick(() => this.applyTabBarBadges())
    }
  },

  applyTabBarBadges() {
    const badges = getDisplayBadges(this.globalData)
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.updateBadges === 'function') {
      tabBar.updateBadges(badges)
    } else if (tabBar && typeof tabBar.syncBadges === 'function') {
      tabBar.syncBadges()
    } else {
      try {
        if (badges.alarms > 0) {
          wx.setTabBarBadge({ index: 2, text: badges.alarms > 99 ? '99+' : String(badges.alarms) })
        } else {
          wx.removeTabBarBadge({ index: 2 })
        }
        if (badges.profile > 0) {
          wx.setTabBarBadge({ index: 4, text: badges.profile > 99 ? '99+' : String(badges.profile) })
        } else {
          wx.removeTabBarBadge({ index: 4 })
        }
      } catch (e) {
        // tab bar may not be ready
      }
    }
    this.refreshCurrentInlineTabBar()
  },

  clearTabBarBadges() {
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.updateBadges === 'function') {
      tabBar.updateBadges({ alarms: 0, workorders: 0, profile: 0 })
    }
    try {
      wx.removeTabBarBadge({ index: 2 })
      wx.removeTabBarBadge({ index: 4 })
    } catch (e) {
      // ignore
    }
  },

  setUser(user) {
    const session = api.getSession()
    if (!session || session.user?.id !== user?.id) {
      this.clearUser()
      return
    }
    this.globalData.user = user
    this.refreshBadges()
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
  },

  setSession(session) {
    this.applySession(session, { reloadPages: false })
  },

  handleSessionExpired() {
    this.clearUser({ redirect: true })
  },

  clearUser(options = {}) {
    const wasLoggedIn = !!this.globalData.user
    this.globalData.user = null
    this.globalData.permissions = []
    this.globalData.unreadNotifications = 0
    this.globalData.unackAlarms = 0
    this.globalData.pendingWorkOrders = 0
    clearBadgeDismissState(this.globalData)
    this.clearTabBarBadges()
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
    if (options.redirect !== false && wasLoggedIn) {
      const pages = getCurrentPages()
      const route = pages[pages.length - 1]?.route || ''
      if (route && !route.startsWith('pages/auth/')) {
        wx.redirectTo({ url: '/pages/auth/login/index' })
      }
    }
  },

  syncSessionFromStorage() {
    const session = api.getSession()
    if (!session) return false
    const { persistSessionUser } = require('./utils/session-user')
    persistSessionUser(session.user, session.permissions)
    return true
  },

  requireAuth(redirectUrl) {
    this.syncSessionFromStorage()
    if (!this.globalData.user) {
      const url = redirectUrl ? `/pages/auth/login/index?redirect=${encodeURIComponent(redirectUrl)}` : '/pages/auth/login/index'
      wx.redirectTo({ url })
      return false
    }
    return true
  },

  requirePermission(permission, roles) {
    this.syncSessionFromStorage()
    const { hasPermission, canAccessByRole } = require('./utils/permission.js')
    const user = this.globalData.user
    const role = user && user.role
    const permissions = this.globalData.permissions
    if (roles && roles.length && !canAccessByRole(role, roles)) {
      wx.redirectTo({ url: '/pages/forbidden/index' })
      return false
    }
    if (permission && !hasPermission(permissions, permission)) {
      wx.redirectTo({ url: '/pages/forbidden/index' })
      return false
    }
    return true
  },
})
