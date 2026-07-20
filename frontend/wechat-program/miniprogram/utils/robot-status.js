const { ROBOT_STATUS_LABELS } = require('./constants')

const CONNECTION_STATUS_LABELS = {
  CONNECTED: '在线',
  OFFLINE: '离线',
  BRIDGE_UNREACHABLE: '离线',
  BRIDGE_UNCONFIGURED: '离线',
  UNKNOWN: '离线',
}

const OFFLINE_REASON_LABELS = {
  NO_HEARTBEAT: '离线',
  HEARTBEAT_TIMEOUT: '离线',
  BRIDGE_UNREACHABLE: '离线',
  BRIDGE_UNCONFIGURED: '离线',
  INVALID_BRIDGE_SNAPSHOT: '离线',
}

/** 与 Web robotStatus.ts 一致：有心跳结果时优先用心跳，否则回退库存 status */
function isRobotOnline(robot, heartbeatOnline) {
  if (heartbeatOnline === true) return true
  if (heartbeatOnline === false) return false
  const status = robot?.status
  return status === 'ONLINE' || status === 'BUSY'
}

function resolveRobotPresence(robot, heartbeatItem) {
  const heartbeatOnline = heartbeatItem ? heartbeatItem.online === true : null
  const online = isRobotOnline(robot, heartbeatOnline)
  let statusLabel
  if (heartbeatItem && heartbeatItem.online === false) {
    statusLabel = '离线'
  } else if (heartbeatItem && heartbeatItem.online === true) {
    statusLabel = robot?.status === 'BUSY' ? (ROBOT_STATUS_LABELS.BUSY || '任务中') : '在线'
  } else {
    statusLabel = ROBOT_STATUS_LABELS[robot?.status] || robot?.status || '-'
  }
  return {
    online,
    statusType: online ? (robot?.status === 'BUSY' ? 'warning' : 'success') : 'info',
    statusLabel,
    heartbeat: heartbeatItem || null,
  }
}

module.exports = {
  isRobotOnline,
  resolveRobotPresence,
  CONNECTION_STATUS_LABELS,
  OFFLINE_REASON_LABELS,
}
