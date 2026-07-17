const { hasPermission } = require('./permission')
const { isWorkOrderUnassigned } = require('./work-order')
const { filterVisibleWorkOrders, isWorkOrderAssignee } = require('./work-order-permission')

function countWorkOrderBadge(orders, user, permissions) {
  if (!user || !hasPermission(permissions, 'workorder:view')) return 0
  if (user.role === 'ADMIN') {
    return orders.filter((o) => o.status === 'REVIEW').length
  }
  if (user.role === 'DISPATCHER') {
    const visible = filterVisibleWorkOrders(orders, user)
    const pool = visible.filter((o) => isWorkOrderUnassigned(o) && o.status === 'PENDING').length
    const mine = visible.filter((o) => isWorkOrderAssignee(o, user) && o.status === 'PROCESSING').length
    return pool + mine
  }
  return 0
}

module.exports = { countWorkOrderBadge }
