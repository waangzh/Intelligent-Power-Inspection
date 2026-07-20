import { useAlarmStore } from '@/stores/alarm'
import { setPageRealtimeResources } from '@/stores/bootstrap'
import { useDetectionStore } from '@/stores/detection'
import { useNotificationStore } from '@/stores/notification'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { useWorkOrderStore } from '@/stores/workOrder'

export async function loadRouteData(routeName: unknown, params: Record<string, unknown> = {}) {
  const name = String(routeName ?? '')
  const loaders: Array<Promise<unknown>> = []
  const realtime: Array<'task' | 'taskEvent' | 'robot' | 'alarm'> = []

  if (['Monitor', 'Tasks', 'Statistics', 'BigScreen'].includes(name)) {
    loaders.push(useSiteStore().load(), useRouteStore().load(), useRobotStore().load())
  }
  if (name === 'Routes') {
    const siteStore = useSiteStore()
    await siteStore.loadSites()
    loaders.push(useRouteStore().load(siteStore.sites[0]?.id), useRobotStore().load())
  }
  if (['Monitor', 'Tasks', 'Statistics', 'BigScreen'].includes(name)) {
    loaders.push(useTaskStore().loadDynamic())
    realtime.push('task', 'robot')
  }
  if (['Alarms', 'Monitor', 'Statistics', 'BigScreen'].includes(name)) {
    loaders.push(useAlarmStore().load({ size: 10 }))
    realtime.push('alarm')
  }
  if (name === 'WorkOrders') loaders.push(useWorkOrderStore().load(), useSiteStore().loadSites())
  if (name === 'Notifications') loaders.push(useNotificationStore().load())
  if (name === 'Sites') loaders.push(useSiteStore().loadSites())
  if (name === 'Robots') loaders.push(useSiteStore().loadSites(), useRobotStore().load())
  if (name === 'Detection') {
    loaders.push(
      useDetectionStore().load(),
      useDetectionStore().loadImages(),
      useDetectionStore().loadRuns(),
      useTaskStore().loadDynamic({ size: 100 }),
      useRouteStore().load(),
      useRobotStore().load(undefined, { size: 100 }),
    )
  }
  if (name === 'Records') loaders.push(useTaskStore().loadRecords())
  if (name === 'Dashboard') realtime.push('task', 'robot', 'alarm')
  if (name === 'TaskDetail' && typeof params.id === 'string') {
    const task = await useTaskStore().loadOne(params.id)
    const route = await useRouteStore().loadOne(task.routeId)
    await Promise.all([
      useRobotStore().loadOne(task.robotId),
      useSiteStore().loadOne(route.siteId),
      useAlarmStore().load({ taskId: task.id }),
      useDetectionStore().loadRuns({ taskId: task.id, size: 100 }),
    ])
    realtime.push('task', 'taskEvent', 'robot', 'alarm')
  }

  await Promise.allSettled(loaders)
  setPageRealtimeResources([...new Set(realtime)])
}
