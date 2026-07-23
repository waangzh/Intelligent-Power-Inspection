import { useAlarmStore } from '@/stores/alarm'
import { useNotificationStore } from '@/stores/notification'
import { useRobotStore } from '@/stores/robot'
import { useTaskStore } from '@/stores/task'
import { connectRealtime, disconnectRealtime, subscribeTopic } from '@/api/realtime'
import { resourcesApi } from '@/api/resources'
import type { ResourceChangeEvent } from '@/types/pagination'

const SESSION_KEY = 'pi_session'
let realtimeStarted = false
let unsubscribeUserRealtime: Array<() => void> = []
let unsubscribePageRealtime: Array<() => void> = []
let pageChangeHandler: (() => void) | undefined

export async function loadAppData() {
  void useNotificationStore().load({ size: 20 }).catch(() => {})
  startRealtime()
}

export function startRealtime() {
  if (realtimeStarted) return
  realtimeStarted = true
  connectRealtime()

  const userId = currentUserId()
  if (userId) {
    const refreshNotification = (event: ResourceChangeEvent) => {
      const apply = (attempt = 0): Promise<void> => resourcesApi.getNotification(event.resourceId)
        .then(useNotificationStore().applyRemoteNotification)
        .catch((error) => {
          if (attempt >= 2) throw error
          return new Promise((resolve) => {
            window.setTimeout(() => { void apply(attempt + 1).then(resolve).catch(() => resolve()) }, 500)
          })
        })
      void apply().catch(() => {})
    }
    unsubscribeUserRealtime = [
      subscribeTopic<ResourceChangeEvent>('/topic/notifications', refreshNotification),
      subscribeTopic<ResourceChangeEvent>(`/topic/notifications/${userId}`, refreshNotification),
    ]
  }
}

export function setPageRealtimeResources(
  resources: Array<'task' | 'taskEvent' | 'robot' | 'alarm'>,
  onChange?: () => void,
) {
  startRealtime()
  pageChangeHandler = onChange
  unsubscribePageRealtime.forEach((unsubscribe) => unsubscribe())
  unsubscribePageRealtime = []
  const selected = new Set(resources)
  if (selected.has('task')) {
    unsubscribePageRealtime.push(subscribeTopic<ResourceChangeEvent>('/topic/tasks', (event) => {
      void resourcesApi.getTask(event.resourceId).then((item) => {
        useTaskStore().applyRemoteTask(item)
        pageChangeHandler?.()
      })
    }))
  }
  if (selected.has('taskEvent')) {
    unsubscribePageRealtime.push(subscribeTopic<ResourceChangeEvent>('/topic/task-events', (event) => {
      void resourcesApi.getTaskEvent(event.resourceId).then((item) => {
        useTaskStore().applyRemoteTaskEvent(item)
        pageChangeHandler?.()
      })
    }))
  }
  if (selected.has('robot')) {
    unsubscribePageRealtime.push(subscribeTopic<ResourceChangeEvent>('/topic/robots', (event) => {
      void resourcesApi.getRobot(event.resourceId).then((item) => {
        useRobotStore().applyRemoteRobot(item)
        pageChangeHandler?.()
      })
    }))
  }
  if (selected.has('alarm')) {
    unsubscribePageRealtime.push(subscribeTopic<ResourceChangeEvent>('/topic/alarms', (event) => {
      void resourcesApi.getAlarm(event.resourceId).then((item) => {
        useAlarmStore().applyRemoteAlarm(item)
        pageChangeHandler?.()
      })
    }))
  }
}

export function stopRealtime() {
  unsubscribeUserRealtime.forEach((unsubscribe) => unsubscribe())
  unsubscribePageRealtime.forEach((unsubscribe) => unsubscribe())
  unsubscribeUserRealtime = []
  unsubscribePageRealtime = []
  realtimeStarted = false
  disconnectRealtime()
}

function currentUserId() {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as { user?: { id?: string } }
    return session.user?.id ?? null
  } catch {
    return null
  }
}
