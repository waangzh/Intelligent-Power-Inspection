import type { GnssFix, GnssFixType, RobotLocation, RobotTrackPoint } from '@/types/robotLocation'
import type { LatLng } from '@/types'

export const GNSS_FIX_TYPE_LABELS: Record<GnssFixType, string> = {
  NO_FIX: '无定位',
  SINGLE_POINT: '单点定位',
  DGPS: '差分 GPS',
  RTK_FIXED: 'RTK 固定解',
  RTK_FLOAT: 'RTK 浮点解',
  UNKNOWN: '未知',
}

export const GNSS_FIX_COLORS: Record<GnssFixType, string> = {
  RTK_FIXED: '#16a34a',
  RTK_FLOAT: '#ca8a04',
  DGPS: '#2563eb',
  SINGLE_POINT: '#ea580c',
  NO_FIX: '#94a3b8',
  UNKNOWN: '#94a3b8',
}

export function isValidGnssCoordinate(latitude?: number | null, longitude?: number | null): boolean {
  return (
    Number.isFinite(latitude)
    && Number.isFinite(longitude)
    && Math.abs(latitude!) <= 90
    && Math.abs(longitude!) <= 180
    && !(latitude === 0 && longitude === 0)
  )
}

export function gnssFixLatLng(fix?: GnssFix | null): LatLng | null {
  if (!fix || !isValidGnssCoordinate(fix.latitude, fix.longitude)) return null
  return { lat: fix.latitude!, lng: fix.longitude! }
}

export function locationLatLng(location?: RobotLocation | null): LatLng | null {
  if (!location?.locationAvailable) return null
  return gnssFixLatLng(location.gnssFix)
}

export function trackPointLatLng(point: RobotTrackPoint): LatLng | null {
  if (!isValidGnssCoordinate(point.latitude, point.longitude)) return null
  return { lat: point.latitude, lng: point.longitude }
}

export function locationModeLabel(location?: RobotLocation | null): string {
  if (!location?.locationAvailable) return '无位置'
  return location.realtime ? '实时位置' : '最后位置'
}

export function formatGnssObservedAt(value?: string | null): string {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
