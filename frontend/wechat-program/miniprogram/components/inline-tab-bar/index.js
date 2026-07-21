const { openPage } = require('../../config/tab-bar')
const { badgeKeyForPath } = require('../../utils/tab-badge')
const { buildTabBarState, resolveTabSession } = require('../../utils/tab-bar-state')

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
      const activePath = this.properties.activePath
      const next = buildTabBarState(activePath)
      const roleChanged = this._role !== next.role
      const listChanged = roleChanged || JSON.stringify(this.data.list) !== JSON.stringify(next.list)
      this._role = next.role
      this.setData({
        ...(listChanged ? { list: next.list } : {}),
        selected: next.selected,
        badges: next.badges,
      })
    },

    onTap(e) {
      const { path } = e.currentTarget.dataset
      if (!path || path === this.properties.activePath) return
      const app = getApp()
      const { role, permissions } = resolveTabSession()
      const badgeKey = badgeKeyForPath(path)
      if (badgeKey && app.dismissTabBadge) app.dismissTabBadge(badgeKey)
      openPage(path, permissions, role)
    },
  },
})
