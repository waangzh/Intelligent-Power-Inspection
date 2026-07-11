import { useAlarmStore } from '@/stores/alarm'
import { useDetectionStore } from '@/stores/detection'
import { useNotificationStore } from '@/stores/notification'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { useWorkOrderStore } from '@/stores/workOrder'
import { connectRealtime, disconnectRealtime, subscribeTopic } from '@/api/realtime'
import type { Alarm, InspectionTask, Robot, TaskEvent } from '@/types'
import type { AppNotification } from '@/types/notification'
import type { UserRole } from '@/types/auth'

const SESSION_KEY = 'pi_session'
let realtimeStarted = false
let unsubscribeRealtime: Array<() => void> = []

export async function loadAppData() {
  const siteStore = useSiteStore()
  const routeStore = useRouteStore()
  const robotStore = useRobotStore()
  const taskStore = useTaskStore()
  const alarmStore = useAlarmStore()
  const workOrderStore = useWorkOrderStore()
  const detectionStore = useDetectionStore()
  const notificationStore = useNotificationStore()

  await Promise.allSettled([
    siteStore.load(),
    routeStore.load(),
    robotStore.load(),
    taskStore.load(),
    alarmStore.load(),
    workOrderStore.load(),
    detectionStore.load(),
    notificationStore.load(),
  ])
  startRealtime()
}

export function startRealtime() {
  if (realtimeStarted) return
  realtimeStarted = true
  connectRealtime()

  unsubscribeRealtime = [
    subscribeTopic<InspectionTask>('/topic/tasks', (task) => {
      useTaskStore().applyRemoteTask(task)
    }),
    subscribeTopic<TaskEvent>('/topic/task-events', (event) => {
      useTaskStore().applyRemoteTaskEvent(event)
    }),
    subscribeTopic<Robot>('/topic/robots', (robot) => {
      useRobotStore().applyRemoteRobot(robot)
    }),
    subscribeTopic<Alarm>('/topic/alarms', (alarm) => {
      useAlarmStore().applyRemoteAlarm(alarm)
      void useNotificationStore().load()
      if (['ADMIN', 'DISPATCHER'].includes(currentUserRole() ?? '')) {
        void useWorkOrderStore().load()
      }
    }),
    subscribeTopic<AppNotification>('/topic/notifications', (notification) => {
      useNotificationStore().applyRemoteNotification(notification)
    }),
  ]

  const userId = currentUserId()
  if (userId) {
    unsubscribeRealtime.push(subscribeTopic<AppNotification>(`/topic/notifications/${userId}`, (notification) => {
      useNotificationStore().applyRemoteNotification(notification)
    }))
  }
}

export function stopRealtime() {
  unsubscribeRealtime.forEach((unsubscribe) => unsubscribe())
  unsubscribeRealtime = []
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

function currentUserRole(): UserRole | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as { user?: { role?: UserRole } }
    return session.user?.role ?? null
  } catch {
    return null
  }
}
