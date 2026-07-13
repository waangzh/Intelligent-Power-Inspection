/** 检测项类型 */
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
  | 'RUNNING'
  | 'PAUSED'
  | 'MANUAL_TAKEOVER'
  | 'COMPLETED'
  | 'CANCELLED'

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  CREATED: '已创建',
  DISPATCHED: '已下发',
  RUNNING: '执行中',
  PAUSED: '已暂停',
  MANUAL_TAKEOVER: '人工接管',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
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
}

export interface Site {
  id: string
  name: string
  address: string
  description: string
  center: LatLng
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
  mapMode: '2d' | '3d' | 'ros2d'
  /** Persisted ROS map asset referenced by this route. */
  mapId?: string | null
  /** ROS map route executor JSON (version 2). */
  executorJson?: import('@/types/routeExecutor').RouteExecutorDocument | null
  createdAt: string
}

export interface MapAsset {
  id: string
  siteId: string
  status: 'AVAILABLE'
  yamlName: string
  pgmName: string
  image: string
  resolution: number
  origin: [number, number, number]
  negate: number
  width: number
  height: number
  yamlSize: number
  pgmSize: number
  yamlSha256: string
  pgmSha256: string
  createdAt: string
  updatedAt: string
}

export interface MapAssetFiles {
  yaml: File
  pgm: File
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
  status: 'ONLINE' | 'OFFLINE' | 'BUSY' | 'CHARGING'
  battery: number
  position?: LatLng
  currentTaskId?: string
  firmware?: string
  lastOnlineAt?: string
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
