const { getVisibleMenuGroups } = require('../../config/menu')

const TAB_PATHS = [
  '/pages/dashboard/index', '/pages/monitor/index', '/pages/alarms/index',
  '/pages/tasks/index', '/pages/profile/info/index',
]

Page({
  data: { menuGroups: [], unreadNotifications: 0 },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/more/index')) return
    const role = app.globalData.user.role
    this.setData({
      menuGroups: getVisibleMenuGroups(role),
      unreadNotifications: app.globalData.unreadNotifications,
    })
    app.refreshBadges()
  },

  go(e) {
    const url = e.currentTarget.dataset.url
    if (TAB_PATHS.includes(url)) wx.switchTab({ url })
    else wx.navigateTo({ url })
  },
})
