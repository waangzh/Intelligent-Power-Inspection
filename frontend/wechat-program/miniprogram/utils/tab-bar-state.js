const { getTabList } = require('../config/tab-bar')

/** 始终以 storage + globalData 合并读取，避免切换账号后底栏仍用旧角色 */
function resolveTabSession() {
  const app = typeof getApp === 'function' ? getApp() : null
  let user = app?.globalData?.user || null
  let permissions = Array.isArray(app?.globalData?.permissions) ? app.globalData.permissions : []

  try {
    const raw = wx.getStorageSync('pi_session')
    if (raw?.user?.role) {
      user = raw.user
    }
    if (Array.isArray(raw?.permissions) && raw.permissions.length) {
      permissions = raw.permissions
    }
  } catch {
    // ignore
  }

  return {
    role: user?.role || '',
    permissions,
  }
}

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
  const { role, permissions } = resolveTabSession()
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
  resolveTabSession,
  buildTabBarState,
  readTabBarBadges,
}
