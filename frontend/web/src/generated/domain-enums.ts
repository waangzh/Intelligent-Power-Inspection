/** AUTO-GENERATED — 请勿手工编辑。运行: npm run domain:generate */
export type UserRole = 'ADMIN' | 'DISPATCHER' | 'VIEWER'

export const USER_ROLE_VALUES = [
  'ADMIN',
  'DISPATCHER',
  'VIEWER',
] as const

export const USER_ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: '管理员',
  DISPATCHER: '调度员',
  VIEWER: '观察员',
}

export type TaskStatus = 'CREATED' | 'DISPATCHED' | 'RUNNING' | 'PAUSED' | 'MANUAL_TAKEOVER' | 'COMPLETED' | 'CANCELLED'

export const TASK_STATUS_VALUES = [
  'CREATED',
  'DISPATCHED',
  'RUNNING',
  'PAUSED',
  'MANUAL_TAKEOVER',
  'COMPLETED',
  'CANCELLED',
] as const

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  CREATED: '已创建',
  DISPATCHED: '已下发',
  RUNNING: '执行中',
  PAUSED: '已暂停',
  MANUAL_TAKEOVER: '人工接管',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export type WorkOrderStatus = 'PENDING' | 'PROCESSING' | 'REVIEW' | 'CLOSED' | 'CANCELLED'

export const WORK_ORDER_STATUS_VALUES = [
  'PENDING',
  'PROCESSING',
  'REVIEW',
  'CLOSED',
  'CANCELLED',
] as const

export const WORK_ORDER_STATUS_LABELS: Record<WorkOrderStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  REVIEW: '待复核',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

export type WorkOrderPriority = 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW'

export const WORK_ORDER_PRIORITY_VALUES = [
  'URGENT',
  'HIGH',
  'MEDIUM',
  'LOW',
] as const

export const WORK_ORDER_PRIORITY_LABELS: Record<WorkOrderPriority, string> = {
  URGENT: '紧急',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export type WorkOrderReviewConclusion = 'RESOLVED' | 'PARTIALLY_RESOLVED' | 'UNRESOLVED' | 'FALSE_ALARM'

export const WORK_ORDER_REVIEW_CONCLUSION_VALUES = [
  'RESOLVED',
  'PARTIALLY_RESOLVED',
  'UNRESOLVED',
  'FALSE_ALARM',
] as const

export const WORK_ORDER_REVIEW_CONCLUSION_LABELS: Record<WorkOrderReviewConclusion, string> = {
  RESOLVED: '已消缺',
  PARTIALLY_RESOLVED: '部分消缺',
  UNRESOLVED: '未消缺',
  FALSE_ALARM: '误报',
}

export type AlarmSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export const ALARM_SEVERITY_VALUES = [
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
] as const

export const ALARM_SEVERITY_LABELS: Record<AlarmSeverity, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '紧急',
}

export type RobotStatus = 'ONLINE' | 'OFFLINE' | 'BUSY'

export const ROBOT_STATUS_VALUES = [
  'ONLINE',
  'OFFLINE',
  'BUSY',
] as const

export const ROBOT_STATUS_LABELS: Record<RobotStatus, string> = {
  ONLINE: '在线',
  OFFLINE: '离线',
  BUSY: '忙碌',
}
