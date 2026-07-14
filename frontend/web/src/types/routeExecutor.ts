/** 路线执行器 JSON 契约。未知字段以 JsonValue 保留，避免编辑器造成数据丢失。 */

export type JsonPrimitive = string | number | boolean | null
export type JsonValue = JsonPrimitive | JsonObject | JsonValue[]
export interface JsonObject { [key: string]: JsonValue | undefined }

export interface Pose2D extends JsonObject { x: number; y: number; yaw: number }
export interface MapPose extends Pose2D { type: 'map_pose'; frame_id: 'map'; [key: string]: JsonValue }
export interface RouteMapIdentity extends JsonObject {
  yaml: string
  image: string
  resolution: number
  origin: [number, number, number]
  width: number
  height: number
  image_sha256: string
}
export interface RouteStartPose extends JsonObject {
  name: string
  frame_id?: 'map'
  pose: Pose2D
  location?: MapPose
  publish_initial_pose?: boolean
  covariance?: Covariance
}
export interface Covariance extends JsonObject { x: number; y: number; yaw: number }
export interface RouteTarget extends JsonObject {
  id: string
  name: string
  pose: Pose2D
  location?: MapPose
  task_duration_sec?: number
}
export interface PatrolRoute extends JsonObject {
  id: string
  name: string
  target_ids: string[]
  return_to_start?: boolean
  loop?: RouteLoop
  goal_timeout_sec?: number
  max_retries_per_checkpoint?: number
  failure_policy?: 'abort' | 'abort_and_return_home'
}
export interface RouteLoop extends JsonObject { enabled?: boolean; wait_sec?: number; max_cycles?: number }
export interface KeepoutZone extends JsonObject {
  id: string
  type: 'hard_keepout'
  enabled: boolean
  polygon: KeepoutPoint[]
  mask_padding_m?: number
}
export interface KeepoutPoint extends JsonObject { x: number; y: number }
export interface RouteSafetyResult extends JsonObject { validation_status?: string; warnings?: JsonValue[] }

export interface RouteExecutorDocumentV2 extends JsonObject {
  version: 2
  frame_id: 'map'
  active_route_id: string
  start_pose: RouteStartPose
  targets: RouteTarget[]
  routes: PatrolRoute[]
  schedules: JsonValue[]
  keepout_zones?: KeepoutZone[]
}
export interface RouteExecutorDocumentV3 extends JsonObject {
  version: 3
  frame_id: 'map'
  map: RouteMapIdentity
  active_route_id: string
  start_pose: RouteStartPose & { frame_id: 'map'; location: MapPose }
  targets: Array<RouteTarget & { location: MapPose }>
  routes: PatrolRoute[]
  keepout_zones: KeepoutZone[]
  schedules: JsonValue[]
  safety?: RouteSafetyResult
}
export type RouteExecutorDocument = RouteExecutorDocumentV2 | RouteExecutorDocumentV3

export interface EditableRoutePoint extends Pose2D { name: string; publishInitialPose: boolean; covX: number; covY: number; covYaw: number }
export interface EditableTarget extends Pose2D { id: string; name: string; taskDuration: number }
export interface EditableKeepoutZone { id: string; name: string; type: 'hard_keepout'; enabled: boolean; maskPaddingM: number; polygon: Array<{ x: number; y: number }> }
export interface EditableRouteDraft {
  sourceTemplate: RouteExecutorDocument | null
  requiresConversion: boolean
  start: EditableRoutePoint
  targets: EditableTarget[]
  keepoutZones: EditableKeepoutZone[]
  route: {
    id: string; name: string; goalTimeout: number; maxRetries: number
    failurePolicy: 'abort' | 'abort_and_return_home'; returnToStart: boolean
    loopEnabled: boolean; loopWait: number; maxCycles: number
  }
}
export interface PlatformRouteContext { defaultRouteId?: string; defaultRouteName?: string }
export interface MapAssetIdentity extends RouteMapIdentity {}
export interface RouteValidationIssue { code: string; jsonPointer: string; message: string; severity: 'ERROR' | 'WARNING' }
export interface RouteValidationResult { valid: boolean; issues: RouteValidationIssue[] }
export interface RouteDraftValidationReport extends RouteValidationResult {
  normalizedExecutorJson: RouteExecutorDocument
  mapAssetId: string
  mapImageSha256: string
  checkedAt?: string
  publishable?: boolean
  mapIdentity?: RouteMapIdentity | null
}

export interface RouteDraftMetadata {
  version: number
  updatedAt: string
  updatedBy?: string | null
  publishable: boolean
  lastPublishable?: { checkedAt: string; mapAssetId: string; mapImageSha256: string }
}

export interface PersistedRouteDraftReport extends Omit<RouteDraftValidationReport, 'normalizedExecutorJson' | 'checkedAt' | 'publishable'> {
  normalizedExecutorJson: RouteExecutorDocument | null
  checkedAt: string | null
  publishable: boolean
  draft: RouteDraftMetadata | null
  fallback?: 'ROUTE_CONFIGURATION' | 'EMPTY'
}

/** 兼容地图画布的轻量状态，不属于执行 JSON。 */
export type EditorMode = 'start' | 'target' | 'yaw' | 'pan'
export interface RosMapState {
  width: number; height: number; pixels: Uint8Array | null; yamlName: string; pgmName: string
  image: string; resolution: number; origin: [number, number, number]; negate: number
}

export interface RouteMapSnapshot {
  width: number
  height: number
  resolution: number
  origin: [number, number, number]
  negate: number
  pgm_base64: string
}
