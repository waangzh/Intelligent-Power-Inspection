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

export const FAULT_TYPE_OPTIONS = [
  '设备渗漏油',
  '表计异常',
  '开关/刀闸异常',
  '异物入侵',
  '火源/烟雾',
  '人员违章',
  '其他',
] as const

export const HANDLING_METHOD_OPTIONS = [
  '现场清理',
  '紧固/复位',
  '更换部件',
  '临时隔离',
  '上报待检修',
  '误报关闭',
] as const

export interface WorkOrderLocation {
  siteName?: string
  routeName?: string
  checkpointName?: string
  areaName?: string
  address?: string
  coordinates?: { x: number; y: number }
}

export interface WorkOrderResolutionForm {
  faultType: string
  handlingMethod: string
  replacedParts?: string
  testResult: string
  remarks?: string
  submittedAt: string
  submittedBy: string
}

export interface WorkOrderReviewForm {
  result: 'PASS' | 'REJECT'
  comment: string
  reviewedAt: string
  reviewedBy: string
}

export interface WorkOrder {
  id: string
  title: string
  description: string
  alarmId?: string
  status: WorkOrderStatus
  priority: WorkOrderPriority
  assigneeId?: string
  assigneeName?: string
  createdById: string
  createdByName: string
  resolution?: string
  location?: WorkOrderLocation
  resolutionForm?: WorkOrderResolutionForm
  reviewForm?: WorkOrderReviewForm
  autoConverted?: boolean
  createdAt: string
  updatedAt: string
  closedAt?: string
}
