const GNSS_FIX_TYPE_LABELS = {
  NO_FIX: '无定位',
  SINGLE_POINT: '单点定位',
  DGPS: '差分 GPS',
  RTK_FIXED: 'RTK 固定解',
  RTK_FLOAT: 'RTK 浮点解',
  UNKNOWN: '未知',
}

function isValidGnssCoordinate(latitude, longitude) {
  return (
    Number.isFinite(latitude)
    && Number.isFinite(longitude)
    && Math.abs(latitude) <= 90
    && Math.abs(longitude) <= 180
    && !(latitude === 0 && longitude === 0)
  )
}

function gnssFixLatLng(fix) {
  if (!fix || !isValidGnssCoordinate(fix.latitude, fix.longitude)) return null
  return { lat: fix.latitude, lng: fix.longitude }
}

function locationLatLng(location) {
  if (!location?.locationAvailable) return null
  return gnssFixLatLng(location.gnssFix)
}

function trackPointLatLng(point) {
  if (!point || !isValidGnssCoordinate(point.latitude, point.longitude)) return null
  return { lat: point.latitude, lng: point.longitude }
}

function locationModeLabel(location) {
  if (!location?.locationAvailable) return '无 GPS 位置'
  return location.realtime ? '实时 GPS' : '最后 GPS'
}

function gnssFixTypeLabel(fixType) {
  return GNSS_FIX_TYPE_LABELS[fixType] || GNSS_FIX_TYPE_LABELS.UNKNOWN
}

/** 默认查询近 1 小时轨迹（与后端 DEFAULT_TRACK_RANGE 一致） */
function defaultTrackQuery(options = {}) {
  const end = options.end ? new Date(options.end) : new Date()
  const start = options.start
    ? new Date(options.start)
    : new Date(end.getTime() - 60 * 60 * 1000)
  const query = {
    start: start.toISOString(),
    end: end.toISOString(),
    limit: options.limit || 500,
  }
  if (options.executionId) query.executionId = options.executionId
  return query
}

function isGpsApiMissingError(err) {
  if (!err) return false
  if (err.statusCode === 404) return true
  const msg = String(err.message || err || '')
  return /404|not found|不存在|未找到/i.test(msg)
}

const GPS_API_FLAG_KEY = 'pi_gps_api_unavailable'

function readGpsApiFlags() {
  try {
    return wx.getStorageSync(GPS_API_FLAG_KEY) || {}
  } catch {
    return {}
  }
}

function isGpsApiCachedUnavailable(baseUrl) {
  if (!baseUrl) return false
  return readGpsApiFlags()[baseUrl] === true
}

function markGpsApiUnavailable(baseUrl) {
  if (!baseUrl) return
  const flags = readGpsApiFlags()
  flags[baseUrl] = true
  try {
    wx.setStorageSync(GPS_API_FLAG_KEY, flags)
  } catch {
    // ignore storage failures
  }
}

function clearGpsApiUnavailable(baseUrl) {
  if (!baseUrl) return
  const flags = readGpsApiFlags()
  delete flags[baseUrl]
  try {
    wx.setStorageSync(GPS_API_FLAG_KEY, flags)
  } catch {
    // ignore storage failures
  }
}

function buildLocationSummary(location, options = {}) {
  if (options.gpsApiUnavailable) {
    return {
      modeLabel: 'GPS 接口未部署',
      fixLabel: '-',
      meta: options.hasRoute
        ? '已显示参考巡检路线，更新后端 JAR 后可启用 GPS'
        : (options.legacyPos ? '已显示档案位置' : '请更新远程后端 JAR 后启用 GPS'),
    }
  }
  if (options.legacyPos && !location?.locationAvailable) {
    return {
      modeLabel: '档案位置',
      fixLabel: '-',
      meta: '等待 Bridge 上报 GNSS 后将切换为实时 GPS',
    }
  }
  if (!location?.locationAvailable || !location.gnssFix) {
    return { modeLabel: locationModeLabel(location), fixLabel: '-', meta: '' }
  }
  const fix = location.gnssFix
  const fixLabel = gnssFixTypeLabel(fix.fixType)
  const parts = []
  if (fix.satellites != null) parts.push(`卫星 ${fix.satellites}`)
  if (fix.hdop != null) parts.push(`HDOP ${fix.hdop}`)
  return {
    modeLabel: locationModeLabel(location),
    fixLabel,
    meta: parts.join(' · '),
  }
}

module.exports = {
  GNSS_FIX_TYPE_LABELS,
  isValidGnssCoordinate,
  gnssFixLatLng,
  locationLatLng,
  trackPointLatLng,
  locationModeLabel,
  gnssFixTypeLabel,
  defaultTrackQuery,
  isGpsApiMissingError,
  isGpsApiCachedUnavailable,
  markGpsApiUnavailable,
  clearGpsApiUnavailable,
  buildLocationSummary,
}
