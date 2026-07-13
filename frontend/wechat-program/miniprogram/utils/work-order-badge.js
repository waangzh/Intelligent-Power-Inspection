const { hasPermission } = require('./permission')
const { filterVisibleWorkOrders } = require('./work-order-permission')

function countWorkOrderBadge(orders, user) {
  if (!user || !hasPermission(user.role, 'workorder:view')) return 0
  if (user.role === 'ADMIN') {
    const { isWorkOrderUnassigned } = require('./work-order')
    return orders.filter((o) =>
      (o.status === 'PENDING' && isWorkOrderUnassigned(o)) || o.status === 'REVIEW',
    ).length
  }
  if (user.role === 'DISPATCHER') {
    return filterVisibleWorkOrders(orders, user).filter((o) => o.status === 'PROCESSING').length
  }
  return 0
}

module.exports = { countWorkOrderBadge }
