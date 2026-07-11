export type WorkOrderStatus = 'PENDING' | 'PROCESSING' | 'REVIEW' | 'CLOSED' | 'CANCELLED'

export type WorkOrderPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export const WORK_ORDER_STATUS_LABELS: Record<WorkOrderStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  REVIEW: '待复核',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

export const WORK_ORDER_PRIORITY_LABELS: Record<WorkOrderPriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

export interface WorkOrder {
  id: string
  title: string
  description: string
  alarmId?: string
  source?: 'AUTO' | 'MANUAL' | 'AGENT'
  status: WorkOrderStatus
  priority: WorkOrderPriority
  assigneeId?: string
  assigneeName?: string
  createdById: string
  createdByName: string
  resolution?: string
  createdAt: string
  updatedAt: string
  closedAt?: string
}
