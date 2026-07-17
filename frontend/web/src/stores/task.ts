import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { InspectionRecord, InspectionTask, RouteRevision, TaskEvent, TaskExecution, TaskStartEligibility, TaskStatus } from '@/types'
import type { RouteDeployment } from '@/types/routeDeployment'
import { uid } from '@/utils/storage'

export function latestReadyRevision(revisions: RouteRevision[], deployments: RouteDeployment[], robotId: string) {
  return revisions
    .filter((revision) => revision.validationReport.valid && deployments.some((deployment) =>
      deployment.robotId === robotId
      && deployment.routeRevisionId === revision.id
      && deployment.state === 'READY_FOR_ROBOT'
      && deployment.routeContentSha256 === revision.contentSha256
      && deployment.mapImageSha256 === revision.mapImageSha256,
    ))
    .sort((a, b) => b.revisionNo - a.revisionNo)[0]
}

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<InspectionTask[]>([])
  const records = ref<InspectionRecord[]>([])
  const events = ref<TaskEvent[]>([])
  const executions = ref<Record<string, TaskExecution>>({})
  const startEligibility = ref<Record<string, TaskStartEligibility>>({})
  const controlInFlight = ref<Record<string, boolean>>({})
  const controlRequestKeys = ref<Record<string, { action: string; key: string }>>({})
  let executionPollTimer: ReturnType<typeof setInterval> | undefined

  async function load() {
    await loadDynamic()
    records.value = await resourcesApi.listRecords()
  }

  async function loadDynamic() {
    tasks.value = (await resourcesApi.listTasks({ size: 50 })).items
  }

  async function loadOne(taskId: string) {
    const task = await resourcesApi.getTask(taskId)
    updateLocalTask(task)
    await Promise.allSettled([refreshEvents(taskId), refreshExecution(taskId)])
    startExecutionPolling()
    return task
  }

  async function refreshExecution(taskId: string) {
    const [execution, eligibility] = await Promise.all([
      resourcesApi.getTaskExecution(taskId).catch(() => null),
      resourcesApi.getTaskStartEligibility(taskId).catch(() => null),
    ])
    if (execution) executions.value = { ...executions.value, [taskId]: execution }
    if (eligibility) startEligibility.value = { ...startEligibility.value, [taskId]: eligibility }
    return execution
  }

  async function refreshExecutions() {
    await Promise.all(tasks.value.filter((task) => task.executionId).map((task) => refreshExecution(task.id)))
  }

  function startExecutionPolling() {
    if (executionPollTimer || !tasks.value.some((task) => task.executionId)) return
    executionPollTimer = setInterval(() => { void refreshExecutions() }, 2000)
  }

  function stopExecutionPolling() {
    if (!executionPollTimer) return
    clearInterval(executionPollTimer)
    executionPollTimer = undefined
  }

  async function startInspection(taskId: string) {
    const execution = await resourcesApi.startTask(taskId, uid('start'))
    executions.value = { ...executions.value, [taskId]: execution }
    await refreshExecution(taskId)
    startExecutionPolling()
    return execution
  }

  async function controlInspection(taskId: string, action: 'PAUSE' | 'RESUME' | 'TAKEOVER' | 'CANCEL', reason?: string) {
    if (controlInFlight.value[taskId]) return executionFor(taskId)
    const current = controlRequestKeys.value[taskId]
    const key = current?.action === action ? current.key : uid('control')
    controlRequestKeys.value = { ...controlRequestKeys.value, [taskId]: { action, key } }
    controlInFlight.value = { ...controlInFlight.value, [taskId]: true }
    try {
      const execution = await ({
        PAUSE: () => resourcesApi.pauseTask(taskId, key),
        RESUME: () => resourcesApi.resumeTask(taskId, key),
        TAKEOVER: () => resourcesApi.takeoverTask(taskId, reason ?? '', key),
        CANCEL: () => resourcesApi.cancelTask(taskId, key),
      }[action])()
      executions.value = { ...executions.value, [taskId]: execution }
      await refreshExecution(taskId)
      startExecutionPolling()
      return execution
    } finally {
      controlInFlight.value = { ...controlInFlight.value, [taskId]: false }
    }
  }

  // 兼容其他页面的快捷操作；执行快照仍以轮询的真实事件结果为准。
  function pause(taskId: string) { void controlInspection(taskId, 'PAUSE') }
  function resume(taskId: string) { void controlInspection(taskId, 'RESUME') }
  function takeover(taskId: string) { void controlInspection(taskId, 'TAKEOVER', '页面快捷人工接管') }
  function cancel(taskId: string) { void controlInspection(taskId, 'CANCEL') }

  function executionFor(taskId: string) {
    return executions.value[taskId]
  }

  function eligibilityFor(taskId: string) {
    return startEligibility.value[taskId]
  }

  function statusOf(task: InspectionTask): TaskStatus {
    return executionFor(task.id)?.status ?? task.status
  }

  function getEventsByTask(taskId: string) {
    return events.value.filter((e) => e.taskId === taskId).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
  }

  function getTaskById(id: string) {
    return tasks.value.find((t) => t.id === id)
  }

  async function createTask(name: string, routeId: string, robotId: string, routeRevisionId: string) {
    const task: InspectionTask = {
      id: uid('task'),
      name,
      routeId,
      robotId,
      status: 'CREATED',
      progress: 0,
      currentCheckpointSeq: 0,
      createdAt: new Date().toISOString(),
      routeRevisionId,
    }
    const saved = await resourcesApi.createTask(task)
    updateLocalTask(saved)
    if (saved.executionId) await refreshExecution(saved.id)
    startExecutionPolling()
    return saved
  }

  function updateTask(id: string, patch: Partial<InspectionTask>) {
    const idx = tasks.value.findIndex((t) => t.id === id)
    if (idx >= 0) {
      tasks.value[idx] = { ...tasks.value[idx], ...patch }
    }
  }

  function setStatusLocal(id: string, status: TaskStatus) {
    const task = tasks.value.find((t) => t.id === id)
    if (!task) return
    const now = new Date().toISOString()

    if (status === 'DISPATCHED') {
      updateTask(id, { status, startedAt: task.startedAt ?? now })
    } else if (status === 'RUNNING') {
      updateTask(id, { status, startedAt: task.startedAt ?? now })
    } else if (status === 'PAUSED') {
      updateTask(id, { status })
    } else if (status === 'MANUAL_TAKEOVER') {
      updateTask(id, { status })
    } else if (status === 'CANCELLED' || status === 'COMPLETED') {
      updateTask(id, {
        status,
        completedAt: now,
        progress: status === 'COMPLETED' ? 100 : task.progress,
      })
    } else {
      updateTask(id, { status })
    }
  }

  function dispatch(id: string) {
    setStatusLocal(id, 'DISPATCHED')
    void resourcesApi.dispatchTask(id).then(updateLocalTask)
  }

  function getActiveTask() {
    return tasks.value.find((t) =>
      ['DISPATCHED', 'STARTING', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING', 'CANCELLING', 'TAKEOVER_PENDING', 'MANUAL_TAKEOVER', 'DISCONNECTED', 'RECOVERING'].includes(statusOf(t)),
    )
  }

  function updateLocalTask(task: InspectionTask) {
    const idx = tasks.value.findIndex((t) => t.id === task.id)
    if (idx >= 0) tasks.value[idx] = task
    else tasks.value.unshift(task)
  }

  function replaceEvents(taskId: string, nextEvents: TaskEvent[]) {
    events.value = [
      ...events.value.filter((event) => event.taskId !== taskId),
      ...nextEvents,
    ]
  }

  async function refreshEvents(taskId: string) {
    replaceEvents(taskId, await resourcesApi.taskEvents(taskId))
  }

  function applyRemoteTaskEvent(event: TaskEvent) {
    const idx = events.value.findIndex((item) => item.id === event.id)
    if (idx >= 0) events.value[idx] = event
    else events.value.unshift(event)
  }

  function applyRemoteTask(task: InspectionTask) {
    updateLocalTask(task)
  }

  return {
    tasks,
    records,
    events,
    executions,
    startEligibility,
    load,
    loadDynamic,
    loadOne,
    refreshExecution,
    refreshExecutions,
    startExecutionPolling,
    stopExecutionPolling,
    startInspection,
    controlInspection,
    controlInFlight,
    executionFor,
    eligibilityFor,
    statusOf,
    createTask,
    updateTask,
    dispatch,
    pause,
    resume,
    takeover,
    cancel,
    getActiveTask,
    getTaskById,
    getEventsByTask,
    refreshEvents,
    applyRemoteTask,
    applyRemoteTaskEvent,
  }
})
