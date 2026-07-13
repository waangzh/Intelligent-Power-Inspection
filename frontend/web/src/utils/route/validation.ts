import type { KeepoutZone, RouteExecutorDocumentV3, RouteValidationIssue, RouteValidationResult } from '@/types/routeExecutor'

const sha256 = /^[0-9a-f]{64}$/
const finite = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value)
const issue = (issues: RouteValidationIssue[], code: string, jsonPointer: string, message: string) => issues.push({ code, jsonPointer, message, severity: 'ERROR' })

export function validateRouteDocument(document: RouteExecutorDocumentV3): RouteValidationResult {
  const issues: RouteValidationIssue[] = []
  if (document.version !== 3) issue(issues, 'INVALID_VERSION', '/version', 'version 必须为 3')
  if (document.frame_id !== 'map') issue(issues, 'INVALID_FRAME_ID', '/frame_id', 'frame_id 必须为 map')
  const map = document.map
  if (!map || !map.yaml || !map.image) issue(issues, 'INVALID_MAP', '/map', 'map 必须包含 yaml 和 image')
  if (!finite(map?.resolution) || map.resolution <= 0) issue(issues, 'INVALID_MAP_RESOLUTION', '/map/resolution', 'resolution 必须为有限正数')
  if (!Array.isArray(map?.origin) || map.origin.length !== 3 || !map.origin.every(finite)) issue(issues, 'INVALID_MAP_ORIGIN', '/map/origin', 'origin 必须为三个有限数')
  for (const key of ['width', 'height'] as const) if (!Number.isInteger(map?.[key]) || map[key] <= 0) issue(issues, 'INVALID_MAP_SIZE', `/map/${key}`, `${key} 必须为正整数`)
  if (!sha256.test(map?.image_sha256 || '')) issue(issues, 'INVALID_MAP_SHA256', '/map/image_sha256', 'image_sha256 必须为 64 位小写十六进制')
  if (!Array.isArray(document.routes) || document.routes.length !== 1) issue(issues, 'MULTIPLE_ROUTES', '/routes', '平台路线必须且只能包含一条 route')
  if (!Array.isArray(document.schedules) || document.schedules.length !== 0) issue(issues, 'NON_EMPTY_SCHEDULES', '/schedules', '平台路线 schedules 必须为空数组')
  const route = document.routes?.[0]
  if (!route || document.active_route_id !== route.id) issue(issues, 'INVALID_ACTIVE_ROUTE', '/active_route_id', 'active_route_id 必须指向唯一 route')
  validatePoseLocation(document.start_pose?.pose, document.start_pose?.location, '/start_pose', issues)
  const ids = new Set<string>()
  document.targets?.forEach((target, index) => {
    const pointer = `/targets/${index}`
    if (!target.id || ids.has(target.id)) issue(issues, 'DUPLICATE_TARGET_ID', `${pointer}/id`, '目标 id 必须非空且唯一')
    ids.add(target.id)
    validatePoseLocation(target.pose, target.location, pointer, issues)
  })
  const targetIds = route?.target_ids || []
  if (targetIds.length !== document.targets.length || targetIds.some((id, index) => id !== document.targets[index]?.id)) issue(issues, 'TARGET_ORDER_MISMATCH', '/routes/0/target_ids', 'target_ids 必须与编辑目标顺序完全一致')
  if (new Set(targetIds).size !== targetIds.length) issue(issues, 'DUPLICATE_TARGET_REFERENCE', '/routes/0/target_ids', 'target_ids 不得重复')
  targetIds.forEach((id, index) => { if (!ids.has(id)) issue(issues, 'UNKNOWN_TARGET_REFERENCE', `/routes/0/target_ids/${index}`, 'target_ids 引用了不存在的目标') })
  if (!finite(route?.goal_timeout_sec) || route.goal_timeout_sec <= 0) issue(issues, 'INVALID_GOAL_TIMEOUT', '/routes/0/goal_timeout_sec', 'goal_timeout_sec 必须为正数')
  const retries = route?.max_retries_per_checkpoint
  const loopWait = route?.loop?.wait_sec
  const loopCycles = route?.loop?.max_cycles
  if (!Number.isInteger(retries) || retries === undefined || retries < 0) issue(issues, 'INVALID_RETRIES', '/routes/0/max_retries_per_checkpoint', 'max_retries_per_checkpoint 必须为非负整数')
  if (!finite(loopWait) || loopWait < 0) issue(issues, 'INVALID_LOOP_WAIT', '/routes/0/loop/wait_sec', 'loop.wait_sec 必须为非负数')
  if (!Number.isInteger(loopCycles) || loopCycles === undefined || loopCycles < 0) issue(issues, 'INVALID_LOOP_CYCLES', '/routes/0/loop/max_cycles', 'loop.max_cycles 必须为非负整数')
  if (route?.failure_policy !== 'abort' && route?.failure_policy !== 'abort_and_return_home') issue(issues, 'INVALID_FAILURE_POLICY', '/routes/0/failure_policy', 'failure_policy 不合法')
  document.keepout_zones?.forEach((zone, index) => validateKeepout(zone, index, map?.resolution, issues))
  return { valid: issues.length === 0, issues }
}

function validatePoseLocation(pose: unknown, location: unknown, pointer: string, issues: RouteValidationIssue[]) {
  const p = pose as { x?: unknown; y?: unknown; yaw?: unknown } | undefined
  const l = location as { type?: unknown; frame_id?: unknown; x?: unknown; y?: unknown; yaw?: unknown } | undefined
  for (const key of ['x', 'y', 'yaw'] as const) {
    if (!finite(p?.[key])) issue(issues, 'INVALID_POSE', `${pointer}/pose/${key}`, 'pose 坐标必须为有限数')
    if (!finite(l?.[key]) || (finite(p?.[key]) && l?.[key] !== p[key])) issue(issues, 'INVALID_LOCATION', `${pointer}/location/${key}`, 'location 必须与 pose 一致')
  }
  if (l?.type !== 'map_pose') issue(issues, 'INVALID_LOCATION_TYPE', `${pointer}/location/type`, 'location.type 必须为 map_pose')
  if (l?.frame_id !== 'map') issue(issues, 'INVALID_LOCATION_FRAME', `${pointer}/location/frame_id`, 'location.frame_id 必须为 map')
}

function validateKeepout(zone: KeepoutZone, index: number, resolution: number, issues: RouteValidationIssue[]) {
  const base = `/keepout_zones/${index}`
  if (!zone.id) issue(issues, 'INVALID_KEEP_OUT_ID', `${base}/id`, '禁行区 id 不得为空')
  if (zone.type !== 'hard_keepout') issue(issues, 'INVALID_KEEP_OUT_TYPE', `${base}/type`, '禁行区类型必须为 hard_keepout')
  if (typeof zone.enabled !== 'boolean') issue(issues, 'INVALID_KEEP_OUT_ENABLED', `${base}/enabled`, 'enabled 必须为布尔值')
  if (!Array.isArray(zone.polygon) || zone.polygon.length < 3) issue(issues, 'INVALID_POLYGON', `${base}/polygon`, 'polygon 至少包含三个点')
  if (zone.polygon.some((point) => !finite(point.x) || !finite(point.y))) issue(issues, 'INVALID_POLYGON_POINT', `${base}/polygon`, 'polygon 坐标必须为有限数')
  if (polygonSelfIntersects(zone.polygon)) issue(issues, 'SELF_INTERSECTING_POLYGON', `${base}/polygon`, 'polygon 不得自交')
  if (Math.abs(polygonArea(zone.polygon)) <= 1e-9) issue(issues, 'ZERO_AREA_POLYGON', `${base}/polygon`, 'polygon 面积必须大于零')
  if (!finite(zone.mask_padding_m) || zone.mask_padding_m < 0 || zone.mask_padding_m > resolution) issue(issues, 'INVALID_MASK_PADDING', `${base}/mask_padding_m`, 'mask_padding_m 必须在 0 到 map.resolution 之间')
}

function polygonArea(points: Array<{ x: number; y: number }>) { return points.reduce((sum, p, i) => sum + p.x * points[(i + 1) % points.length].y - p.y * points[(i + 1) % points.length].x, 0) / 2 }
function polygonSelfIntersects(points: Array<{ x: number; y: number }>) {
  for (let i = 0; i < points.length; i += 1) for (let j = i + 1; j < points.length; j += 1) {
    if (j === i + 1 || (i === 0 && j === points.length - 1)) continue
    if (segmentsIntersect(points[i], points[(i + 1) % points.length], points[j], points[(j + 1) % points.length])) return true
  }
  return false
}
function segmentsIntersect(a: { x: number; y: number }, b: { x: number; y: number }, c: { x: number; y: number }, d: { x: number; y: number }) {
  const cross = (p: typeof a, q: typeof a, r: typeof a) => (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
  const abC = cross(a, b, c); const abD = cross(a, b, d); const cdA = cross(c, d, a); const cdB = cross(c, d, b)
  return abC * abD < 0 && cdA * cdB < 0
}
