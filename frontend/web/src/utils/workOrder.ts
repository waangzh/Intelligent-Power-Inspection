import type { WorkOrder, WorkOrderStatus } from '@/types/workOrder'

const ACTIVE_STATUSES: WorkOrderStatus[] = ['PROCESSING', 'REVIEW']

/** 后端曾把 assigneeName 默认成创建人，且未写入 assigneeId */
export function isPhantomAssignee(order: WorkOrder): boolean {
  if (!order.assigneeName?.trim()) return true
  if (order.assigneeId) return false
  return order.assigneeName === order.createdByName
}

/** 仅待处理工单可视为未指派 */
export function isWorkOrderUnassigned(order: WorkOrder): boolean {
  if (order.status !== 'PENDING') return false
  return isPhantomAssignee(order)
}

export function resolveAssigneeName(order: WorkOrder): string | undefined {
  if (!isPhantomAssignee(order) && order.assigneeName?.trim()) {
    return order.assigneeName.trim()
  }
  return order.resolutionForm?.submittedBy?.trim() || undefined
}

export function workOrderAssigneeLabel(order: WorkOrder): string {
  return resolveAssigneeName(order) || '待指派'
}

/** 处理中/待复核却没有真实处理人，属于历史脏数据 */
export function isInconsistentActiveOrder(order: WorkOrder): boolean {
  return ACTIVE_STATUSES.includes(order.status) && !resolveAssigneeName(order)
}

export function normalizeWorkOrder(order: WorkOrder): WorkOrder {
  if (isInconsistentActiveOrder(order)) {
    return {
      ...order,
      status: 'PENDING',
      assigneeName: undefined,
      assigneeId: undefined,
    }
  }

  if (isWorkOrderUnassigned(order)) {
    return { ...order, assigneeName: undefined, assigneeId: undefined }
  }

  // 已指派但仍停留在待处理，属于历史数据，应进入处理中
  if (order.status === 'PENDING' && !isPhantomAssignee(order)) {
    return { ...order, status: 'PROCESSING' }
  }

  return order
}
