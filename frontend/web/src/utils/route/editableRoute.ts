import type { EditableKeepoutZone, EditableRouteDraft, EditableTarget, PlatformRouteContext, RouteExecutorDocument } from '@/types/routeExecutor'
import { asRecord, finiteNumber, poseOf } from './common'

const defaultRouteId = 'route_patrol_001'

export function createEditableRouteDraft(context?: PlatformRouteContext): EditableRouteDraft {
  const id = context?.defaultRouteId || defaultRouteId
  return {
    sourceTemplate: null, requiresConversion: false,
    start: { name: '初始起点', x: 0, y: 0, yaw: 0, publishInitialPose: true, covX: 0.25, covY: 0.25, covYaw: 0.0685 },
    targets: [], keepoutZones: [],
    route: { id, name: context?.defaultRouteName || '本地巡检路线', goalTimeout: 120, maxRetries: 0, failurePolicy: 'abort', returnToStart: true, loopEnabled: false, loopWait: 600, maxCycles: 0 },
  }
}

export function toEditableRouteDraft(document: RouteExecutorDocument, context?: PlatformRouteContext): EditableRouteDraft {
  const draft = createEditableRouteDraft(context)
  const start = asRecord(document.start_pose)
  const startPose = poseOf(start.pose)
  const covariance = asRecord(start.covariance)
  draft.sourceTemplate = document
  draft.requiresConversion = document.version === 2
  draft.start = {
    name: typeof start.name === 'string' && start.name.trim() ? start.name : '初始起点', ...startPose,
    publishInitialPose: start.publish_initial_pose !== false,
    covX: finiteNumber(covariance.x, 0.25), covY: finiteNumber(covariance.y, 0.25), covYaw: finiteNumber(covariance.yaw, 0.0685),
  }
  const firstRoute = asRecord(document.routes[0])
  const targetById = new Map(document.targets.map((target) => [target.id, target]))
  const ordered = Array.isArray(firstRoute.target_ids) && firstRoute.target_ids.length ? firstRoute.target_ids : document.targets.map((target) => target.id)
  draft.targets = ordered
    .filter((id): id is string => typeof id === 'string')
    .map((id) => targetById.get(id))
    .filter((target): target is NonNullable<typeof target> => Boolean(target))
    .map(toEditableTarget)
  const loop = asRecord(firstRoute.loop)
  draft.route = {
    id: typeof firstRoute.id === 'string' && firstRoute.id ? firstRoute.id : draft.route.id,
    name: typeof firstRoute.name === 'string' && firstRoute.name ? firstRoute.name : draft.route.name,
    goalTimeout: finiteNumber(firstRoute.goal_timeout_sec, 120), maxRetries: finiteNumber(firstRoute.max_retries_per_checkpoint, 0),
    failurePolicy: firstRoute.failure_policy === 'abort_and_return_home' ? 'abort_and_return_home' : 'abort',
    returnToStart: firstRoute.return_to_start === true, loopEnabled: loop.enabled === true,
    loopWait: finiteNumber(loop.wait_sec, 600), maxCycles: finiteNumber(loop.max_cycles, 0),
  }
  draft.keepoutZones = (document.keepout_zones || []).map((zone): EditableKeepoutZone => ({
    id: zone.id, name: typeof zone.name === 'string' ? zone.name : zone.id, type: 'hard_keepout', enabled: zone.enabled,
    maskPaddingM: finiteNumber(zone.mask_padding_m), polygon: zone.polygon.map((point) => ({ x: finiteNumber(point.x), y: finiteNumber(point.y) })),
  }))
  return draft
}

function toEditableTarget(target: RouteExecutorDocument['targets'][number]): EditableTarget {
  const pose = poseOf(target.pose)
  return { id: target.id, name: target.name || target.id, ...pose, taskDuration: finiteNumber(target.task_duration_sec, 0) }
}
