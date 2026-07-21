const { USER_ROLE_VALUES } = require('../generated/domain-enums')

function normalizeRole(role) {
  const value = String(role || '').trim().toUpperCase()
  if (USER_ROLE_VALUES.includes(value)) return value
  return value
}

/** 以 storage 为准同步 globalData，全站统一读角色/权限 */
function resolveSession() {
  let stored = null
  try {
    stored = wx.getStorageSync('pi_session') || null
  } catch {
    // ignore
  }

  const app = typeof getApp === 'function' ? getApp() : null
  let user = stored?.user || app?.globalData?.user || null
  let permissions = Array.isArray(stored?.permissions) && stored.permissions.length
    ? stored.permissions
    : (Array.isArray(app?.globalData?.permissions) ? app.globalData.permissions : [])

  if (user) {
    user = { ...user, role: normalizeRole(user.role) }
  }

  if (app && user) {
    app.globalData.user = user
    if (permissions.length) app.globalData.permissions = permissions
  }

  return {
    user,
    role: user?.role || '',
    permissions,
  }
}

function persistSessionUser(user, permissions) {
  if (!user) return
  const normalizedUser = { ...user, role: normalizeRole(user.role) }
  let stored = null
  try {
    stored = wx.getStorageSync('pi_session') || {}
  } catch {
    stored = {}
  }
  const next = {
    ...stored,
    user: normalizedUser,
    permissions: permissions || stored.permissions || [],
  }
  wx.setStorageSync('pi_session', next)
  const app = typeof getApp === 'function' ? getApp() : null
  if (app) {
    app.globalData.user = normalizedUser
    if (next.permissions?.length) app.globalData.permissions = next.permissions
  }
  return normalizedUser
}

module.exports = {
  normalizeRole,
  resolveSession,
  persistSessionUser,
}
