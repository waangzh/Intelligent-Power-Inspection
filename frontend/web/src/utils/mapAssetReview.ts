import type { MapAsset, MapAssetQuery } from '@/types/mapAsset'

export function buildMapAssetQuery(query: MapAssetQuery = {}) {
  const params = new URLSearchParams()
  if (query.source) params.set('source', query.source)
  if (query.status) params.set('status', query.status)
  if (query.siteId) params.set('siteId', query.siteId)
  const value = params.toString()
  return value ? `?${value}` : ''
}

/** 前端再做一次可用性收敛，避免非 AVAILABLE 资产进入路线地图选择器。 */
export function availableMapAssets(assets: MapAsset[], siteId?: string) {
  return assets.filter(asset => asset.status === 'AVAILABLE' && (!siteId || asset.siteId === siteId))
}

export function shortMapHash(value?: string | null) {
  return value && value.length > 12 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value || '-'
}
