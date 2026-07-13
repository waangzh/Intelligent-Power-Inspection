const { getTabList, isNativeTabPage } = require('../config/tab-bar')

Component({
  data: {
    selected: 0,
    list: [],
    badges: {
      alarms: 0,
      workorders: 0,
      profile: 0,
    },
  },

  lifetimes: {
    attached() {
      this.initTabBar()
    },
  },

  methods: {
    initTabBar() {
      const app = getApp()
      const user = app.globalData.user
      const list = getTabList(user?.role)
      this.setData({ list })
      if (app.registerTabBar) app.registerTabBar(this)
      if (app.applyTabBarBadges) app.applyTabBarBadges()
    },

    setSelected(pagePath) {
      const idx = this.data.list.findIndex((t) => t.pagePath === pagePath)
      if (idx >= 0) this.setData({ selected: idx })
    },

    updateBadges(badges) {
      this.setData({ badges: { ...this.data.badges, ...badges } })
    },

    switchTab(e) {
      const { path, index } = e.currentTarget.dataset
      if (isNativeTabPage(path)) {
        wx.switchTab({ url: path })
      } else {
        wx.navigateTo({ url: path })
      }
      this.setData({ selected: Number(index) })
    },
  },
})
