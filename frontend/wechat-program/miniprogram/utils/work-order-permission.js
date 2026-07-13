const { hasPermission } = require('./permission')
const { isWorkOrderUnassigned } = require('./work-order')

function isWorkOrderAssignee(order, user) {
  if (!user || !order) return false
  return order.assigneeId === user.id || order.assigneeName === user.displayName
}

/** 调度员可见：接单大厅（未指派）+ 我的工单 */
function filterVisibleWorkOrders(orders, user) {
  if (!user || !orders?.length) return orders || []
  if (user.role !== 'DISPATCHER') return orders
  return orders.filter((o) => isWorkOrderUnassigned(o) || isWorkOrderAssignee(o, user))
}

function filterByScope(orders, user, scope) {
  if (!user || user.role !== 'DISPATCHER' || !scope || scope === 'ALL') return orders
  if (scope === 'POOL') return orders.filter((o) => isWorkOrderUnassigned(o))
  if (scope === 'MINE') return orders.filter((o) => isWorkOrderAssignee(o, user))
  return orders
}

function canCreateWorkOrder(user) {
  return !!user && hasPermission(user.role, 'workorder:create')
}

function canClaimOrder(order, user) {
  if (!user || order.status !== 'PENDING') return false
  if (user.role !== 'DISPATCHER') return false
  return hasPermission(user.role, 'workorder:process') && isWorkOrderUnassigned(order)
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

function assertCanClaimOrder(order, user) {
  if (!user) throw new Error('未登录')
  if (!canClaimOrder(order, user)) throw new Error('无法接单，可能已被他人抢走')
}

function assertCanSubmitReview(order, user) {
  if (!user) throw new Error('未登录')
  if (!canSubmitReview(order, user)) throw new Error('仅接单调度员可提交复核')
}

function assertCanConfirmReview(order, user) {
  if (!user) throw new Error('未登录')
  if (!canConfirmReview(order, user)) throw new Error('仅管理员可确认复核')
}

function assertStatusTransition(order, nextStatus, user) {
  const cur = order.status
  if (nextStatus === 'PROCESSING' && cur === 'PENDING') {
    assertCanClaimOrder(order, user)
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
  isWorkOrderUnassigned,
  filterVisibleWorkOrders,
  filterByScope,
  canCreateWorkOrder,
  canClaimOrder,
  canSubmitReview,
  canConfirmReview,
  assertCanCreateWorkOrder,
  assertCanClaimOrder,
  assertCanSubmitReview,
  assertCanConfirmReview,
  assertStatusTransition,
}
