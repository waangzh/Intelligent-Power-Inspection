import type { JsonObject, JsonValue, MapPose, Pose2D } from '@/types/routeExecutor'

export function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function cloneJson<T extends JsonValue>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export function asRecord(value: unknown): JsonObject {
  return isJsonObject(value) ? value : {}
}

export function finiteNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export function poseOf(value: unknown): Pose2D {
  const source = asRecord(value)
  return { x: finiteNumber(source.x), y: finiteNumber(source.y), yaw: finiteNumber(source.yaw) }
}

export function mapPose(pose: Pose2D): MapPose {
  return { type: 'map_pose', frame_id: 'map', x: pose.x, y: pose.y, yaw: pose.yaw }
}
