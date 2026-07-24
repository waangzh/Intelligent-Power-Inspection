const READY_DEPLOYMENT_STATE = 'READY_FOR_ROBOT'

const BRIDGE_STATUS_LABELS = {
  STARTING: '启动中',
  START_FAILED: '启动失败',
  WAITING_LOCAL_CONFIRM: '待本地确认',
  PAUSING: '暂停中',
  RESUMING: '恢复中',
  CANCELLING: '取消中',
  ESTOPPING: '急停中',
  ESTOPPED: '已急停',
  FAILED: '失败',
  DISCONNECTED: '连接中断',
  RECOVERING: '恢复连接',
  TAKEOVER_PENDING: '接管中',
}

const RUNNING_STATUSES = [
  'DISPATCHED', 'STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSING',
  'PAUSED', 'RESUMING', 'MANUAL_TAKEOVER', 'TAKEOVER_PENDING', 'DISCONNECTED', 'RECOVERING',
]

const LAUNCH_STATUSES = ['CREATED', 'START_FAILED']

function isBridgeTask(task) {
  return !!(task && (task.executionId || task.routeRevisionId))
}

function latestReadyRevision(revisions, deployments, robotId) {
  return (revisions || [])
    .filter((revision) => {
      const valid = revision.validationReport?.valid ?? revision.valid
      if (valid === false) return false
      return (deployments || []).some((deployment) =>
        deployment.robotId === robotId
        && deployment.routeRevisionId === revision.id
        && deployment.state === READY_DEPLOYMENT_STATE
        && deployment.routeContentSha256 === revision.contentSha256
        && deployment.mapImageSha256 === revision.mapImageSha256,
      )
    })
    .sort((a, b) => (b.revisionNo || 0) - (a.revisionNo || 0))[0]
}

function resolveTaskStatus(task, execution) {
  if (execution?.status) return execution.status
  return task?.status || 'CREATED'
}

function taskStatusLabel(status) {
  return BRIDGE_STATUS_LABELS[status] || null
}

function canLaunchTask(task, execution) {
  const status = resolveTaskStatus(task, execution)
  return LAUNCH_STATUSES.includes(status)
}

function launchButtonLabel(task) {
  return isBridgeTask(task) ? '启动' : '下发'
}

function isActiveTask(task, execution) {
  const status = resolveTaskStatus(task, execution)
  return RUNNING_STATUSES.includes(status)
}

function readyRevisionLabel(revision) {
  if (!revision) return ''
  const no = revision.revisionNo != null ? `第 ${revision.revisionNo} 版` : '就绪版本'
  return revision.id ? `${no}（${revision.id}）` : no
}

module.exports = {
  READY_DEPLOYMENT_STATE,
  BRIDGE_STATUS_LABELS,
  RUNNING_STATUSES,
  LAUNCH_STATUSES,
  isBridgeTask,
  latestReadyRevision,
  resolveTaskStatus,
  taskStatusLabel,
  canLaunchTask,
  launchButtonLabel,
  isActiveTask,
  readyRevisionLabel,
}
