const { getTabList, openPage } = require('../config/tab-bar')

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
      const list = getTabList(app.globalData.permissions, user?.role)
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
      const app = getApp()
      openPage(path, app.globalData.permissions, app.globalData.user?.role)
      if (index !== undefined) this.setData({ selected: Number(index) })
    },
  },
})
