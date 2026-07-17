export type WorkOrderStatus = 'PENDING' | 'PROCESSING' | 'REVIEW' | 'CLOSED' | 'CANCELLED'

export type WorkOrderPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export type WorkOrderReviewConclusion = 'RESOLVED' | 'PARTIALLY_RESOLVED' | 'UNRESOLVED' | 'FALSE_ALARM'

export interface WorkOrderLocation {
  siteName?: string
  routeName?: string
  checkpointName?: string
  areaName?: string
  address?: string
}

export interface WorkOrderResolutionForm {
  faultType: string
  handlingMethod: string
  replacedParts?: string
  testResult: string
  remarks?: string
  conclusion: WorkOrderReviewConclusion
  submittedAt: string
  submittedBy: string
}

export interface WorkOrderReviewForm {
  result: 'PASS' | 'REJECT'
  comment: string
  reviewedAt: string
  reviewedBy: string
}

export const FAULT_TYPE_OPTIONS = ['设备故障', '线路故障', '通信故障', '环境异常', '其他']
export const HANDLING_METHOD_OPTIONS = ['现场处置', '更换部件', '参数调整', '转人工处理', '其他']

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

export const WORK_ORDER_REVIEW_CONCLUSION_OPTIONS: WorkOrderReviewConclusion[] = [
  'RESOLVED',
  'PARTIALLY_RESOLVED',
  'UNRESOLVED',
  'FALSE_ALARM',
]

/** 部分消缺 / 未消缺时，后端要求必须填写遗留风险与后续计划 */
export const CONCLUSIONS_REQUIRING_FOLLOW_UP: WorkOrderReviewConclusion[] = ['PARTIALLY_RESOLVED', 'UNRESOLVED']

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
  location?: WorkOrderLocation
  autoConverted?: boolean
  assigneeId?: string
  assigneeName?: string
  createdById: string
  createdByName: string
  resolution?: string
  resolutionForm?: WorkOrderResolutionForm
  review?: WorkOrderReview
  reviewForm?: WorkOrderReviewForm
  createdAt: string
  updatedAt: string
  closedAt?: string
}
