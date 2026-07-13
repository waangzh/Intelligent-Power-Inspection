const { hasPermission } = require('./permission')
const { canAssignOrder: isAssignableStatus, isWorkOrderUnassigned } = require('./work-order')

/** 与 lzm Web WorkOrderManage visibleOrders 一致 */
function isWorkOrderAssignee(order, user) {
  if (!user || !order) return false
  return order.assigneeId === user.id || order.assigneeName === user.displayName
}

/** 调度员仅看指派给自己的工单；待指派 PENDING 不展示 */
function filterVisibleWorkOrders(orders, user) {
  if (!user || !orders?.length) return orders || []
  if (user.role !== 'DISPATCHER') return orders
  return orders.filter((o) => {
    if (!isWorkOrderAssignee(o, user)) return false
    if (o.status === 'PENDING' && isWorkOrderUnassigned(o)) return false
    return true
  })
}

function canCreateWorkOrder(user) {
  return !!user && hasPermission(user.role, 'workorder:create')
}

function canAssignOrder(order, user) {
  return !!user && hasPermission(user.role, 'workorder:assign') && isAssignableStatus(order)
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

function assertCanAssignOrder(order, user) {
  if (!user) throw new Error('未登录')
  if (!canAssignOrder(order, user)) throw new Error('无指派权限')
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
  filterVisibleWorkOrders,
  canCreateWorkOrder,
  canAssignOrder,
  canSubmitReview,
  canConfirmReview,
  assertCanCreateWorkOrder,
  assertCanAssignOrder,
  assertCanSubmitReview,
  assertCanConfirmReview,
  assertStatusTransition,
}
