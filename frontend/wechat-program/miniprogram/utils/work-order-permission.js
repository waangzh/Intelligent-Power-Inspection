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

function canCreateWorkOrder(user, permissions) {
  return !!user && hasPermission(permissions, 'workorder:create')
}

function canClaimOrder(order, user, permissions) {
  if (!user || order.status !== 'PENDING') return false
  if (user.role !== 'DISPATCHER') return false
  return hasPermission(permissions, 'workorder:process') && isWorkOrderUnassigned(order)
}

function canSubmitReview(order, user, permissions) {
  if (!user || order.status !== 'PROCESSING') return false
  if (user.role !== 'DISPATCHER') return false
  return hasPermission(permissions, 'workorder:process') && isWorkOrderAssignee(order, user)
}

function canConfirmReview(order, user, permissions) {
  return !!(user && order.status === 'REVIEW' && hasPermission(permissions, 'workorder:review'))
}

function assertCanCreateWorkOrder(user, permissions) {
  if (!user) throw new Error('未登录')
  if (!canCreateWorkOrder(user, permissions)) throw new Error('无转工单权限')
}

function assertCanClaimOrder(order, user, permissions) {
  if (!user) throw new Error('未登录')
  if (!canClaimOrder(order, user, permissions)) throw new Error('无法接单，可能已被他人抢走')
}

function assertCanSubmitReview(order, user, permissions) {
  if (!user) throw new Error('未登录')
  if (!canSubmitReview(order, user, permissions)) throw new Error('仅接单调度员可提交复核')
}

function assertCanConfirmReview(order, user, permissions) {
  if (!user) throw new Error('未登录')
  if (!canConfirmReview(order, user, permissions)) throw new Error('仅管理员可确认复核')
}

function assertStatusTransition(order, nextStatus, user, permissions) {
  const cur = order.status
  if (nextStatus === 'PROCESSING' && cur === 'PENDING') {
    assertCanClaimOrder(order, user, permissions)
    return
  }
  if (nextStatus === 'REVIEW' && cur === 'PROCESSING') {
    assertCanSubmitReview(order, user, permissions)
    return
  }
  if (cur === 'REVIEW' && (nextStatus === 'CLOSED' || nextStatus === 'PROCESSING')) {
    assertCanConfirmReview(order, user, permissions)
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
