/** ROS 地图资产元数据（对应 POST/GET /api/v1/map-assets） */
export interface MapAsset {
  id: string
  siteId: string
  status: MapAssetStatus
  source: MapAssetSource
  sourceRobotId?: string | null
  sourceBridgeRobotId?: string | null
  uploadIdempotencyKey?: string | null
  capturedAt?: string | null
  reviewedBy?: string | null
  reviewedAt?: string | null
  reviewComment?: string | null
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
  contentIdentitySha256?: string
  occupiedThresh?: string
  freeThresh?: string
  mode?: 'trinary' | 'scale' | 'raw'
  /** 管理端读取时由后端实时检查 YAML/PGM 是否仍完整存在。 */
  filesReady?: boolean
  createdAt: string
  updatedAt: string
}

export type MapAssetStatus = 'PENDING_REVIEW' | 'AVAILABLE' | 'REJECTED'
export type MapAssetSource = 'USER' | 'ROBOT'

export interface MapAssetQuery {
  source?: MapAssetSource
  status?: MapAssetStatus
  siteId?: string
}

export interface MapAssetReviewInput {
  action: 'APPROVE' | 'REJECT'
  comment?: string
}

export interface MapAssetUploadInput {
  yamlText: string
  yamlName: string
  pgmBuffer: ArrayBuffer
  pgmName: string
}

export interface MapAssetFiles {
  yamlText: string
  pgmBuffer: ArrayBuffer
  yamlName: string
  pgmName: string
}

/** 路线编辑器上传地图时选择的一对源文件。 */
export interface MapAssetUploadFiles {
  yaml: File
  pgm: File
}
