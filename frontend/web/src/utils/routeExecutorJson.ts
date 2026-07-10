import type { RouteExecutorDocument, RouteExecutorTarget } from '@/types/routeExecutor'
import { round3 } from '@/utils/rosMap'

export interface RouteFormState {
  startName: string
  startX: number
  startY: number
  startYaw: number
  publishInitialPose: boolean
  covX: number
  covY: number
  covYaw: number
  routeId: string
  activeRouteId: string
  routeName: string
  goalTimeout: number
  maxRetries: number
  failurePolicy: 'abort_and_return_home' | 'abort'
  returnToStart: boolean
  loopEnabled: boolean
  loopWait: number
  maxCycles: number
}

export function createDefaultRouteForm(routeId = 'route_patrol_001', routeName?: string): RouteFormState {
  return {
    startName: '初始起点',
    startX: 0,
    startY: 0,
    startYaw: 0,
    publishInitialPose: true,
    covX: 0.25,
    covY: 0.25,
    covYaw: 0.0685,
    routeId,
    activeRouteId: routeId,
    routeName: routeName?.trim() || '本地巡逻路线',
    goalTimeout: 120,
    maxRetries: 1,
    failurePolicy: 'abort_and_return_home',
    returnToStart: true,
    loopEnabled: false,
    loopWait: 600,
    maxCycles: 0,
  }
}

export function loadRouteJson(
  route: RouteExecutorDocument,
  form: RouteFormState,
): { targets: RouteExecutorTarget[]; nextTargetNo: number } {
  if (route.version !== 2 || route.frame_id !== 'map') {
    throw new Error('路线 JSON 必须是 version=2 且 frame_id=map。')
  }

  if (route.start_pose) {
    form.startName = route.start_pose.name || '初始起点'
    form.startX = route.start_pose.pose?.x ?? 0
    form.startY = route.start_pose.pose?.y ?? 0
    form.startYaw = route.start_pose.pose?.yaw ?? 0
    form.publishInitialPose = route.start_pose.publish_initial_pose !== false
    form.covX = route.start_pose.covariance?.x ?? 0.25
    form.covY = route.start_pose.covariance?.y ?? 0.25
    form.covYaw = route.start_pose.covariance?.yaw ?? 0.0685
  }

  const firstRoute = Array.isArray(route.routes) ? route.routes[0] : null
  if (firstRoute) {
    form.routeId = firstRoute.id || form.routeId
    form.activeRouteId = route.active_route_id || firstRoute.id || form.routeId
    form.routeName = firstRoute.name || form.routeName
    form.returnToStart = firstRoute.return_to_start !== false
    form.loopEnabled = Boolean(firstRoute.loop?.enabled)
    form.loopWait = firstRoute.loop?.wait_sec ?? 600
    form.maxCycles = firstRoute.loop?.max_cycles ?? 0
    form.goalTimeout = firstRoute.goal_timeout_sec ?? 120
    form.maxRetries = firstRoute.max_retries_per_checkpoint ?? 1
    form.failurePolicy = firstRoute.failure_policy === 'abort' ? 'abort' : 'abort_and_return_home'
  }

  const byId = new Map((route.targets || []).map((target) => [target.id, target]))
  const orderedIds = firstRoute?.target_ids?.length
    ? firstRoute.target_ids
    : (route.targets || []).map((target) => target.id)

  const targets = orderedIds
    .map((id) => byId.get(id))
    .filter(Boolean)
    .map((target, index) => ({
      id: target!.id || `target_${String(index + 1).padStart(3, '0')}`,
      name: target!.name || `巡检点${index + 1}`,
      x: Number(target!.pose?.x ?? 0),
      y: Number(target!.pose?.y ?? 0),
      yaw: Number(target!.pose?.yaw ?? 0),
      taskDuration: Number(target!.task_duration_sec ?? 5),
    }))

  const maxNo = targets.reduce((max, target) => {
    const match = String(target.id).match(/target_(\d+)/)
    return Math.max(max, match ? Number(match[1]) : 0)
  }, targets.length)

  return { targets, nextTargetNo: maxNo + 1 }
}

export function buildRouteJson(form: RouteFormState, targets: RouteExecutorTarget[]): RouteExecutorDocument {
  const routeId = form.routeId.trim() || 'route_patrol_001'
  return {
    version: 2,
    frame_id: 'map',
    active_route_id: form.activeRouteId.trim() || routeId,
    start_pose: {
      name: form.startName.trim() || '初始起点',
      pose: {
        x: round3(form.startX),
        y: round3(form.startY),
        yaw: round3(form.startYaw),
      },
      publish_initial_pose: form.publishInitialPose,
      covariance: {
        x: form.covX,
        y: form.covY,
        yaw: form.covYaw,
      },
    },
    targets: targets.map((target) => ({
      id: target.id,
      name: target.name || target.id,
      pose: {
        x: round3(target.x),
        y: round3(target.y),
        yaw: round3(target.yaw || 0),
      },
      task_duration_sec: target.taskDuration ?? 5,
    })),
    routes: [
      {
        id: routeId,
        name: form.routeName.trim() || '本地巡逻路线',
        target_ids: targets.map((target) => target.id),
        return_to_start: form.returnToStart,
        loop: {
          enabled: form.loopEnabled,
          wait_sec: form.loopWait,
          max_cycles: Math.max(0, Math.floor(form.maxCycles)),
        },
        goal_timeout_sec: form.goalTimeout,
        max_retries_per_checkpoint: Math.max(0, Math.floor(form.maxRetries)),
        failure_policy: form.failurePolicy,
      },
    ],
    schedules: [],
  }
}

export function withPlatformRouteName(doc: RouteExecutorDocument, platformName: string): RouteExecutorDocument {
  const name = platformName.trim()
  if (!name || !doc.routes[0]) return doc
  return {
    ...doc,
    routes: [{ ...doc.routes[0], name }, ...doc.routes.slice(1)],
  }
}

export function downloadRouteJson(doc: RouteExecutorDocument, filename?: string) {
  const text = JSON.stringify(doc, null, 2)
  const blob = new Blob([text], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || `${doc.routes[0]?.id || 'route_patrol_001'}.json`
  link.click()
  URL.revokeObjectURL(url)
}
