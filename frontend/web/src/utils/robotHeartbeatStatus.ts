import type { RobotConnectionStatus, RobotHeartbeatStatus } from '@/types/robotHeartbeat'

export type RobotHeartbeatListState = 'ready' | 'empty' | 'failed'

const CONNECTION_LABELS: Record<RobotConnectionStatus, string> = {
  CONNECTED: '已连接',
  OFFLINE: '离线',
  UNKNOWN: '未知',
  BRIDGE_UNREACHABLE: 'Bridge 不可达',
  BRIDGE_UNCONFIGURED: 'Bridge 未配置',
}

const OFFLINE_REASON_LABELS: Record<string, string> = {
  HEARTBEAT_TIMEOUT: '心跳超时（超过 12 秒）',
  NO_HEARTBEAT: '尚未收到心跳',
  BRIDGE_UNREACHABLE: '平台无法访问本机 Bridge',
  BRIDGE_UNCONFIGURED: '该机器人未在 Bridge 中配置',
  INVALID_BRIDGE_SNAPSHOT: 'Bridge 返回的心跳快照无效',
}

export function connectionLabel(status: RobotConnectionStatus) {
  return CONNECTION_LABELS[status]
}

export function offlineReasonLabel(reason?: string | null) {
  if (!reason) return '-'
  return OFFLINE_REASON_LABELS[reason] ?? reason
}

export function heartbeatVisual(status: RobotHeartbeatStatus): 'success' | 'danger' | 'warning' | 'info' {
  if (status.online) return 'success'
  if (status.connectionStatus === 'OFFLINE' || status.connectionStatus === 'BRIDGE_UNREACHABLE') return 'danger'
  if (status.connectionStatus === 'BRIDGE_UNCONFIGURED') return 'warning'
  return 'info'
}

export function heartbeatListState(items: RobotHeartbeatStatus[], failed: boolean): RobotHeartbeatListState {
  if (failed) return 'failed'
  return items.length === 0 ? 'empty' : 'ready'
}
