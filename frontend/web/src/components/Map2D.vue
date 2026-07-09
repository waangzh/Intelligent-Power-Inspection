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

const props = defineProps<{
  center: LatLng
  fallbackCenter?: LatLng
  zoom?: number
  route?: Route | null
  areas?: Area[]
  robotPosition?: LatLng | null
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
let resizeObserver: ResizeObserver | null = null
let drawing = false
let currentPath: LatLng[] = []

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

function hasValidGeoCoordinate(latlng: LatLng): boolean {
  return (
    Number.isFinite(latlng.lat) &&
    Number.isFinite(latlng.lng) &&
    Math.abs(latlng.lat) <= 90 &&
    Math.abs(latlng.lng) <= 180
  )
}

function isRosLocalCoordinate(latlng: LatLng): boolean {
  const point = latlng as MapPoint
  return (
    Number.isFinite(point.x) &&
    Number.isFinite(point.y) &&
    Math.abs(point.lat - point.y!) < 1e-9 &&
    Math.abs(point.lng - point.x!) < 1e-9
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
    marker.bindPopup(`<b>${cp.seq}. ${cp.name}</b>`)
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
        .bindPopup(area.name)
        .addTo(areasLayer!)
    }
  })
}

function renderRobot() {
  if (!map) return
  if (robotMarker) {
    map.removeLayer(robotMarker)
    robotMarker = null
  }
  if (props.robotPosition) {
    if (!canDrawOnGeoMap(props.robotPosition)) return
    robotMarker = L.marker(toLeaflet(props.robotPosition), {
      icon: L.divIcon({
        className: 'robot-marker',
        html: '<div class="robot-dot">🤖</div>',
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      }),
    }).addTo(map)
  }
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

defineExpose({ startDrawPath, stopDrawPath, clearPath })

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
watch(() => props.robotPosition, renderRobot, { deep: true })
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

.robot-dot {
  font-size: 22px;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.4));
}
</style>
