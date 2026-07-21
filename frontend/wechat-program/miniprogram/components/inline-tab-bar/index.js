const { getTabList, openPage } = require('../../config/tab-bar')

Component({
  properties: {
    activePath: {
      type: String,
      value: '',
    },
  },

  data: {
    list: [],
    selected: -1,
    badges: {
      alarms: 0,
      workorders: 0,
      profile: 0,
    },
  },

  lifetimes: {
    attached() {
      this.refresh()
    },
  },

  pageLifetimes: {
    show() {
      this.refresh()
    },
  },

  methods: {
    refresh() {
      const app = getApp()
      const list = getTabList(app.globalData.permissions, app.globalData.user?.role)
      const activePath = this.properties.activePath
      const selected = activePath ? list.findIndex((t) => t.pagePath === activePath) : -1
      this.setData({
        list,
        selected,
        badges: {
          alarms: app.globalData.unackAlarms || 0,
          workorders: app.globalData.pendingWorkOrders || 0,
          profile: app.globalData.unreadNotifications || 0,
        },
      })
    },

    onTap(e) {
      const { path } = e.currentTarget.dataset
      if (!path || path === this.properties.activePath) return
      const app = getApp()
      openPage(path, app.globalData.permissions, app.globalData.user?.role)
    },
  },
})
