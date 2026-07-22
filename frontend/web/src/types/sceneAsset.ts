export type SceneAssetStatus = 'PROCESSING' | 'PENDING_REVIEW' | 'AVAILABLE' | 'REJECTED' | 'FAILED' | 'DELETED'
export type SceneAssetKind = 'POINT_CLOUD' | 'MESH'
export type SceneAssetFormat = 'PLY' | 'OBJ' | 'GLB'

export interface SceneAsset {
  id: string
  siteId: string
  status: SceneAssetStatus
  source: 'ROBOT'
  sourceRobotId?: string | null
  sourceBridgeRobotId?: string | null
  assetKind: SceneAssetKind
  format: SceneAssetFormat
  originalName: string
  contentType?: string | null
  fileSize: number
  modelSha256: string
  metadataSha256: string
  sourceCaptureSessionId?: string | null
  sourceReconstructSessionId: string
  reconstructProfile?: string | null
  coordinateSystem: string
  unit: string
  pointCount: number
  reportedPointCount?: number | null
  pointCountMismatch: boolean
  capturedAt?: string | null
  reconstructedAt: string
  createdAt: string
  updatedAt: string
  reviewedBy?: string | null
  reviewedAt?: string | null
  reviewComment?: string | null
  filesReady: boolean
  previewReady: boolean
  sceneFrame?: string | null
  referenceFrame?: string | null
  sceneToReferenceTransform?: number[] | null
}

export interface SceneAssetQuery {
  source?: 'ROBOT'
  status?: SceneAssetStatus
  siteId?: string
  robotId?: string
  assetKind?: SceneAssetKind
}

export interface SceneAssetReviewInput {
  action: 'APPROVE' | 'REJECT'
  comment?: string
}
