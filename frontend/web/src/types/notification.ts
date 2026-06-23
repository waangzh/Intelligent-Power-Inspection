export type NotificationType = 'ALARM' | 'TASK' | 'WORKORDER' | 'SYSTEM'

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  ALARM: '告警',
  TASK: '任务',
  WORKORDER: '工单',
  SYSTEM: '系统',
}

export interface AppNotification {
  id: string
  userId: string
  type: NotificationType
  title: string
  content: string
  read: boolean
  link?: string
  createdAt: string
}
