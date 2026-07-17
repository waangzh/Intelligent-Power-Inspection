/** 检测项类型 */
export type {
  MapAsset,
  MapAssetFiles,
  MapAssetQuery,
  MapAssetReviewInput,
  MapAssetSource,
  MapAssetStatus,
  MapAssetUploadFiles,
  MapAssetUploadInput,
} from './mapAsset'

export type DetectionType =
  | 'PERSON'
  | 'HELMET'
  | 'OBSTACLE'
  | 'FIRE'
  | 'SWITCH'
  | 'METER'
  | 'OIL_LEAK'
  | 'FOREIGN_OBJECT'

/** 路线级检测项 */
export const ROUTE_DETECTIONS: DetectionType[] = ['PERSON', 'HELMET', 'OBSTACLE', 'FIRE']

/** 检查点级检测项 */
export const CHECKPOINT_DETECTIONS: DetectionType[] = [
  'SWITCH',
  'METER',
  'OIL_LEAK',
  'FIRE',
  'FOREIGN_OBJECT',
]

export const DETECTION_LABELS: Record<DetectionType, string> = {
  PERSON: '人员检测',
  HELMET: '安全帽检测',
  OBSTACLE: '障碍物检测',
  FIRE: '火源/烟雾检测',
  SWITCH: '开关/刀闸状态',
  METER: '表计/指示灯',
  OIL_LEAK: '漏油检测',
  FOREIGN_OBJECT: '异物检测',
}

export type TaskStatus =
  | 'CREATED'
  | 'DISPATCHED'
  | 'STARTING'
  | 'RUNNING'
  | 'PAUSING'
  | 'PAUSED'
  | 'RESUMING'
  | 'CANCELLING'
  | 'TAKEOVER_PENDING'
  | 'MANUAL_TAKEOVER'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'START_FAILED'
  | 'FAILED'
  | 'DISCONNECTED'
  | 'RECOVERING'

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  CREATED: '已创建',
  DISPATCHED: '已下发',
  STARTING: '启动中',
  RUNNING: '执行中',
  PAUSING: '暂停请求中',
  PAUSED: '已暂停',
  RESUMING: '恢复请求中',
  CANCELLING: '取消请求中',
  TAKEOVER_PENDING: '人工接管请求中',
  MANUAL_TAKEOVER: '人工接管',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  START_FAILED: '启动失败',
  FAILED: '执行失败',
  DISCONNECTED: '机器人断联',
  RECOVERING: '执行恢复中',
}

export type AlarmSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlarmWorkOrderMode = 'AUTO' | 'MANUAL'
export type WorkOrderConversionStatus = 'PROCESSING' | 'WAITING_MANUAL' | 'SUCCEEDED' | 'FAILED'

export interface AlarmWorkOrderPolicy {
  id: string
  rules: Record<AlarmSeverity, AlarmWorkOrderMode>
  updatedBy?: string
  updatedAt?: string
}

export const ALARM_SEVERITY_LABELS: Record<AlarmSeverity, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '紧急',
}

export interface LatLng {
  lat: number
  lng: number
  x?: number
  y?: number
  yaw?: number
}

export interface Site {
  id: string
  name: string
  address: string
  description: string
  center: LatLng
  lingbotMapId?: string
  /** 设备端建图是否已上传（由设备推送地图数据后标记） */
  deviceMapUploaded?: boolean
  createdAt: string
}

export interface Area {
  id: string
  siteId: string
  name: string
  polygon: LatLng[]
}

export interface DetectionItem {
  type: DetectionType
  enabled: boolean
  prompt?: string
  threshold: number
}

export interface Checkpoint {
  id: string
  routeId: string
  name: string
  seq: number
  position: LatLng
  pan: number
  tilt: number
  detections: DetectionItem[]
  dwellSeconds: number
}

export interface Route {
  id: string
  siteId: string
  name: string
  description: string
  path: LatLng[]
  routeDetections: DetectionItem[]
  checkpoints: Checkpoint[]
  mapMode: '2d' | '3d'
  /** 关联的 ROS 地图资产 ID（/api/v1/map-assets） */
  mapId?: string
  /** ROS map route executor JSON (version 2). */
  executorJson?: import('@/types/routeExecutor').RouteExecutorDocument | null
  createdAt: string
}

export interface RobotPose {
  frame?: string
  x: number
  y: number
  yaw: number
}

export interface RobotTelemetry {
  bridgeBaseUrl?: string
  bridgeReachable?: boolean
  bridgeSyncedAt?: string
  online?: boolean
  canStatus?: string
  zlacStatus?: string
  taskStatus?: string
  systemMode?: string
  mappingStatus?: 'running' | 'not_running'
  nav2Status?: 'running' | 'not_running'
  patrolState?: string
  patrolExecutorRunning?: boolean
  patrolMessage?: string
  activeRouteId?: string
  activeTargetId?: string
  lastOdomAgeSec?: number | null
  lastScanAgeSec?: number | null
  velocity?: { linear_x: number; angular_z: number }
  pose?: RobotPose
}

export interface RouteRevision {
  id: string
  routeId: string
  revisionNo: number
  contentSha256: string
  mapAssetId: string
  mapImageSha256: string
  executorJson: Record<string, unknown>
  validationReport: {
    valid: boolean
    validatedAt: string
    issues: unknown[]
    [key: string]: unknown
  }
  createdBy?: string
  createdAt: string
}

export interface Robot {
  id: string
  name: string
  model: string
  serialNo: string
  siteId?: string
  status: 'ONLINE' | 'OFFLINE' | 'BUSY'
  position?: LatLng & { x?: number; y?: number; yaw?: number }
  currentTaskId?: string
  firmware?: string
  lastOnlineAt?: string
  telemetry?: RobotTelemetry
}

export interface TaskEvent {
  id: string
  taskId: string
  type: 'DISPATCH' | 'ARRIVE' | 'INSPECT' | 'DETECT' | 'ALARM' | 'PAUSE' | 'RESUME' | 'COMPLETE' | 'VOICE'
  message: string
  checkpointName?: string
  imageUrl?: string
  createdAt: string
}

export interface DetectionTemplate {
  id: string
  name: string
  scope: 'ROUTE' | 'CHECKPOINT'
  types: DetectionType[]
  description: string
  prompts: Record<string, string>
  createdAt: string
}

export type LingBotMapStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type LingBotMapInputKind = 'video' | 'image_sequence'
export type LingBotMapOutputProfile = 'preview' | 'viewer-ready' | 'rendered-video' | 'predictions'

export interface LingBotMapArtifacts {
  pointCloudUrl?: string
  meshUrl?: string
  trajectoryUrl?: string
  previewVideoUrl?: string
  metadataUrl?: string
}

export interface LingBotMapJob {
  id: string
  siteId: string
  siteName: string
  name: string
  status: LingBotMapStatus
  progress: number
  pointCount: number
  videoCount: number
  inputKind?: LingBotMapInputKind
  videoUrl?: string
  imageFolderUrl?: string
  fps?: number
  stride?: number
  keyframeInterval?: number
  windowSize?: number
  outputProfile?: LingBotMapOutputProfile
  maskSky?: boolean
  externalJobId?: string
  frameCount?: number
  mapId?: string
  artifacts?: LingBotMapArtifacts
  errorMessage?: string
  createdAt: string
  completedAt?: string
}

export interface LingBotVideoUploadResponse {
  videoUrl: string
  filename: string
  size: number
}

export interface InspectionTask {
  id: string
  name: string
  routeId: string
  robotId: string
  status: TaskStatus
  progress: number
  currentCheckpointSeq: number
  startedAt?: string
  completedAt?: string
  createdAt: string
  routeRevisionId?: string
  executionId?: string
  routeContentSha256?: string
  mapImageSha256?: string
}

export interface TaskExecution {
  taskId: string
  executionId: string
  routeRevisionId: string
  robotId: string
  deploymentId?: string | null
  executorRouteId?: string | null
  routeContentSha256: string
  mapImageSha256: string
  status: TaskStatus
  currentTargetId?: string | null
  progress: number
  lastRobotSequence: number
  lastEventAt?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  manualReconciliationRequired: boolean
  latestControl?: TaskExecutionControl | null
  createdAt: string
  updatedAt: string
}

export interface TaskExecutionControl {
  action: 'PAUSE' | 'RESUME' | 'TAKEOVER' | 'CANCEL'
  requestId: string
  status: 'PENDING_SEND' | 'SENDING' | 'QUEUED' | 'ACKED' | 'RECONCILING' | 'CONFIRMED' | 'FAILED'
  commandId?: string | null
  takeoverReason?: string | null
  requestedBy?: string | null
  requestedAt?: string | null
  ackedAt?: string | null
  confirmedAt?: string | null
  recoveryAction?: string | null
  resultCode?: string | null
  resultMessage?: string | null
}

export interface TaskStartEligibility extends TaskExecution {
  eligible: boolean
  ineligibleReason?: string | null
}

export interface Alarm {
  id: string
  taskId: string
  routeName: string
  checkpointName?: string
  type: DetectionType
  severity: AlarmSeverity
  message: string
  imageUrl?: string
  acknowledged: boolean
  workOrderModeApplied?: AlarmWorkOrderMode
  workOrderConversionStatus?: WorkOrderConversionStatus
  workOrderConversionSource?: 'AUTO' | 'MANUAL' | 'AGENT'
  workOrderConversionError?: string
  workOrderId?: string
  convertedAt?: string
  createdAt: string
}

export interface InspectionRecord {
  id: string
  taskId: string
  taskName: string
  routeName: string
  robotName: string
  alarmCount: number
  checkpointCount: number
  duration: string
  summary: string
  completedAt: string
}

export interface LocateAnythingFinding {
  type: DetectionType
  prompt?: string
  score: number
  bbox: number[]
  label: string
  imageUrl?: string
  rawResult?: Record<string, unknown>
}

export interface ManualDetectionResponse {
  requestId: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  inputImageUrl: string
  resultImageUrl?: string
  findings: LocateAnythingFinding[]
  warnings: string[]
  errorMessage?: string
  createdAt?: string
  startedAt?: string
  completedAt?: string
}
