const { hasPermission } = require('./permission')

function isWorkOrderAssignee(order, user) {
  if (!user || !order) return false
  return order.assigneeId === user.id
    || order.assigneeName === user.displayName
    || order.assigneeName === user.name
}

function canCreateWorkOrder(user) {
  return !!user && hasPermission(user.role, 'workorder:create')
}

function canAcceptOrder(order, user) {
  if (!user || order.status !== 'PENDING') return false
  if (hasPermission(user.role, 'workorder:assign')) return true
  return hasPermission(user.role, 'workorder:process') && isWorkOrderAssignee(order, user)
}

function canSubmitReview(order, user) {
  if (!user || order.status !== 'PROCESSING') return false
  if (user.role !== 'DISPATCHER') return false
  return hasPermission(user.role, 'workorder:process') && isWorkOrderAssignee(order, user)
}

function canConfirmReview(order, user) {
  return !!(user && order.status === 'REVIEW' && hasPermission(user.role, 'workorder:review'))
}

function assertCanCreateWorkOrder(user) {
  if (!user) throw new Error('未登录')
  if (!canCreateWorkOrder(user)) throw new Error('无转工单权限')
}

function assertCanAcceptOrder(order, user) {
  if (!user) throw new Error('未登录')
  if (!canAcceptOrder(order, user)) throw new Error('无接单权限')
}

function assertCanSubmitReview(order, user) {
  if (!user) throw new Error('未登录')
  if (!canSubmitReview(order, user)) throw new Error('仅指派调度员可提交复核')
}

function assertCanConfirmReview(order, user) {
  if (!user) throw new Error('未登录')
  if (!canConfirmReview(order, user)) throw new Error('仅管理员可确认复核')
}

function assertStatusTransition(order, nextStatus, user) {
  const cur = order.status
  if (nextStatus === 'PROCESSING' && cur === 'PENDING') {
    assertCanAcceptOrder(order, user)
    return
  }
  if (nextStatus === 'REVIEW' && cur === 'PROCESSING') {
    assertCanSubmitReview(order, user)
    return
  }
  if (cur === 'REVIEW' && (nextStatus === 'CLOSED' || nextStatus === 'PROCESSING')) {
    assertCanConfirmReview(order, user)
    return
  }
  throw new Error('不允许的状态变更')
}

module.exports = {
  isWorkOrderAssignee,
  canCreateWorkOrder,
  canAcceptOrder,
  canSubmitReview,
  canConfirmReview,
  assertCanCreateWorkOrder,
  assertCanAcceptOrder,
  assertCanSubmitReview,
  assertCanConfirmReview,
  assertStatusTransition,
}
