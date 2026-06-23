import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { InspectionRecord, InspectionTask, TaskEvent, TaskStatus } from '@/types'
import { uid } from '@/utils/storage'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<InspectionTask[]>([])
  const records = ref<InspectionRecord[]>([])
  const events = ref<TaskEvent[]>([])

  async function load() {
    await loadDynamic()
    records.value = await resourcesApi.listRecords()
  }

  async function loadDynamic() {
    tasks.value = await resourcesApi.listTasks()
    const taskEvents = await Promise.all(tasks.value.map((task) => resourcesApi.taskEvents(task.id).catch(() => [])))
    events.value = taskEvents.flat()
    records.value = await resourcesApi.listRecords().catch(() => records.value)
  }

  function getEventsByTask(taskId: string) {
    return events.value.filter((e) => e.taskId === taskId).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
  }

  function getTaskById(id: string) {
    return tasks.value.find((t) => t.id === id)
  }

  function createTask(name: string, routeId: string, robotId: string) {
    const task: InspectionTask = {
      id: uid('task'),
      name,
      routeId,
      robotId,
      status: 'CREATED',
      progress: 0,
      currentCheckpointSeq: 0,
      createdAt: new Date().toISOString(),
    }
    tasks.value.unshift(task)
    void resourcesApi.createTask(task).then(updateLocalTask)
    return task
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

  function pause(id: string) {
    if (tasks.value.find((t) => t.id === id)?.status === 'RUNNING') {
      setStatusLocal(id, 'PAUSED')
      void resourcesApi.pauseTask(id).then(updateLocalTask)
    }
  }

  function resume(id: string) {
    const task = tasks.value.find((t) => t.id === id)
    if (task && (task.status === 'PAUSED' || task.status === 'MANUAL_TAKEOVER')) {
      setStatusLocal(id, 'RUNNING')
      void resourcesApi.resumeTask(id).then(updateLocalTask)
    }
  }

  function cancel(id: string) {
    setStatusLocal(id, 'CANCELLED')
    void resourcesApi.cancelTask(id).then(updateLocalTask)
  }

  function takeover(id: string) {
    const task = tasks.value.find((t) => t.id === id)
    if (task?.status === 'RUNNING') {
      setStatusLocal(id, 'MANUAL_TAKEOVER')
      void resourcesApi.takeoverTask(id).then(updateLocalTask)
    }
  }

  function getActiveTask() {
    return tasks.value.find((t) =>
      ['DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status),
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

  function applyRemoteTaskEvent(event: TaskEvent) {
    const idx = events.value.findIndex((item) => item.id === event.id)
    if (idx >= 0) events.value[idx] = event
    else events.value.unshift(event)
  }

  function applyRemoteTask(task: InspectionTask) {
    updateLocalTask(task)
    void resourcesApi.taskEvents(task.id).then((nextEvents) => replaceEvents(task.id, nextEvents))
    if (task.status === 'COMPLETED') {
      void resourcesApi.listRecords().then((nextRecords) => {
        records.value = nextRecords
      })
    }
  }

  return {
    tasks,
    records,
    events,
    load,
    loadDynamic,
    createTask,
    updateTask,
    dispatch,
    pause,
    resume,
    cancel,
    takeover,
    getActiveTask,
    getTaskById,
    getEventsByTask,
    applyRemoteTask,
    applyRemoteTaskEvent,
  }
})
