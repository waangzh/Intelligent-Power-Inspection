import type { EditableKeepoutZone, JsonObject, Pose2D, RouteExecutorDocumentV3, RosMapState } from '@/types/routeExecutor'
import { mapToPixel, pixelToMap, rosMapImageFileName, round3 } from '@/utils/rosMap'

export type MapClass = 'free' | 'unknown' | 'occupied' | 'outside' | 'unloaded' | 'keepout'

export interface PointSafety extends JsonObject {
  validation_status: 'ok' | 'warning' | 'unsafe'
  min_keepout_distance_m: number | null
  warnings: string[]
}

export interface CalculatedRouteSafety extends JsonObject {
  validation_status: 'ok' | 'warning' | 'unsafe'
  min_keepout_distance_m: number | null
  warnings: string[]
  pointSafety: Record<string, PointSafety>
}

export interface RouteSafetyPoint extends Pose2D { id: string; label: string }

const ROBOT_FOOTPRINT = [
  { x: 0.1865, y: 0 }, { x: 0.16543, y: 0.10591 }, { x: 0.10544, y: 0.19569 }, { x: 0.01566, y: 0.25568 },
  { x: -0.09025, y: 0.27675 }, { x: -0.19616, y: 0.25568 }, { x: -0.28594, y: 0.19569 }, { x: -0.34593, y: 0.10591 },
  { x: -0.367, y: 0 }, { x: -0.34593, y: -0.10591 }, { x: -0.28594, y: -0.19569 }, { x: -0.19616, y: -0.25568 },
  { x: -0.09025, y: -0.27675 }, { x: 0.01566, y: -0.25568 }, { x: 0.10544, y: -0.19569 }, { x: 0.16543, y: -0.10591 },
]
const FOOTPRINT_PADDING = 0.01
const EPSILON = 1e-9

export function describeMapClass(kind: MapClass) {
  return ({ outside: '越界', occupied: '障碍区', unknown: '未知区', unloaded: '地图未加载', keepout: '禁行区', free: '空闲区' } satisfies Record<MapClass, string>)[kind]
}

export function classifyMapCoordinate(map: RosMapState, x: number, y: number): MapClass {
  if (!map.width || !map.height || !map.pixels) return 'unloaded'
  const pixel = mapToPixel(map, x, y)
  const px = Math.floor(pixel.x)
  const py = Math.floor(pixel.y)
  if (px < 0 || px >= map.width || py < 0 || py >= map.height) return 'outside'
  const raw = map.pixels[py * map.width + px]
  const color = map.negate ? 255 - raw : raw
  const occupancy = (255 - color) / 255
  if (occupancy > map.occupiedThresh) return 'occupied'
  if (occupancy < map.freeThresh) return 'free'
  return 'unknown'
}

export function transformFootprint(pose: Pose2D, padded = false) {
  const footprint = padded
    ? ROBOT_FOOTPRINT.map((point) => ({ x: point.x + (point.x > 0 ? FOOTPRINT_PADDING : point.x < 0 ? -FOOTPRINT_PADDING : 0), y: point.y + (point.y > 0 ? FOOTPRINT_PADDING : point.y < 0 ? -FOOTPRINT_PADDING : 0) }))
    : ROBOT_FOOTPRINT
  const cos = Math.cos(pose.yaw || 0)
  const sin = Math.sin(pose.yaw || 0)
  return footprint.map((point) => ({ x: pose.x + point.x * cos - point.y * sin, y: pose.y + point.x * sin + point.y * cos }))
}

export function classifyFootprint(map: RosMapState, zones: EditableKeepoutZone[], pose: Pose2D): MapClass {
  if (!map.width || !map.height || !map.pixels) return 'unloaded'
  const footprint = transformFootprint(pose, true)
  const activeZones = zones.filter((zone) => zone.enabled && zone.type === 'hard_keepout' && zone.polygon.length >= 3)
  if (activeZones.some((zone) => pointInPolygon(pose, zone.polygon) || polygonsIntersect(footprint, zone.polygon))) return 'keepout'

  const polygon = footprint.map((point) => mapToPixel(map, point.x, point.y))
  const xs = polygon.map((point) => point.x)
  const ys = polygon.map((point) => point.y)
  const classes: MapClass[] = []
  const add = (kind: MapClass) => { if (kind !== 'free' && !classes.includes(kind)) classes.push(kind) }
  footprint.forEach((point) => add(classifyMapCoordinate(map, point.x, point.y)))
  add(classifyMapCoordinate(map, pose.x, pose.y))
  for (let py = Math.floor(Math.min(...ys)); py <= Math.ceil(Math.max(...ys)); py += 1) {
    for (let px = Math.floor(Math.min(...xs)); px <= Math.ceil(Math.max(...xs)); px += 1) {
      if (!pointInPolygon({ x: px + 0.5, y: py + 0.5 }, polygon)) continue
      const point = pixelToMap(map, px + 0.5, py + 0.5)
      add(classifyMapCoordinate(map, point.x, point.y))
    }
  }
  if (classes.includes('outside')) return 'outside'
  if (classes.includes('occupied')) return 'occupied'
  if (classes.includes('unknown')) return 'unknown'
  return 'free'
}

export function calculateRouteSafety(map: RosMapState, zones: EditableKeepoutZone[], points: RouteSafetyPoint[]): CalculatedRouteSafety {
  const activeZones = zones.filter((zone) => zone.enabled && zone.type === 'hard_keepout' && zone.polygon.length >= 3)
  const problems: string[] = []
  const warnings: string[] = []
  const pointSafety: Record<string, PointSafety> = {}
  let minimum: number | null = null

  for (const point of points) {
    const kind = classifyFootprint(map, zones, point)
    if (kind !== 'free') problems.push(`${point.label} footprint(${round3(point.x)}, ${round3(point.y)})=${describeMapClass(kind)}`)
    const footprint = transformFootprint(point, true)
    const distances = activeZones.map((zone) => polygonDistance(footprint, zone.polygon))
    const distance = distances.length ? Math.min(...distances) : null
    if (distance !== null) minimum = minimum === null ? distance : Math.min(minimum, distance)
    const pointWarnings = activeZones
      .filter((zone, index) => distances[index] < 0.2 && !polygonsIntersect(footprint, zone.polygon))
      .map((zone) => `${point.id} footprint is closer than 0.20m to ${zone.id}`)
    warnings.push(...pointWarnings)
    pointSafety[point.id] = {
      validation_status: kind !== 'free' ? 'unsafe' : pointWarnings.length ? 'warning' : 'ok',
      min_keepout_distance_m: distance,
      warnings: pointWarnings,
    }
  }
  return {
    validation_status: problems.length ? 'unsafe' : warnings.length ? 'warning' : 'ok',
    min_keepout_distance_m: minimum,
    warnings: [...problems, ...warnings],
    pointSafety,
  }
}

export function validateAnnotationExport(map: RosMapState, imageSha256: string, zones: EditableKeepoutZone[], points: RouteSafetyPoint[]): string[] {
  if (!map.width || !map.height || !map.pixels) return ['请先加载对应的 PGM 地图后再保存或导出。']
  if (!map.yamlName || !map.pgmName || !map.image || rosMapImageFileName(map.image).toLowerCase() !== rosMapImageFileName(map.pgmName).toLowerCase() || !/^[0-9a-f]{64}$/.test(imageSha256)) {
    return ['当前地图身份不完整，请重新加载 YAML 和 PGM 后再保存或导出。']
  }
  const zoneProblems = zones.flatMap((zone) => {
    if (zone.polygon.length < 3) return [`${zone.name || zone.id} 至少需要 3 个顶点，当前只有 ${zone.polygon.length} 个。`]
    if (polygonSelfIntersects(zone.polygon)) return [`${zone.name || zone.id} 的 polygon 不能自交。`]
    if (Math.abs(polygonArea(zone.polygon)) <= EPSILON) return [`${zone.name || zone.id} 的 polygon 面积必须大于零。`]
    return []
  })
  return [...zoneProblems, ...calculateRouteSafety(map, zones, points).warnings.filter((warning) => warning.includes('footprint('))]
}

export function applyRouteSafety(document: RouteExecutorDocumentV3, safety: CalculatedRouteSafety): RouteExecutorDocumentV3 {
  return {
    ...document,
    targets: document.targets.map((target) => ({ ...target, safety: safety.pointSafety[target.id] })),
    safety: { validation_status: safety.validation_status, min_keepout_distance_m: safety.min_keepout_distance_m, warnings: safety.warnings },
  }
}

export function polygonArea(points: Array<{ x: number; y: number }>) {
  return points.reduce((sum, point, index) => {
    const next = points[(index + 1) % points.length]
    return sum + point.x * next.y - point.y * next.x
  }, 0) / 2
}

export function polygonSelfIntersects(points: Array<{ x: number; y: number }>) {
  for (let i = 0; i < points.length; i += 1) {
    for (let j = i + 1; j < points.length; j += 1) {
      if (j === i + 1 || (i === 0 && j === points.length - 1)) continue
      if (segmentsIntersect(points[i], points[(i + 1) % points.length], points[j], points[(j + 1) % points.length])) return true
    }
  }
  return false
}

function pointInPolygon(point: { x: number; y: number }, polygon: Array<{ x: number; y: number }>) {
  let inside = false
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i, i += 1) {
    const a = polygon[i]
    const b = polygon[j]
    const crosses = (a.y > point.y) !== (b.y > point.y)
    if (crosses && point.x < ((b.x - a.x) * (point.y - a.y)) / (b.y - a.y) + a.x) inside = !inside
  }
  return inside
}

function polygonsIntersect(first: Array<{ x: number; y: number }>, second: Array<{ x: number; y: number }>) {
  return first.some((point) => pointInPolygon(point, second)) || second.some((point) => pointInPolygon(point, first)) || first.some((point, index) =>
    second.some((other, otherIndex) => segmentsIntersect(point, first[(index + 1) % first.length], other, second[(otherIndex + 1) % second.length])),
  )
}

function polygonDistance(first: Array<{ x: number; y: number }>, second: Array<{ x: number; y: number }>) {
  if (polygonsIntersect(first, second)) return 0
  return Math.min(...first.flatMap((point) => second.map((other, index) => pointSegmentDistance(point, other, second[(index + 1) % second.length]))))
}

function pointSegmentDistance(point: { x: number; y: number }, a: { x: number; y: number }, b: { x: number; y: number }) {
  const dx = b.x - a.x
  const dy = b.y - a.y
  if (dx === 0 && dy === 0) return Math.hypot(point.x - a.x, point.y - a.y)
  const ratio = Math.max(0, Math.min(1, ((point.x - a.x) * dx + (point.y - a.y) * dy) / (dx * dx + dy * dy)))
  return Math.hypot(point.x - (a.x + ratio * dx), point.y - (a.y + ratio * dy))
}

function segmentsIntersect(a: { x: number; y: number }, b: { x: number; y: number }, c: { x: number; y: number }, d: { x: number; y: number }) {
  const cross = (p: typeof a, q: typeof a, r: typeof a) => (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
  const abC = cross(a, b, c)
  const abD = cross(a, b, d)
  const cdA = cross(c, d, a)
  const cdB = cross(c, d, b)
  const onSegment = (p: typeof a, q: typeof a, r: typeof a) => Math.abs(cross(p, q, r)) <= EPSILON && r.x >= Math.min(p.x, q.x) - EPSILON && r.x <= Math.max(p.x, q.x) + EPSILON && r.y >= Math.min(p.y, q.y) - EPSILON && r.y <= Math.max(p.y, q.y) + EPSILON
  return (abC * abD < -EPSILON && cdA * cdB < -EPSILON)
    || onSegment(a, b, c)
    || onSegment(a, b, d)
    || onSegment(c, d, a)
    || onSegment(c, d, b)
}
