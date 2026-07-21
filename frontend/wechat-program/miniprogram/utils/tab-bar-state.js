const { getTabList } = require('../config/tab-bar')
const { resolveSession } = require('./session-user')

function readTabBarBadges() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app?.getTabBarBadgeCounts) return app.getTabBarBadgeCounts()
  return {
    alarms: app?.globalData?.unackAlarms || 0,
    workorders: app?.globalData?.pendingWorkOrders || 0,
    profile: app?.globalData?.unreadNotifications || 0,
  }
}

function buildTabBarState(activePath) {
  const { role, permissions } = resolveSession()
  const list = getTabList(permissions, role)
  let selected = 0
  if (activePath) {
    const idx = list.findIndex((t) => t.pagePath === activePath)
    if (idx >= 0) selected = idx
  }
  return {
    role,
    list,
    selected,
    badges: readTabBarBadges(),
  }
}

module.exports = {
  resolveTabSession: resolveSession,
  buildTabBarState,
  readTabBarBadges,
}
