/** 微信小程序 map 组件要求 lat ∈ [-90, 90]、lng ∈ [-180, 180] */
const DEFAULT_CENTER = { lat: 30.2741, lng: 120.1551 }

function readLat(point) {
  if (!point) return NaN
  return Number(point.lat ?? point.latitude)
}

function readLng(point) {
  if (!point) return NaN
  return Number(point.lng ?? point.longitude ?? point.lon)
}

function isValidLat(lat) {
  return Number.isFinite(lat) && lat >= -90 && lat <= 90
}

function isValidLng(lng) {
  return Number.isFinite(lng) && lng >= -180 && lng <= 180
}

function isValidGeoPoint(point) {
  return isValidLat(readLat(point)) && isValidLng(readLng(point))
}

function normalizeGeoPoint(point, fallback = DEFAULT_CENTER) {
  if (!isValidGeoPoint(point)) {
    return { lat: fallback.lat, lng: fallback.lng }
  }
  return { lat: readLat(point), lng: readLng(point) }
}

function toMapPoint(point) {
  if (!isValidGeoPoint(point)) return null
  return { latitude: readLat(point), longitude: readLng(point) }
}

function cloneCenter(point) {
  const normalized = normalizeGeoPoint(point)
  return { lat: normalized.lat, lng: normalized.lng }
}

module.exports = {
  DEFAULT_CENTER,
  isValidGeoPoint,
  normalizeGeoPoint,
  toMapPoint,
  cloneCenter,
}
