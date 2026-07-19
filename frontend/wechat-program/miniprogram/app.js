const api = require('./services/index')
const apiConfig = require('./config/api')
const { countWorkOrderBadge } = require('./utils/work-order-badge')

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

  restoreSession() {
    const session = api.getSession()
    if (session) {
      this.applySession(session)
      api.refreshMe().then((fresh) => {
        if (fresh) this.applySession(fresh)
      }).catch(() => {
        // request.js 已根据响应特征区分“token 失效”与“网络抖动”：
        // 前者会清掉 pi_session，此时才需要退出登录态；网络抖动时 session 仍在，
        // 保留乐观展示，避免偶发超时就把用户强制登出。
        if (!api.getSession()) {
          this.clearUser()
        }
      })
    }
  },

  applySession(session) {
    if (!session?.user || !Array.isArray(session.permissions) || !session.permissions.length) {
      this.clearUser()
      return
    }
    this.globalData.user = session.user
    this.globalData.permissions = session.permissions
    this.refreshBadges()
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
    try {
      const [ntf, alarms, orders] = await Promise.all([
        api.getNotifications(user.id),
        api.getAlarms(),
        api.getWorkOrders().catch(() => []),
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
    this.applySession(session)
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
  },

  clearUser() {
    this.globalData.user = null
    this.globalData.permissions = []
    this.globalData.unreadNotifications = 0
    this.globalData.unackAlarms = 0
    this.globalData.pendingWorkOrders = 0
    this.clearTabBarBadges()
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
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
