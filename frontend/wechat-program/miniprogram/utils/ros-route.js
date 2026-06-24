const { round3, isInside } = require('./ros-map')

function numOr(value, fallback) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function createEmptyRosRoute(routeId, routeName) {
  const id = routeId || 'route_patrol_001'
  const name = routeName || '本地巡逻路线'
  return {
    version: 2,
    frame_id: 'map',
    active_route_id: id,
    start_pose: {
      name: '初始起点',
      pose: { x: 0, y: 0, yaw: 0 },
      publish_initial_pose: true,
      covariance: { x: 0.25, y: 0.25, yaw: 0.0685 },
    },
    targets: [],
    routes: [{
      id,
      name,
      target_ids: [],
      return_to_start: true,
      loop: { enabled: false, wait_sec: 600, max_cycles: 0 },
      goal_timeout_sec: 120,
      max_retries_per_checkpoint: 1,
      failure_policy: 'abort_and_return_home',
    }],
    schedules: [],
  }
}

function validateRosRoute(route) {
  if (!route || route.version !== 2 || route.frame_id !== 'map') {
    throw new Error('路线 JSON 必须是 version=2 且 frame_id=map')
  }
}

function loadRosRoute(raw, fallbackId, fallbackName) {
  validateRosRoute(raw)
  const rosRoute = JSON.parse(JSON.stringify(raw))
  if (!rosRoute.routes || !rosRoute.routes.length) {
    rosRoute.routes = [createEmptyRosRoute(fallbackId, fallbackName).routes[0]]
  }
  if (!rosRoute.active_route_id) {
    rosRoute.active_route_id = rosRoute.routes[0].id
  }
  return rosRoute
}

function orderedTargets(rosRoute) {
  if (!rosRoute) return []
  const byId = new Map((rosRoute.targets || []).map((t) => [t.id, t]))
  const routeDef = rosRoute.routes && rosRoute.routes[0]
  const ids = routeDef && routeDef.target_ids && routeDef.target_ids.length
    ? routeDef.target_ids
    : (rosRoute.targets || []).map((t) => t.id)
  return ids.map((id) => byId.get(id)).filter(Boolean)
}

function nextTargetId(targets) {
  const maxNo = (targets || []).reduce((max, target) => {
    const match = String(target.id).match(/target_(\d+)/)
    return Math.max(max, match ? Number(match[1]) : 0)
  }, targets ? targets.length : 0)
  return `target_${String(maxNo + 1).padStart(3, '0')}`
}

function computeYawFromPixel(originPx, pointerPx) {
  return round3(-Math.atan2(pointerPx.y - originPx.y, pointerPx.x - originPx.x))
}

/** 与 route_map_tool.html buildRouteJson 字段一致 */
function buildRouteJson(rosRoute) {
  if (!rosRoute) return null
  const routeDef = rosRoute.routes && rosRoute.routes[0] ? rosRoute.routes[0] : {}
  const routeId = String(routeDef.id || 'route_patrol_001').trim() || 'route_patrol_001'
  const activeRouteId = String(rosRoute.active_route_id || routeId).trim() || routeId
  const start = rosRoute.start_pose || {}
  const pose = start.pose || { x: 0, y: 0, yaw: 0 }
  const cov = start.covariance || { x: 0.25, y: 0.25, yaw: 0.0685 }
  const targets = orderedTargets(rosRoute)

  return {
    version: 2,
    frame_id: 'map',
    active_route_id: activeRouteId,
    start_pose: {
      name: String(start.name || '初始起点').trim() || '初始起点',
      pose: {
        x: round3(pose.x),
        y: round3(pose.y),
        yaw: round3(pose.yaw || 0),
      },
      publish_initial_pose: start.publish_initial_pose !== false,
      covariance: {
        x: numOr(cov.x, 0.25),
        y: numOr(cov.y, 0.25),
        yaw: numOr(cov.yaw, 0.0685),
      },
    },
    targets: targets.map((target) => ({
      id: target.id,
      name: target.name || target.id,
      pose: {
        x: round3(target.pose.x),
        y: round3(target.pose.y),
        yaw: round3(target.pose.yaw || 0),
      },
      task_duration_sec: numOr(target.task_duration_sec, 5),
    })),
    routes: [{
      id: routeId,
      name: String(routeDef.name || '本地巡逻路线').trim() || '本地巡逻路线',
      target_ids: targets.map((t) => t.id),
      return_to_start: routeDef.return_to_start !== false,
      loop: {
        enabled: !!(routeDef.loop && routeDef.loop.enabled),
        wait_sec: Math.max(0, Math.floor(numOr(routeDef.loop && routeDef.loop.wait_sec, 600))),
        max_cycles: Math.max(0, Math.floor(numOr(routeDef.loop && routeDef.loop.max_cycles, 0))),
      },
      goal_timeout_sec: Math.max(1, Math.floor(numOr(routeDef.goal_timeout_sec, 120))),
      max_retries_per_checkpoint: Math.max(0, Math.floor(numOr(routeDef.max_retries_per_checkpoint, 1))),
      failure_policy: routeDef.failure_policy === 'abort' ? 'abort' : 'abort_and_return_home',
    }],
    schedules: [],
  }
}

function getOutOfBoundsLabels(rosRoute, mapMeta) {
  if (!rosRoute || !mapMeta) return []
  const labels = []
  const start = rosRoute.start_pose && rosRoute.start_pose.pose
  if (start && !isInside(start.x, start.y, mapMeta)) labels.push('起点')
  orderedTargets(rosRoute).forEach((target, index) => {
    if (!isInside(target.pose.x, target.pose.y, mapMeta)) {
      labels.push(`#${index + 1}`)
    }
  })
  return labels
}

function getYawTargetLabel(rosRoute, yawTargetKind, yawTargetId) {
  if (!yawTargetKind || yawTargetKind === 'start') return '起点'
  const targets = orderedTargets(rosRoute)
  const index = targets.findIndex((t) => t.id === yawTargetId)
  if (index < 0) return '巡检点'
  return `#${index + 1} ${targets[index].name}`
}

function buildTargetStatus(rosRoute, mapMeta, yawTargetKind, yawTargetId) {
  const outOfBounds = getOutOfBoundsLabels(rosRoute, mapMeta)
  const yawLabel = getYawTargetLabel(rosRoute, yawTargetKind, yawTargetId)
  if (outOfBounds.length) {
    return {
      targetStatus: `越界提示：${outOfBounds.join('、')} 不在当前地图范围内，请换回对应地图或重新标定。`,
      targetStatusError: true,
      yawTargetLabel: yawLabel,
    }
  }
  const count = orderedTargets(rosRoute).length
  if (count) {
    return {
      targetStatus: `共 ${count} 个巡检点，顺序即导航顺序。当前方向点：${yawLabel}。`,
      targetStatusError: false,
      yawTargetLabel: yawLabel,
    }
  }
  return {
    targetStatus: `还没有巡检点。当前方向点：${yawLabel}。`,
    targetStatusError: false,
    yawTargetLabel: yawLabel,
  }
}

module.exports = {
  createEmptyRosRoute,
  validateRosRoute,
  loadRosRoute,
  orderedTargets,
  nextTargetId,
  computeYawFromPixel,
  buildRouteJson,
  getOutOfBoundsLabels,
  getYawTargetLabel,
  buildTargetStatus,
  round3,
  numOr,
}
