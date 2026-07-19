import type { Robot } from '@/types'

export const PLATFORM_STATUS_LABELS: Record<Robot['status'], string> = {
  ONLINE: '在线',
  OFFLINE: '离线',
  BUSY: '任务中',
}

export const PATROL_STATE_LABELS: Record<string, string> = {
  unavailable: '不可用',
  idle: '空闲',
  running: '巡逻中',
  paused: '已暂停',
  succeeded: '已完成',
  failed: '失败',
  canceled: '已取消',
}

export function patrolStateLabel(state?: string) {
  if (!state) return '-'
  return PATROL_STATE_LABELS[state] ?? state
}

export function mappingStatusLabel(status?: string) {
  if (status === 'running') return '建图中'
  if (status === 'not_running') return '未建图'
  return status ?? '-'
}

export function nav2StatusLabel(status?: string) {
  if (status === 'running') return '导航就绪'
  if (status === 'not_running') return '导航未启'
  return status ?? '-'
}

export function sensorFreshness(age?: number | null) {
  if (age == null) return '无数据'
  if (age <= 3) return `${age.toFixed(1)}s · 正常`
  return `${age.toFixed(1)}s · 滞后`
}

export function bridgeReachable(robot: Robot) {
  return robot.telemetry?.bridgeReachable === true && robot.telemetry?.online === true
}

/** Prefer heartbeat presence; fall back to inventory only when heartbeat is unknown. */
export function isRobotOnline(robot: Robot, heartbeatOnline?: boolean | null) {
  if (heartbeatOnline === true) return true
  if (heartbeatOnline === false) return false
  return robot.status === 'ONLINE' || robot.status === 'BUSY'
}
