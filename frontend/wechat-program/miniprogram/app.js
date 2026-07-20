const api = require('./services/index')
const apiConfig = require('./config/api')
const { countWorkOrderBadge } = require('./utils/work-order-badge')
const { hasPermission } = require('./utils/permission')

App({
  globalData: {
    user: null,
    permissions: [],
    unreadNotifications: 0,
    unackAlarms: 0,
    pendingWorkOrders: 0,
    tabBarComponent: null,
  },

  onLaunch() {
    if (apiConfig.mockMode === 'openapi') {
      console.warn('[power-inspection] OpenAPI Mock 模式：请求', apiConfig.baseUrl)
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
    // 先恢复本地展示，等 token 续期 /auth/me 成功后再拉 badge，避免启动时过期 token 刷屏 401。
    this.globalData.user = session.user
    this.globalData.permissions = session.permissions
    const { ensureSessionFresh } = require('./utils/request')
    ensureSessionFresh()
      .then(() => api.refreshMe())
      .then((fresh) => {
        if (fresh) {
          this.applySession(fresh, { reloadPages: true })
          return
        }
        if (!api.getSession()) {
          this.clearUser({ redirect: true })
        }
      })
      .catch(() => {
        if (!api.getSession()) {
          this.clearUser({ redirect: true })
        }
      })
  },

  applySession(session, options = {}) {
    if (!session?.user || !Array.isArray(session.permissions) || !session.permissions.length) {
      this.clearUser()
      return
    }
    const prevUserId = this.globalData.user?.id
    const prevUserSig = JSON.stringify(this.globalData.user || null)
    const prevPerms = JSON.stringify(this.globalData.permissions || [])
    this.globalData.user = session.user
    this.globalData.permissions = session.permissions
    if (!options.skipBadges) {
      this.refreshBadges()
    }
    const sessionChanged = prevUserId !== session.user.id
      || prevUserSig !== JSON.stringify(session.user)
      || prevPerms !== JSON.stringify(session.permissions)
    // 显式要求重载时始终刷新页面（如 refreshMe / 重新登录）；
    // 默认仅在用户或权限变化时重载，避免无意义重复请求。
    if (options.reloadPages === true || (options.reloadPages !== false && sessionChanged)) {
      this.reloadVisiblePages()
    }
  },

  registerTabBar(component) {
    this.globalData.tabBarComponent = component
    this.applyTabBarBadges()
  },

  async refreshBadges() {
    const user = this.globalData.user
    if (!user) {
      this.clearTabBarBadges()
      return
    }
    const canViewWorkOrders = hasPermission(this.globalData.permissions, 'workorder:view')
    try {
      const [ntf, alarms, orders] = await Promise.all([
        api.getNotifications(user.id),
        api.getAlarms(),
        canViewWorkOrders ? api.getWorkOrders() : Promise.resolve([]),
      ])
      this.globalData.unreadNotifications = ntf.filter((n) => !n.read).length
      this.globalData.unackAlarms = alarms.filter((a) => !a.acknowledged).length
      this.globalData.pendingWorkOrders = countWorkOrderBadge(orders, user, this.globalData.permissions)
      this.applyTabBarBadges()
    } catch (e) {
      console.warn('refreshBadges', e)
    }
  },

  applyTabBarBadges() {
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.updateBadges === 'function') {
      tabBar.updateBadges({
        alarms: this.globalData.unackAlarms,
        workorders: this.globalData.pendingWorkOrders,
        profile: this.globalData.unreadNotifications,
      })
      return
    }
    try {
      if (this.globalData.unackAlarms > 0) {
        wx.setTabBarBadge({ index: 2, text: this.globalData.unackAlarms > 99 ? '99+' : String(this.globalData.unackAlarms) })
      } else {
        wx.removeTabBarBadge({ index: 2 })
      }
      if (this.globalData.unreadNotifications > 0) {
        wx.setTabBarBadge({ index: 4, text: this.globalData.unreadNotifications > 99 ? '99+' : String(this.globalData.unreadNotifications) })
      } else {
        wx.removeTabBarBadge({ index: 4 })
      }
    } catch (e) {
      // tab bar may not be ready
    }
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
    this.applySession(session, { reloadPages: true })
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

  requireAuth(redirectUrl) {
    if (!this.globalData.user) {
      const url = redirectUrl ? `/pages/auth/login/index?redirect=${encodeURIComponent(redirectUrl)}` : '/pages/auth/login/index'
      wx.redirectTo({ url })
      return false
    }
    return true
  },

  requirePermission(permission, roles) {
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
