import type { SceneAssetQuery } from '@/types/sceneAsset'

export function buildSceneAssetQuery(query: SceneAssetQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value) params.set(key, value)
  })
  const value = params.toString()
  return value ? `?${value}` : ''
}

export function formatSceneFileSize(value: number) {
  if (!Number.isFinite(value) || value < 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

export function shortSceneHash(value?: string | null) {
  return value ? `${value.slice(0, 12)}…${value.slice(-6)}` : '-'
}
