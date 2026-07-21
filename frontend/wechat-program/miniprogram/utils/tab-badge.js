const COUNT_KEYS = {
  workorders: 'pendingWorkOrders',
  profile: 'unreadNotifications',
  alarms: 'unackAlarms',
}

function effectiveCount(actual, dismissedAt) {
  const n = Number(actual) || 0
  if (n <= 0) return 0
  if (dismissedAt === undefined || dismissedAt === null) return n
  return n > dismissedAt ? n : 0
}

const { normalizeRole } = require('./session-user')

/** 调度员 Tab 角标：仅工单、我的；其余角色保持原逻辑 */
function getDisplayBadges(globalData) {
  const role = normalizeRole(globalData.user?.role)
  const dismissed = globalData.badgeDismissedAt || {}
  const badges = {
    alarms: 0,
    workorders: 0,
    profile: 0,
  }

  if (role === 'DISPATCHER') {
    badges.workorders = effectiveCount(globalData.pendingWorkOrders, dismissed.workorders)
    badges.profile = effectiveCount(globalData.unreadNotifications, dismissed.profile)
    return badges
  }

  if (role === 'VIEWER') {
    badges.alarms = effectiveCount(globalData.unackAlarms, dismissed.alarms)
    badges.profile = effectiveCount(globalData.unreadNotifications, dismissed.profile)
    return badges
  }

  badges.alarms = effectiveCount(globalData.unackAlarms, dismissed.alarms)
  badges.workorders = effectiveCount(globalData.pendingWorkOrders, dismissed.workorders)
  badges.profile = effectiveCount(globalData.unreadNotifications, dismissed.profile)
  return badges
}

function dismissTabBadge(globalData, key) {
  const countKey = COUNT_KEYS[key]
  if (!countKey || !globalData) return
  if (!globalData.badgeDismissedAt) globalData.badgeDismissedAt = {}
  globalData.badgeDismissedAt[key] = globalData[countKey] || 0
}

function clearBadgeDismissState(globalData) {
  if (globalData) globalData.badgeDismissedAt = {}
}

function badgeKeyForPath(pagePath) {
  if (pagePath === '/pages/dashboard/index') return 'alarms'
  if (pagePath === '/pages/alarms/index') return 'alarms'
  if (pagePath === '/pages/workorders/index') return 'workorders'
  if (pagePath === '/pages/profile/info/index') return 'profile'
  return ''
}

function dismissKeyForRoute(route) {
  const path = route ? (route.startsWith('/') ? route : `/${route}`) : ''
  return badgeKeyForPath(path)
}

module.exports = {
  getDisplayBadges,
  dismissTabBadge,
  clearBadgeDismissState,
  badgeKeyForPath,
  dismissKeyForRoute,
}
