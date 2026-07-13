const api = require('./services/index')
const { countWorkOrderBadge } = require('./utils/work-order-badge')

App({
  globalData: {
    user: null,
    unreadNotifications: 0,
    unackAlarms: 0,
    pendingWorkOrders: 0,
    tabBarComponent: null,
  },

  onLaunch() {
    this.restoreSession()
  },

  restoreSession() {
    const session = api.getSession()
    if (session) {
      this.globalData.user = session.user
      this.refreshBadges()
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
    try {
      const [ntf, alarms, orders] = await Promise.all([
        api.getNotifications(user.id),
        api.getAlarms(),
        api.getWorkOrders().catch(() => []),
      ])
      this.globalData.unreadNotifications = ntf.filter((n) => !n.read).length
      this.globalData.unackAlarms = alarms.filter((a) => !a.acknowledged).length
      this.globalData.pendingWorkOrders = countWorkOrderBadge(orders, user)
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
    this.globalData.user = user
    this.refreshBadges()
    const tabBar = this.globalData.tabBarComponent
    if (tabBar && typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
  },

  clearUser() {
    this.globalData.user = null
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
    const role = this.globalData.user && this.globalData.user.role
    if (roles && roles.length && !canAccessByRole(role, roles)) {
      wx.redirectTo({ url: '/pages/forbidden/index' })
      return false
    }
    if (permission && !hasPermission(role, permission)) {
      wx.redirectTo({ url: '/pages/forbidden/index' })
      return false
    }
    return true
  },
})
