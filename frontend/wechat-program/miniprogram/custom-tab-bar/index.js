const { openPage } = require('../config/tab-bar')
const { badgeKeyForPath } = require('../utils/tab-badge')
const { buildTabBarState, resolveTabSession } = require('../utils/tab-bar-state')

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

  pageLifetimes: {
    show() {
      this.initTabBar()
    },
  },

  methods: {
    initTabBar() {
      const route = getCurrentPages().slice(-1)[0]?.route || ''
      const pagePath = route ? `/${route}` : ''
      const next = buildTabBarState(pagePath)
      this._role = next.role
      this.setData({
        list: next.list,
        selected: next.selected,
        badges: next.badges,
      })
      const app = getApp()
      if (app.registerTabBar) app.registerTabBar(this)
    },

    syncBadges() {
      const route = getCurrentPages().slice(-1)[0]?.route || ''
      const pagePath = route ? `/${route}` : ''
      const next = buildTabBarState(pagePath)
      this.setData({
        badges: next.badges,
        selected: next.selected,
      })
    },

    setSelected(pagePath) {
      const idx = this.data.list.findIndex((t) => t.pagePath === pagePath)
      if (idx >= 0) {
        this.setData({ selected: idx, badges: buildTabBarState(pagePath).badges })
      } else {
        this.initTabBar()
      }
    },

    updateBadges(badges) {
      this.setData({ badges: { ...badges } })
    },

    switchTab(e) {
      const { path } = e.currentTarget.dataset
      const app = getApp()
      const { role, permissions } = resolveTabSession()
      const badgeKey = badgeKeyForPath(path)
      if (badgeKey && app.dismissTabBadge) app.dismissTabBadge(badgeKey)
      openPage(path, permissions, role)
    },
  },
})
