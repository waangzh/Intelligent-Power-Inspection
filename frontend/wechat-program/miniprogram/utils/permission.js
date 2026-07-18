function hasPermission(permissions, permission) {
  if (!permissions?.length) return false
  return permissions.includes(permission)
}

function canAccessByRole(role, allowedRoles) {
  if (!allowedRoles || !allowedRoles.length) return true
  return !!role && allowedRoles.includes(role)
}

function canAccess(permissions, role, rule) {
  if (!rule) return true
  if (rule.roles && rule.roles.length && !canAccessByRole(role, rule.roles)) return false
  if (rule.permission && !hasPermission(permissions, rule.permission)) return false
  return true
}

function canControlTask(permissions) {
  return hasPermission(permissions, 'task:control')
}

function canTakeoverTask(permissions) {
  return hasPermission(permissions, 'task:takeover')
}

function canCancelTask(permissions) {
  return hasPermission(permissions, 'task:control')
}

function canEstopTask(permissions) {
  return hasPermission(permissions, 'task:estop')
}

function isEmergencyCancel(permissions) {
  return canEstopTask(permissions) && !canControlTask(permissions)
}

function cancelTaskLabel(permissions) {
  return canEstopTask(permissions) && !canControlTask(permissions) ? '急停' : '取消'
}

module.exports = {
  hasPermission,
  canAccessByRole,
  canAccess,
  canControlTask,
  canTakeoverTask,
  canCancelTask,
  canEstopTask,
  isEmergencyCancel,
  cancelTaskLabel,
}
