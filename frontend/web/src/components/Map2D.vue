<template>
  <div ref="mapEl" class="map-container" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import type { Area, LatLng, Route } from '@/types'

const props = defineProps<{
  center: LatLng
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
let map: L.Map | null = null
let pathLayer: L.Polyline | null = null
let markersLayer: L.LayerGroup | null = null
let areasLayer: L.LayerGroup | null = null
let robotMarker: L.Marker | null = null
let drawing = false
let currentPath: LatLng[] = []

const defaultIcon = L.icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
})

function toLeaflet(latlng: LatLng): L.LatLngExpression {
  return [latlng.lat, latlng.lng]
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

  if (route.path.length > 0) {
    pathLayer = L.polyline(route.path.map(toLeaflet), {
      color: '#1a5fb4',
      weight: 4,
      opacity: 0.85,
    }).addTo(map)
  }

  route.checkpoints.forEach((cp) => {
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
    if (area.polygon.length >= 3) {
      L.polygon(area.polygon.map(toLeaflet), {
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

  map = L.map(mapEl.value).setView(toLeaflet(props.center), props.zoom ?? 17)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap',
    maxZoom: 20,
  }).addTo(map)

  areasLayer = L.layerGroup().addTo(map)
  markersLayer = L.layerGroup().addTo(map)

  map.on('click', onMapClick)
  renderRoute()
  renderAreas()
  renderRobot()
})

onUnmounted(() => {
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
    map?.setView(toLeaflet(c), map.getZoom())
  },
  { deep: true },
)
</script>

<style>
.robot-marker {
  background: transparent;
  border: none;
}

.robot-dot {
  font-size: 22px;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.4));
}
</style>
