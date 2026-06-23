const DETECTION_LABELS = {
  PERSON: '人员检测',
  HELMET: '安全帽检测',
  OBSTACLE: '障碍物检测',
  FIRE: '火源/烟雾检测',
  SWITCH: '开关/刀闸状态',
  METER: '表计/指示灯',
  OIL_LEAK: '漏油检测',
  FOREIGN_OBJECT: '异物检测',
}

const TASK_STATUS_LABELS = {
  CREATED: '已创建',
  DISPATCHED: '已下发',
  RUNNING: '执行中',
  PAUSED: '已暂停',
  MANUAL_TAKEOVER: '人工接管',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const ALARM_SEVERITY_LABELS = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '紧急',
}

const ROLE_LABELS = {
  ADMIN: '管理员',
  DISPATCHER: '调度员',
  VIEWER: '观察员',
}

const WORK_ORDER_STATUS_LABELS = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  REVIEW: '待复核',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const WORK_ORDER_PRIORITY_LABELS = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

const NOTIFICATION_TYPE_LABELS = {
  ALARM: '告警',
  TASK: '任务',
  WORKORDER: '工单',
  SYSTEM: '系统',
}

const ROUTE_DETECTIONS = ['PERSON', 'HELMET', 'OBSTACLE', 'FIRE']
const CHECKPOINT_DETECTIONS = ['SWITCH', 'METER', 'OIL_LEAK', 'FIRE', 'FOREIGN_OBJECT']

const ACTIVITY_TYPE_LABELS = {
  LOGIN: '登录',
  PROFILE: '资料',
  AVATAR: '头像',
  PASSWORD: '安全',
  TASK: '任务',
  ALARM: '告警',
  SETTINGS: '设置',
}

module.exports = {
  DETECTION_LABELS,
  TASK_STATUS_LABELS,
  ALARM_SEVERITY_LABELS,
  ROLE_LABELS,
  WORK_ORDER_STATUS_LABELS,
  WORK_ORDER_PRIORITY_LABELS,
  NOTIFICATION_TYPE_LABELS,
  ROUTE_DETECTIONS,
  CHECKPOINT_DETECTIONS,
  ACTIVITY_TYPE_LABELS,
}
