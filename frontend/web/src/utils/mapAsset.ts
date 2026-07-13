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
  const form = new FormData()
  form.append('siteId', siteId)
  form.append('yaml', new File([input.yamlText], input.yamlName, { type: 'application/yaml' }))
  form.append('pgm', new File([input.pgmBuffer], input.pgmName, { type: 'image/x-portable-graymap' }))
  return resourcesApi.uploadMapAsset(form)
}
