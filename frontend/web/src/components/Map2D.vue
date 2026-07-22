<template>
  <div class="map-shell">
    <div ref="mapEl" class="map-container" />
    <div v-if="mapLoadError" class="map-error">
      <strong>地图底图加载失败</strong>
      <span>请检查网络或配置 VITE_MAP_TILE_URL。</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import type { Area, LatLng, Route } from '@/types'
import type { RobotLocation, RobotTrackPoint } from '@/types/robotLocation'
import {
  GNSS_FIX_COLORS,
  GNSS_FIX_TYPE_LABELS,
  formatGnssObservedAt,
  locationLatLng,
  locationModeLabel,
  trackPointLatLng,
} from '@/utils/robotLocation'

const props = defineProps<{
  center: LatLng
  fallbackCenter?: LatLng
  zoom?: number
  route?: Route | null
  areas?: Area[]
  /** @deprecated Prefer robotLocation for GPS-backed positions */
  robotPosition?: LatLng | null
  robotLocation?: RobotLocation | null
  robotLabel?: string
  trackPoints?: RobotTrackPoint[]
  showTrack?: boolean
  followRobot?: boolean
  editable?: boolean
}>()

const emit = defineEmits<{
  pathChange: [LatLng[]]
  checkpointAdd: [LatLng]
  checkpointSelect: [string]
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const mapLoadError = ref(false)
let map: L.Map | null = null
let pathLayer: L.Polyline | null = null
let markersLayer: L.LayerGroup | null = null
let areasLayer: L.LayerGroup | null = null
let robotMarker: L.Marker | null = null
let trackLayer: L.Polyline | null = null
let trackStartMarker: L.CircleMarker | null = null
let trackEndMarker: L.CircleMarker | null = null
let resizeObserver: ResizeObserver | null = null
let drawing = false
let currentPath: LatLng[] = []
let lastFollowLatLng: L.LatLng | null = null

const tileUrl = import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const tileAttribution = import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; OpenStreetMap contributors'

const defaultIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
})

type MapPoint = LatLng & { x?: number; y?: number }

const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => HTML_ESCAPES[character] ?? character)
}

function hasValidGeoCoordinate(latlng: LatLng): boolean {
  return (
    Number.isFinite(latlng.lat)
    && Number.isFinite(latlng.lng)
    && Math.abs(latlng.lat) <= 90
    && Math.abs(latlng.lng) <= 180
  )
}

function isRosLocalCoordinate(latlng: LatLng): boolean {
  const point = latlng as MapPoint
  return (
    Number.isFinite(point.x)
    && Number.isFinite(point.y)
    && Math.abs(point.lat - point.y!) < 1e-9
    && Math.abs(point.lng - point.x!) < 1e-9
  )
}

function canDrawOnGeoMap(latlng: LatLng): boolean {
  return hasValidGeoCoordinate(latlng) && !isRosLocalCoordinate(latlng)
}

function safeCenter(latlng: LatLng): LatLng {
  if (canDrawOnGeoMap(latlng)) return latlng
  if (props.fallbackCenter && canDrawOnGeoMap(props.fallbackCenter)) return props.fallbackCenter
  return latlng
}

function toLeaflet(latlng: LatLng): L.LatLngExpression {
  return [latlng.lat, latlng.lng]
}

function resolveRobotLatLng(): LatLng | null {
  if (props.robotLocation !== undefined) return locationLatLng(props.robotLocation)
  if (props.robotPosition && canDrawOnGeoMap(props.robotPosition)) return props.robotPosition
  return null
}

function robotMarkerHtml(): string {
  const fix = props.robotLocation?.gnssFix
  const fixType = fix?.fixType ?? 'NO_FIX'
  const stale = fix?.stale || props.robotLocation?.realtime === false
  const offline = props.robotLocation?.online === false
  const color = offline || stale ? '#64748b' : GNSS_FIX_COLORS[fixType]
  const opacity = offline || stale ? 0.55 : 1
  const ring = stale ? 'dashed' : 'solid'
  return `
    <div class="robot-gnss-marker" style="opacity:${opacity}">
      <span class="robot-gnss-ring" style="border-color:${color}; border-style:${ring}"></span>
      <span class="robot-gnss-dot" style="background:${color}">🤖</span>
    </div>
  `
}

function robotPopupHtml(): string {
  const label = escapeHtml(props.robotLabel?.trim() || '机器人')
  const location = props.robotLocation
  const fix = location?.gnssFix
  const latlng = resolveRobotLatLng()
  if (!latlng) return `<b>${label}</b><br/>无可用 GPS 位置`

  const lines = [
    `<b>${label}</b>`,
    `${locationModeLabel(location)}${location?.online === false ? ' · 离线' : ''}`,
    `经纬度：${latlng.lat.toFixed(7)}, ${latlng.lng.toFixed(7)}`,
  ]
  if (fix) {
    lines.push(`RTK：${GNSS_FIX_TYPE_LABELS[fix.fixType]}`)
    if (fix.satellites != null) lines.push(`卫星数：${fix.satellites}`)
    if (fix.hdop != null) lines.push(`HDOP：${fix.hdop}`)
    lines.push(`定位时间：${formatGnssObservedAt(fix.observedAt)}`)
    if (fix.ageSec != null) lines.push(`数据延迟：${fix.ageSec.toFixed(1)}s`)
  }
  if (location?.state) lines.push(`巡逻状态：${escapeHtml(location.state)}`)
  if (location?.executionId) lines.push(`执行 ID：${escapeHtml(location.executionId)}`)
  return lines.join('<br/>')
}

function refreshMapSize() {
  void nextTick(() => {
    map?.invalidateSize()
  })
}

function renderRoute() {
  if (!map || !markersLayer) return
  markersLayer.clearLayers()
  if (pathLayer) {
    map.removeLayer(pathLayer)
    pathLayer = null
  }

  const route = props.route
  if (!route) return

  const path = route.path.filter(canDrawOnGeoMap)
  if (path.length > 0) {
    pathLayer = L.polyline(path.map(toLeaflet), {
      color: '#1a5fb4',
      weight: 4,
      opacity: 0.85,
    }).addTo(map)
  }

  route.checkpoints.filter((cp) => canDrawOnGeoMap(cp.position)).forEach((cp) => {
    const marker = L.marker(toLeaflet(cp.position), { icon: defaultIcon })
    marker.bindPopup(`<b>${cp.seq}. ${escapeHtml(cp.name)}</b>`)
    marker.on('click', () => emit('checkpointSelect', cp.id))
    markersLayer!.addLayer(marker)

    L.circleMarker(toLeaflet(cp.position), {
      radius: 8,
      color: '#fff',
      weight: 2,
      fillColor: '#e6a23c',
      fillOpacity: 1,
    })
      .bindTooltip(`${cp.seq}`)
      .addTo(markersLayer!)
  })
}

function renderAreas() {
  if (!map || !areasLayer) return
  areasLayer.clearLayers()
  props.areas?.forEach((area) => {
    const polygon = area.polygon.filter(canDrawOnGeoMap)
    if (polygon.length >= 3) {
      L.polygon(polygon.map(toLeaflet), {
        color: '#67c23a',
        fillColor: '#67c23a',
        fillOpacity: 0.12,
        weight: 2,
      })
        .bindPopup(escapeHtml(area.name))
        .addTo(areasLayer!)
    }
  })
}

function clearTrackLayers() {
  if (!map) return
  if (trackLayer) {
    map.removeLayer(trackLayer)
    trackLayer = null
  }
  if (trackStartMarker) {
    map.removeLayer(trackStartMarker)
    trackStartMarker = null
  }
  if (trackEndMarker) {
    map.removeLayer(trackEndMarker)
    trackEndMarker = null
  }
}

function renderTrack() {
  if (!map) return
  clearTrackLayers()
  if (!props.showTrack) return

  const latlngs = (props.trackPoints ?? [])
    .map(trackPointLatLng)
    .filter((item): item is LatLng => item !== null)
    .map(toLeaflet)

  if (latlngs.length === 0) return

  if (latlngs.length >= 2) {
    trackLayer = L.polyline(latlngs, {
      color: '#0ea5e9',
      weight: 4,
      opacity: 0.85,
    }).addTo(map)
  }

  const start = latlngs[0] as L.LatLngTuple
  trackStartMarker = L.circleMarker(start, {
    radius: 7,
    color: '#fff',
    weight: 2,
    fillColor: '#16a34a',
    fillOpacity: 1,
  })
    .bindTooltip('起点')
    .addTo(map)
  if (latlngs.length >= 2) {
    const end = latlngs[latlngs.length - 1] as L.LatLngTuple
    trackEndMarker = L.circleMarker(end, {
      radius: 7,
      color: '#fff',
      weight: 2,
      fillColor: '#dc2626',
      fillOpacity: 1,
    })
      .bindTooltip('终点')
      .addTo(map)
  }
}

function maybeFollowRobot(latlng: LatLng) {
  if (!map || !props.followRobot) return
  const leafletLatLng = L.latLng(latlng.lat, latlng.lng)
  if (lastFollowLatLng && lastFollowLatLng.distanceTo(leafletLatLng) < 0.5) return
  lastFollowLatLng = leafletLatLng
  map.panTo(leafletLatLng, { animate: true })
}

function renderRobot() {
  if (!map) return

  const latlng = resolveRobotLatLng()
  if (!latlng) {
    if (robotMarker) {
      map.removeLayer(robotMarker)
      robotMarker = null
    }
    return
  }

  const icon = L.divIcon({
    className: 'robot-marker',
    html: robotMarkerHtml(),
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  })

  if (robotMarker) {
    robotMarker.setLatLng(toLeaflet(latlng))
    robotMarker.setIcon(icon)
    robotMarker.setPopupContent(robotPopupHtml())
  } else {
    robotMarker = L.marker(toLeaflet(latlng), { icon })
      .bindPopup(robotPopupHtml())
      .addTo(map)
  }

  maybeFollowRobot(latlng)
}

function onMapClick(e: L.LeafletMouseEvent) {
  if (!props.editable) return
  const point: LatLng = { lat: e.latlng.lat, lng: e.latlng.lng }

  if (drawing) {
    currentPath.push(point)
    emit('pathChange', [...currentPath])
  } else {
    emit('checkpointAdd', point)
  }
}

function startDrawPath(existing: LatLng[]) {
  drawing = true
  currentPath = [...existing]
}

function stopDrawPath() {
  drawing = false
}

function clearPath() {
  currentPath = []
  emit('pathChange', [])
}

function fitToTrack() {
  if (!map) return
  const latlngs = (props.trackPoints ?? [])
    .map(trackPointLatLng)
    .filter((item): item is LatLng => item !== null)
    .map((item) => L.latLng(item.lat, item.lng))
  if (latlngs.length === 0) return
  if (latlngs.length === 1) {
    map.setView(latlngs[0]!, Math.max(map.getZoom(), 17), { animate: true })
    return
  }
  map.fitBounds(L.latLngBounds(latlngs), { padding: [24, 24] })
}

function fitToRobot() {
  const latlng = resolveRobotLatLng()
  if (!map || !latlng) return
  map.setView(toLeaflet(latlng), Math.max(map.getZoom(), 17), { animate: true })
}

defineExpose({ startDrawPath, stopDrawPath, clearPath, fitToTrack, fitToRobot })

onMounted(() => {
  if (!mapEl.value) return

  map = L.map(mapEl.value).setView(toLeaflet(safeCenter(props.center)), props.zoom ?? 17)

  L.tileLayer(tileUrl, {
    attribution: tileAttribution,
    maxZoom: 20,
  })
    .on('tileload', () => {
      mapLoadError.value = false
    })
    .on('tileerror', () => {
      mapLoadError.value = true
    })
    .addTo(map)

  areasLayer = L.layerGroup().addTo(map)
  markersLayer = L.layerGroup().addTo(map)

  resizeObserver = new ResizeObserver(refreshMapSize)
  resizeObserver.observe(mapEl.value)
  refreshMapSize()

  map.on('click', onMapClick)
  renderRoute()
  renderAreas()
  renderTrack()
  renderRobot()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  map?.off('click', onMapClick)
  map?.remove()
  map = null
})

watch(
  () => props.route,
  () => {
    if (props.route) currentPath = [...props.route.path]
    renderRoute()
  },
  { deep: true },
)

watch(() => props.areas, renderAreas, { deep: true })
watch(
  () => [props.robotPosition, props.robotLocation, props.robotLabel],
  renderRobot,
  { deep: true },
)
watch(
  () => [props.trackPoints, props.showTrack],
  renderTrack,
  { deep: true },
)
watch(
  () => props.center,
  (c) => {
    map?.setView(toLeaflet(safeCenter(c)), map.getZoom())
    refreshMapSize()
  },
  { deep: true },
)
</script>

<style>
.map-shell {
  position: relative;
  height: 100%;
  min-height: 100%;
}

.map-error {
  position: absolute;
  inset: auto 12px 12px 12px;
  z-index: 500;
  display: grid;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid #f3d19e;
  background: rgba(253, 246, 236, 0.95);
  color: #8a4b00;
  font-size: 12px;
  pointer-events: none;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
}

.map-error strong {
  font-size: 13px;
}

.robot-marker {
  background: transparent;
  border: none;
}

.robot-gnss-marker {
  position: relative;
  width: 32px;
  height: 32px;
}

.robot-gnss-ring {
  position: absolute;
  inset: 0;
  border: 2px solid #16a34a;
  border-radius: 50%;
}

.robot-gnss-dot {
  position: absolute;
  inset: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.35));
}
</style>
