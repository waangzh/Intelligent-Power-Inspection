import { resourcesApi } from '@/api/resources'
import type { MapAssetFiles, MapAssetUploadInput } from '@/types/mapAsset'

export async function fetchMapAssetFiles(mapId: string): Promise<MapAssetFiles> {
  const [meta, yamlBlob, pgmBlob] = await Promise.all([
    resourcesApi.getMapAsset(mapId),
    resourcesApi.getMapAssetYaml(mapId),
    resourcesApi.getMapAssetPgm(mapId),
  ])
  return {
    yamlText: await yamlBlob.text(),
    pgmBuffer: await pgmBlob.arrayBuffer(),
    yamlName: meta.yamlName,
    pgmName: meta.pgmName,
  }
}

export async function uploadMapAsset(siteId: string, input: MapAssetUploadInput) {
  return resourcesApi.uploadMapAsset(siteId, input)
}
