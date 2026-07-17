import type { EditableRouteDraft, MapAssetIdentity, RouteExecutorDocument, RouteExecutorDocumentV3 } from '@/types/routeExecutor'
import { cloneJson, mapPose } from './common'

export function mergeManagedRouteFields(
  sourceTemplate: RouteExecutorDocument | null,
  editable: EditableRouteDraft,
  mapAsset: MapAssetIdentity,
): RouteExecutorDocumentV3 {
  const base = (sourceTemplate ? cloneJson(sourceTemplate) : {}) as Partial<RouteExecutorDocumentV3>
  if (sourceTemplate && sourceTemplate.routes.length !== 1) {
    throw new Error('路线编辑器仅支持包含一条 route 的文件')
  }
  if (sourceTemplate && (!Array.isArray(sourceTemplate.schedules) || sourceTemplate.schedules.length !== 0)) {
    throw new Error('路线编辑器不支持非空 schedules，已拒绝覆盖以避免数据丢失')
  }
  const sourceTargets = new Map((Array.isArray(base.targets) ? base.targets : []).map((target) => [target.id, target]))
  const sourceZones = new Map((Array.isArray(base.keepout_zones) ? base.keepout_zones : []).map((zone) => [zone.id, zone]))
  const pose = { x: editable.start.x, y: editable.start.y, yaw: editable.start.yaw }
  const targets = editable.targets.map((item) => {
    const targetPose = { x: item.x, y: item.y, yaw: item.yaw }
    return {
      ...(sourceTargets.get(item.id) || {}), id: item.id, name: item.name || item.id, pose: targetPose,
      location: mapPose(targetPose), task_duration_sec: item.taskDuration,
    }
  })
  const route = {
    ...((Array.isArray(base.routes) ? base.routes[0] : {}) || {}), id: editable.route.id, name: editable.route.name,
    target_ids: editable.targets.map((item) => item.id), return_to_start: editable.route.returnToStart,
    loop: { ...(((Array.isArray(base.routes) ? base.routes[0] : {}) || {}).loop || {}), enabled: editable.route.loopEnabled, wait_sec: editable.route.loopWait, max_cycles: editable.route.maxCycles },
    goal_timeout_sec: editable.route.goalTimeout, max_retries_per_checkpoint: editable.route.maxRetries, failure_policy: editable.route.failurePolicy,
  }
  return {
    ...base, version: 3, frame_id: 'map', map: { ...(base.map || {}), ...mapAsset }, active_route_id: editable.route.id,
    start_pose: {
      ...(base.start_pose || {}), name: editable.start.name || '初始起点', frame_id: 'map', pose, location: mapPose(pose),
      publish_initial_pose: editable.start.publishInitialPose,
      covariance: { ...((base.start_pose || {}).covariance || {}), x: editable.start.covX, y: editable.start.covY, yaw: editable.start.covYaw },
    },
    targets, routes: [route],
    keepout_zones: editable.keepoutZones.map((zone) => ({
      ...(sourceZones.get(zone.id) || {}), id: zone.id, name: zone.name || zone.id, type: 'hard_keepout', enabled: zone.enabled,
      mask_padding_m: Math.min(mapAsset.resolution, Math.max(0, zone.maskPaddingM ?? mapAsset.resolution)),
      polygon: zone.polygon.map((point) => ({ x: point.x, y: point.y })),
    })),
    schedules: Array.isArray(base.schedules) ? base.schedules : [],
  } as RouteExecutorDocumentV3
}
