import type { LatLng } from '@/types'

export const DEFAULT_GEO_CENTER: Readonly<LatLng> = Object.freeze({
  lat: 30.2741,
  lng: 120.1551,
})

export function isValidGeoCoordinate(point?: Partial<LatLng> | null): boolean {
  return Boolean(
    point
    && Number.isFinite(point.lat)
    && Number.isFinite(point.lng)
    && Math.abs(point.lat!) <= 90
    && Math.abs(point.lng!) <= 180,
  )
}

export function isSuspectedCoordinateSwap(point?: Partial<LatLng> | null): boolean {
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lng)) return false
  return Math.abs(point.lat!) > 90
    && Math.abs(point.lat!) <= 180
    && Math.abs(point.lng!) <= 90
}

export function defaultGeoCenter(): LatLng {
  return { ...DEFAULT_GEO_CENTER }
}
