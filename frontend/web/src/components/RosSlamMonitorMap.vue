<template>
  <div ref="wrapRef" class="ros-monitor-map">
    <canvas ref="canvasRef" />
    <div v-if="show3dOverlay" class="overlay-3d">
      <Map3D :route="route" :robot-position="robotLatLng" />
    </div>
    <div v-if="mapLoading" class="map-empty">
      <span>正在加载地图…</span>
    </div>
    <div v-else-if="!mapReady" class="map-empty">
      <span>暂无 2D 建图数据</span>
      <small>请先在「巡检规划」中加载 YAML/PGM 地图、标注路线并保存到平台</small>
    </div>
    <div v-if="mapReady" class="map-legend">
      <span class="legend-item"><i class="dot start" />起点</span>
      <span class="legend-item"><i class="dot target" />巡检点</span>
      <span class="legend-item"><i class="dot robot" />机器人</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import Map3D from '@/components/Map3D.vue'
import type { LatLng, Route } from '@/types'
import type { RouteExecutorDocument, RosMapState } from '@/types/routeExecutor'
import {
  createDefaultMapState,
  decodeMapSnapshot,
  mapToPixel,
  parsePgm,
  parseYaml,
  rebuildMapBitmap,
  rosCoordFromLatLng,
} from '@/utils/rosMap'
import { fetchMapAssetFiles } from '@/utils/mapAsset'

const props = defineProps<{
  route?: Route | null
  robotPosition?: LatLng | null
  height?: number
}>()

const wrapRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const map = reactive<RosMapState>(createDefaultMapState())
const view = reactive({ scale: 1, offsetX: 0, offsetY: 0 })
const mapBitmapCanvas = document.createElement('canvas')
let mapBitmapCtx = mapBitmapCanvas.getContext('2d')
let resizeObserver: ResizeObserver | null = null

const mapReady = computed(() => Boolean(map.width && map.height && map.pixels))
const show3dOverlay = computed(() => mapReady.value && (props.route?.checkpoints.length ?? 0) > 0)
const mapLoading = ref(false)

const robotLatLng = computed(() => props.robotPosition ?? null)

const executorDoc = computed(() => props.route?.executorJson ?? null)

function fitToScreen() {
  const wrap = wrapRef.value
  if (!wrap || !map.width || !map.height) return
  const rect = wrap.getBoundingClientRect()
  const padding = 24
  const scaleX = (rect.width - padding * 2) / map.width
  const scaleY = (rect.height - padding * 2) / map.height
  view.scale = Math.min(scaleX, scaleY, 4)
  view.offsetX = (rect.width - map.width * view.scale) / 2
  view.offsetY = (rect.height - map.height * view.scale) / 2
}

function applyMapState(patch: Partial<typeof map>) {
  Object.assign(map, createDefaultMapState(), patch)
  mapBitmapCtx = mapBitmapCanvas.getContext('2d')
  rebuildMapBitmap(map, mapBitmapCanvas)
  fitToScreen()
}

function loadMapFromDoc(doc: RouteExecutorDocument | null | undefined) {
  if (!doc?.map_snapshot) {
    Object.assign(map, createDefaultMapState())
    return
  }
  applyMapState(decodeMapSnapshot(doc.map_snapshot))
}

async function loadMapFromRoute(route: Route | null | undefined) {
  if (!route) {
    Object.assign(map, createDefaultMapState())
    return
  }
  if (route.mapId) {
    mapLoading.value = true
    try {
      const files = await fetchMapAssetFiles(route.mapId)
      const parsed = parsePgm(files.pgmBuffer)
      applyMapState({
        ...parseYaml(files.yamlText),
        width: parsed.width,
        height: parsed.height,
        pixels: parsed.pixels,
        yamlName: files.yamlName,
        pgmName: files.pgmName,
      })
      return
    } catch {
      // fall back to embedded snapshot
    } finally {
      mapLoading.value = false
    }
  }
  loadMapFromDoc(route.executorJson)
}

function drawArrow(ctx: CanvasRenderingContext2D, x: number, y: number, yaw: number, color: string, scale: number) {
  const len = 22
  ctx.save()
  ctx.translate(x, y)
  ctx.rotate(-yaw)
  ctx.strokeStyle = color
  ctx.fillStyle = color
  ctx.lineWidth = 2 / scale
  ctx.beginPath()
  ctx.moveTo(0, 0)
  ctx.lineTo(len / scale, 0)
  ctx.stroke()
  ctx.beginPath()
  ctx.moveTo(len / scale, 0)
  ctx.lineTo((len - 6) / scale, -4 / scale)
  ctx.lineTo((len - 6) / scale, 4 / scale)
  ctx.closePath()
  ctx.fill()
  ctx.restore()
}

function drawRobotIcon(ctx: CanvasRenderingContext2D, x: number, y: number, yaw: number, scale: number) {
  const w = 14 / scale
  const h = 10 / scale
  ctx.save()
  ctx.translate(x, y)
  ctx.rotate(-yaw)
  ctx.fillStyle = '#1a5fb4'
  ctx.strokeStyle = '#ffffff'
  ctx.lineWidth = 2 / scale
  ctx.fillRect(-w / 2, -h / 2, w, h)
  ctx.strokeRect(-w / 2, -h / 2, w, h)
  ctx.fillStyle = '#ffd700'
  ctx.beginPath()
  ctx.moveTo(w / 2, 0)
  ctx.lineTo(w / 2 + 6 / scale, -3 / scale)
  ctx.lineTo(w / 2 + 6 / scale, 3 / scale)
  ctx.closePath()
  ctx.fill()
  ctx.restore()
}

function draw() {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  const wrap = wrapRef.value
  if (!canvas || !ctx || !wrap) return

  const rect = wrap.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.max(1, Math.floor(rect.width * dpr))
  canvas.height = Math.max(1, Math.floor(rect.height * dpr))
  canvas.style.width = `${rect.width}px`
  canvas.style.height = `${rect.height}px`
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, rect.width, rect.height)

  ctx.save()
  ctx.translate(view.offsetX, view.offsetY)
  ctx.scale(view.scale, view.scale)

  if (mapBitmapCtx && map.width && map.height) {
    ctx.imageSmoothingEnabled = false
    ctx.drawImage(mapBitmapCanvas, 0, 0)
  } else {
    ctx.fillStyle = '#f0f4f8'
    ctx.fillRect(0, 0, rect.width, rect.height)
  }

  const doc = executorDoc.value
  if (doc) {
    const start = doc.start_pose?.pose
    const targets = doc.targets ?? []

    if (targets.length && start) {
      ctx.strokeStyle = '#2563eb'
      ctx.lineWidth = 2 / view.scale
      ctx.setLineDash([6 / view.scale, 4 / view.scale])
      ctx.beginPath()
      const sp = mapToPixel(map, start.x, start.y)
      ctx.moveTo(sp.x, sp.y)
      for (const t of targets) {
        const p = mapToPixel(map, t.pose.x, t.pose.y)
        ctx.lineTo(p.x, p.y)
      }
      const routeDef = doc.routes[0]
      if (routeDef?.return_to_start) ctx.lineTo(sp.x, sp.y)
      ctx.stroke()
      ctx.setLineDash([])
    }

    if (start) {
      const p = mapToPixel(map, start.x, start.y)
      ctx.fillStyle = '#0f766e'
      ctx.strokeStyle = '#fff'
      ctx.lineWidth = 2 / view.scale
      ctx.beginPath()
      ctx.arc(p.x, p.y, 7 / view.scale, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      drawArrow(ctx, p.x, p.y, start.yaw ?? 0, '#0f766e', view.scale)
    }

    targets.forEach((target, index) => {
      const p = mapToPixel(map, target.pose.x, target.pose.y)
      ctx.fillStyle = '#2563eb'
      ctx.strokeStyle = '#fff'
      ctx.lineWidth = 2 / view.scale
      ctx.beginPath()
      ctx.arc(p.x, p.y, 6 / view.scale, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      drawArrow(ctx, p.x, p.y, target.pose.yaw ?? 0, '#2563eb', view.scale)
      ctx.fillStyle = '#fff'
      ctx.font = `bold ${10 / view.scale}px sans-serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(String(index + 1), p.x, p.y)
    })
  }

  if (props.robotPosition && mapReady.value) {
    const { x, y, yaw } = rosCoordFromLatLng(props.robotPosition)
    const p = mapToPixel(map, x, y)
    ctx.shadowColor = 'rgba(26, 95, 180, 0.45)'
    ctx.shadowBlur = 8 / view.scale
    drawRobotIcon(ctx, p.x, p.y, yaw, view.scale)
    ctx.shadowBlur = 0
  }

  ctx.restore()
}

function resizeCanvas() {
  fitToScreen()
  draw()
}

watch(() => props.route, (route) => void loadMapFromRoute(route), { immediate: true, deep: true })
watch(() => props.robotPosition, () => draw(), { deep: true })

onMounted(() => {
  void loadMapFromRoute(props.route)
  resizeObserver = new ResizeObserver(() => resizeCanvas())
  if (wrapRef.value) resizeObserver.observe(wrapRef.value)
  resizeCanvas()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.ros-monitor-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 360px;
  background: #0d2137;
  border-radius: 8px;
  overflow: hidden;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.overlay-3d {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  pointer-events: none;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #b8c5d6;
  font-size: 14px;
  text-align: center;
  padding: 24px;
  background: rgba(13, 33, 55, 0.72);
}

.map-empty small {
  font-size: 12px;
  color: #8a9bb0;
  max-width: 320px;
}

.map-legend {
  position: absolute;
  bottom: 10px;
  left: 10px;
  display: flex;
  gap: 12px;
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 6px;
  font-size: 11px;
  color: #fff;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.start {
  background: #0f766e;
}

.dot.target {
  background: #2563eb;
}

.dot.robot {
  background: #1a5fb4;
  border-radius: 2px;
}
</style>
