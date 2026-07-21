import type { TaskExecution } from '@/types'

export interface StartReceiptView {
  label: string
  detail: string
  type: 'success' | 'warning' | 'info' | 'danger'
}

export function startReceiptOf(current?: TaskExecution): StartReceiptView {
  const status = current?.status
  if (!current || status === 'CREATED') {
    return { label: '尚未下发', detail: '选择启动方式后才会向 Bridge 创建命令', type: 'info' }
  }
  if (status === 'COMPLETED') {
    return { label: '已完成', detail: '机器人已回传路线完成事件', type: 'success' }
  }
  if (status === 'CANCELLED') {
    return { label: '已取消', detail: '机器人已确认任务取消', type: 'info' }
  }
  if (status === 'START_FAILED') {
    return { label: '启动失败', detail: current.lastErrorMessage || '启动命令未完成', type: 'danger' }
  }
  if (status === 'FAILED') {
    return { label: '已失败', detail: current.lastErrorMessage || '机器人回传任务失败', type: 'danger' }
  }
  if (current.localConfirmedAt && status === 'WAITING_LOCAL_CONFIRM') {
    return { label: '机器人已确认', detail: '现场人员已确认，等待 route_started 事件', type: 'warning' }
  }
  if (status === 'WAITING_LOCAL_CONFIRM') {
    return { label: '等待机器人本地确认', detail: '部署已校验，等待现场人员在触摸屏确认', type: 'warning' }
  }
  if (current.startedAt || ['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING', 'MANUAL_TAKEOVER'].includes(status ?? '')) {
    return { label: '机器人已执行', detail: '已收到机器人真实运行事件', type: 'success' }
  }
  if (current.lastRobotSequence > 0 || current.lastEventAt) {
    return { label: '机器人已领取', detail: '已收到机器人事件，等待路线启动确认', type: 'success' }
  }
  if (current.startCommandId) {
    return { label: 'Bridge 已入队', detail: 'Bridge 已接受命令，等待机器人领取并回传事件', type: 'warning' }
  }
  return { label: '平台正在下发', detail: '启动请求已受理，正在创建 Bridge 命令', type: 'warning' }
}
