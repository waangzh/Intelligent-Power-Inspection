function hasPermission(permissions, permission) {
  if (!permissions?.length) return false
  return permissions.includes(permission)
}

function canViewWorkOrders(role, permissions) {
  return hasPermission(permissions, 'workorder:view')
    || String(role || '').trim().toUpperCase() === 'VIEWER'
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
const ESTOP_TASK_STATUSES = [
  'RUNNING', 'PAUSED', 'DISPATCHED', 'MANUAL_TAKEOVER', 'STARTING',
  'WAITING_LOCAL_CONFIRM', 'DISCONNECTED', 'RECOVERING',
]
const CANCEL_TASK_STATUSES = [
  'CREATED', 'START_FAILED', 'DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER',
  'STARTING', 'WAITING_LOCAL_CONFIRM',
]

function canShowTaskEstop(task, permissions) {
  if (!canEstopTask(permissions)) return false
  const status = task?.displayStatus || task?.status
  if (!status || ['COMPLETED', 'CANCELLED', 'ESTOPPED', 'ESTOPPING', 'CANCELLING', 'FAILED'].includes(status)) {
    return false
  }
  if (task?.executionId) return ESTOP_TASK_STATUSES.includes(status)
  return ['CREATED', ...ESTOP_TASK_STATUSES].includes(status)
}

function canShowTaskCancel(task, permissions) {
  if (!hasPermission(permissions, 'task:control')) return false
  const status = task?.displayStatus || task?.status
  if (!status || ['COMPLETED', 'CANCELLED', 'ESTOPPED'].includes(status)) return false
  if (task?.executionId) {
    return ['STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSED'].includes(status)
  }
  return CANCEL_TASK_STATUSES.includes(status)
}

function isEmergencyCancel(permissions) {
  return hasPermission(permissions, 'task:estop') && !hasPermission(permissions, 'task:control')
}

function cancelTaskLabel(permissions) {
  return isEmergencyCancel(permissions) ? '急停' : '取消'
}

module.exports = {
  hasPermission,
  canViewWorkOrders,
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
