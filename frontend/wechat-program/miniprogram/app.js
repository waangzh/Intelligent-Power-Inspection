const api = require('./services/index')

App({
  globalData: {
    user: null,
    unreadNotifications: 0,
    unackAlarms: 0,
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

  async refreshBadges() {
    const user = this.globalData.user
    if (!user) {
      this.clearTabBarBadges()
      return
    }
    try {
      const [ntf, alarms] = await Promise.all([
        api.getNotifications(user.id),
        api.getAlarms(),
      ])
      this.globalData.unreadNotifications = ntf.filter((n) => !n.read).length
      this.globalData.unackAlarms = alarms.filter((a) => !a.acknowledged).length
      this.applyTabBarBadges()
    } catch (e) {
      console.warn('refreshBadges', e)
    }
  },

  applyTabBarBadges() {
    const alarmCount = this.globalData.unackAlarms
    const ntfCount = this.globalData.unreadNotifications
    if (alarmCount > 0) {
      wx.setTabBarBadge({
        index: 2,
        text: alarmCount > 99 ? '99+' : String(alarmCount),
      })
    } else {
      wx.removeTabBarBadge({ index: 2 })
    }
    if (ntfCount > 0) {
      wx.setTabBarBadge({
        index: 4,
        text: ntfCount > 99 ? '99+' : String(ntfCount),
      })
    } else {
      wx.removeTabBarBadge({ index: 4 })
    }
  },

  clearTabBarBadges() {
    try {
      wx.removeTabBarBadge({ index: 2 })
      wx.removeTabBarBadge({ index: 4 })
    } catch (e) {
      // tab bar may not be ready on cold start
    }
  },

  setUser(user) {
    this.globalData.user = user
    this.refreshBadges()
  },

  clearUser() {
    this.globalData.user = null
    this.globalData.unreadNotifications = 0
    this.globalData.unackAlarms = 0
    this.clearTabBarBadges()
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
