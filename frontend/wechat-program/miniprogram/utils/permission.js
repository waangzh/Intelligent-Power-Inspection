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
  return hasPermission(permissions, 'task:control') || hasPermission(permissions, 'task:estop')
}

function canEstopTask(permissions) {
  return hasPermission(permissions, 'task:estop')
}

const TERMINAL_TASK_STATUSES = ['COMPLETED', 'CANCELLED', 'ESTOPPED']
const ESTOP_TASK_STATUSES = ['RUNNING', 'PAUSED', 'DISPATCHED', 'MANUAL_TAKEOVER', 'STARTING']
const CANCEL_TASK_STATUSES = ['CREATED', ...ESTOP_TASK_STATUSES]

function canShowTaskEstop(task, permissions) {
  if (!canEstopTask(permissions)) return false
  const status = task?.status
  return !!status && ESTOP_TASK_STATUSES.includes(status)
}

function canShowTaskCancel(task, permissions) {
  if (!hasPermission(permissions, 'task:control')) return false
  const status = task?.status
  return !!status && CANCEL_TASK_STATUSES.includes(status)
}

function isEmergencyCancel(permissions) {
  return hasPermission(permissions, 'task:estop') && !hasPermission(permissions, 'task:control')
}

function cancelTaskLabel(permissions) {
  return isEmergencyCancel(permissions) ? '急停' : '取消'
}

module.exports = {
  hasPermission,
  canAccessByRole,
  canAccess,
  canControlTask,
  canTakeoverTask,
  canCancelTask,
  canEstopTask,
  canShowTaskEstop,
  canShowTaskCancel,
  isEmergencyCancel,
  cancelTaskLabel,
}
