export type WorkOrderStatus = 'PENDING' | 'PROCESSING' | 'REVIEW' | 'CLOSED' | 'CANCELLED'

export type WorkOrderPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export type WorkOrderReviewConclusion = 'RESOLVED' | 'PARTIALLY_RESOLVED' | 'UNRESOLVED' | 'FALSE_ALARM'

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

export const WORK_ORDER_REVIEW_CONCLUSION_LABELS: Record<WorkOrderReviewConclusion, string> = {
  RESOLVED: '已消缺',
  PARTIALLY_RESOLVED: '部分消缺',
  UNRESOLVED: '未消缺',
  FALSE_ALARM: '误报',
}

export interface WorkOrderReviewInput {
  conclusion: WorkOrderReviewConclusion
  onsiteFinding: string
  handlingMeasures: string
  followUpPlan?: string
}

export interface WorkOrderReview extends WorkOrderReviewInput {
  submittedById: string
  submittedByName: string
  submittedAt: string
}

export interface WorkOrder {
  id: string
  title: string
  description: string
  alarmId?: string
  source?: 'AUTO' | 'MANUAL' | 'AGENT'
  status: WorkOrderStatus
  priority: WorkOrderPriority
  locationDescription?: string
  assigneeId?: string
  assigneeName?: string
  createdById: string
  createdByName: string
  resolution?: string
  review?: WorkOrderReview
  createdAt: string
  updatedAt: string
  closedAt?: string
}
