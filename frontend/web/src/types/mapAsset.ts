/** ROS 地图资产元数据（对应 POST/GET /api/v1/map-assets） */
export interface MapAsset {
  id: string
  siteId: string
  status: 'AVAILABLE' | string
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
