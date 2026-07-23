<template>
  <div
    ref="wrapRef"
    class="ros-monitor-map"
    :class="{ dragging: pointerState.active }"
    @wheel.prevent="onWheel"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <canvas ref="canvasRef" />

    <div v-if="mapLoading" class="map-empty">
      <span>正在加载导航地图...</span>
    </div>
    <div v-else-if="!mapReady" class="map-empty">
      <span>暂无二维建图数据</span>
      <small>请先为当前路线关联已审核的 ROS 栅格地图</small>
    </div>

    <div v-if="showConnectionAlert && mapReady" class="connection-alert">
      <strong>实时数据已中断</strong>
      <span>最后位置：{{ lastPositionAt || '未知' }}</span>
    </div>
    <div v-else-if="showTrackingAlert && mapReady" class="tracking-alert">
      <strong>{{ trackingAlert }}</strong>
    </div>

    <div v-if="mapReady" class="map-legend" aria-label="地图图例">
      <strong>图例</strong>
      <span><i class="robot-symbol" />机器人位置及朝向</span>
      <span><i class="line completed" />已完成路线</span>
      <span><i class="line active" />正在执行路线</span>
      <span><i class="line pending" />待执行路线</span>
      <span><i class="line abnormal" />阻塞 / 告警路线</span>
      <span><i class="zone" />禁行区</span>
    </div>

    <div v-if="mapReady" class="map-scale">滚轮缩放 · 拖拽平移</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type { LatLng, Route } from '@/types'
import type { RouteExecutorDocument, RosMapState, RouteMapSnapshot, RouteTarget } from '@/types/routeExecutor'
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

const props = withDefaults(defineProps<{
  route?: Route | null
  robotPosition?: LatLng | null
  taskProgress?: number
  activeTargetId?: string | null
  alarmCheckpointIds?: string[]
  routeAbnormal?: boolean
  offline?: boolean
  lastPositionAt?: string
  trackingAlert?: string
}>(), {
  route: null,
  robotPosition: null,
  taskProgress: 0,
  activeTargetId: null,
  alarmCheckpointIds: () => [],
  routeAbnormal: false,
  offline: false,
  lastPositionAt: '',
  trackingAlert: '',
})

const ALERT_DISPLAY_DURATION_MS = 5000
const wrapRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const map = reactive<RosMapState>(createDefaultMapState())
const view = reactive({ scale: 1, offsetX: 0, offsetY: 0 })
const pointerState = reactive({ active: false, x: 0, y: 0 })
const showConnectionAlert = ref(false)
const showTrackingAlert = ref(false)
const mapBitmapCanvas = document.createElement('canvas')
let mapBitmapCtx = mapBitmapCanvas.getContext('2d')
let resizeObserver: ResizeObserver | null = null
let connectionAlertTimer: ReturnType<typeof setTimeout> | undefined
let trackingAlertTimer: ReturnType<typeof setTimeout> | undefined

const mapReady = computed(() => Boolean(map.width && map.height && map.pixels))
const mapLoading = ref(false)
const executorDoc = computed(() => props.route?.executorJson ?? null)

function fitToScreen() {
  const wrap = wrapRef.value
  if (!wrap || !map.width || !map.height) return
  const rect = wrap.getBoundingClientRect()
  const padding = 42
  const scaleX = (rect.width - padding * 2) / map.width
  const scaleY = (rect.height - padding * 2) / map.height
  view.scale = Math.min(scaleX, scaleY, 5)
  view.offsetX = (rect.width - map.width * view.scale) / 2
  view.offsetY = (rect.height - map.height * view.scale) / 2
}

function recenter() {
  fitToScreen()
  draw()
}

function applyMapState(patch: Partial<typeof map>) {
  Object.assign(map, createDefaultMapState(), patch)
  mapBitmapCtx = mapBitmapCanvas.getContext('2d')
  rebuildMapBitmap(map, mapBitmapCanvas)
  recenter()
}

function isRouteMapSnapshot(value: unknown): value is RouteMapSnapshot {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const snapshot = value as Record<string, unknown>
  return typeof snapshot.width === 'number'
    && typeof snapshot.height === 'number'
    && typeof snapshot.resolution === 'number'
    && typeof snapshot.negate === 'number'
    && typeof snapshot.pgm_base64 === 'string'
    && Array.isArray(snapshot.origin)
    && snapshot.origin.length === 3
}

function loadMapFromDoc(doc: RouteExecutorDocument | null | undefined) {
  const snapshot = doc?.map_snapshot
  if (!isRouteMapSnapshot(snapshot)) {
    Object.assign(map, createDefaultMapState())
    draw()
    return
  }
  applyMapState(decodeMapSnapshot(snapshot))
}

async function loadMapFromRoute(route: Route | null | undefined) {
  if (!route) {
    Object.assign(map, createDefaultMapState())
    draw()
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
      // 旧路线可能只保存了嵌入式地图快照，继续尝试回退加载。
    } finally {
      mapLoading.value = false
    }
  }
  loadMapFromDoc(route.executorJson)
}

function orderedTargets(doc: RouteExecutorDocument): RouteTarget[] {
  const route = doc.routes.find(item => item.id === doc.active_route_id) ?? doc.routes[0]
  if (!route?.target_ids?.length) return doc.targets
  const byId = new Map(doc.targets.map(target => [target.id, target]))
  return route.target_ids.map(id => byId.get(id)).filter((target): target is RouteTarget => Boolean(target))
}

function drawKeepoutZones(ctx: CanvasRenderingContext2D, doc: RouteExecutorDocument) {
  for (const keepout of doc.keepout_zones ?? []) {
    if (!keepout.enabled || keepout.polygon.length < 3) continue
    ctx.save()
    ctx.beginPath()
    keepout.polygon.forEach((point, index) => {
      const pixel = mapToPixel(map, point.x, point.y)
      if (index === 0) ctx.moveTo(pixel.x, pixel.y)
      else ctx.lineTo(pixel.x, pixel.y)
    })
    ctx.closePath()
    ctx.fillStyle = 'rgba(240, 68, 56, 0.12)'
    ctx.strokeStyle = '#f04438'
    ctx.lineWidth = 1.5 / view.scale
    ctx.setLineDash([5 / view.scale, 3 / view.scale])
    ctx.fill()
    ctx.stroke()
    ctx.restore()
  }
}

function segmentState(target: RouteTarget, index: number, targetCount: number) {
  if (props.alarmCheckpointIds.includes(target.id)) return 'abnormal'
  const activeIndex = props.activeTargetId
    ? orderedTargets(executorDoc.value!).findIndex(item => item.id === props.activeTargetId)
    : -1
  const completedCount = activeIndex >= 0
    ? activeIndex
    : Math.floor(Math.max(0, Math.min(props.taskProgress, 100)) / 100 * targetCount)
  const executingIndex = activeIndex >= 0 ? activeIndex : Math.min(completedCount, targetCount - 1)
  if (index < completedCount) return 'completed'
  if (index === executingIndex) return props.routeAbnormal ? 'abnormal' : 'active'
  return 'pending'
}

function strokeRouteSegment(
  ctx: CanvasRenderingContext2D,
  from: { x: number; y: number },
  to: { x: number; y: number },
  state: ReturnType<typeof segmentState>,
) {
  const styles = {
    completed: { color: '#79a8b5', width: 3, dash: [] as number[] },
    active: { color: '#1768f2', width: 4, dash: [] as number[] },
    pending: { color: '#4d94ff', width: 2.5, dash: [8, 6] },
    abnormal: { color: '#f04438', width: 4, dash: [] as number[] },
  }
  const style = styles[state]
  ctx.save()
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.94)'
  ctx.lineWidth = (style.width + 3) / view.scale
  ctx.setLineDash(style.dash.map(value => value / view.scale))
  ctx.beginPath()
  ctx.moveTo(from.x, from.y)
  ctx.lineTo(to.x, to.y)
  ctx.stroke()
  ctx.strokeStyle = style.color
  ctx.lineWidth = style.width / view.scale
  ctx.beginPath()
  ctx.moveTo(from.x, from.y)
  ctx.lineTo(to.x, to.y)
  ctx.stroke()
  ctx.restore()
}

function drawRoute(ctx: CanvasRenderingContext2D, doc: RouteExecutorDocument) {
  const start = doc.start_pose?.pose
  const targets = orderedTargets(doc)
  if (!start || !targets.length) return

  let previous = mapToPixel(map, start.x, start.y)
  targets.forEach((target, index) => {
    const current = mapToPixel(map, target.pose.x, target.pose.y)
    strokeRouteSegment(ctx, previous, current, segmentState(target, index, targets.length))
    previous = current
  })

  const route = doc.routes.find(item => item.id === doc.active_route_id) ?? doc.routes[0]
  if (route?.return_to_start) {
    const target = targets.at(-1)
    if (target) {
      strokeRouteSegment(
        ctx,
        mapToPixel(map, target.pose.x, target.pose.y),
        mapToPixel(map, start.x, start.y),
        props.taskProgress >= 100 ? 'completed' : 'pending',
      )
    }
  }

  drawWaypoint(ctx, start.x, start.y, 'start', 'S')
  targets.forEach((target, index) => {
    const isEnd = index === targets.length - 1
    drawWaypoint(ctx, target.pose.x, target.pose.y, isEnd ? 'end' : 'target', isEnd ? 'E' : String(index + 1))
    if (props.alarmCheckpointIds.includes(target.id)) drawAlarm(ctx, target.pose.x, target.pose.y)
  })
}

function drawWaypoint(ctx: CanvasRenderingContext2D, x: number, y: number, kind: 'start' | 'target' | 'end', label: string) {
  const point = mapToPixel(map, x, y)
  const colors = { start: '#2da76f', target: '#2f80ed', end: '#f04438' }
  ctx.save()
  ctx.fillStyle = '#fff'
  ctx.strokeStyle = colors[kind]
  ctx.lineWidth = 3 / view.scale
  ctx.beginPath()
  ctx.arc(point.x, point.y, 7 / view.scale, 0, Math.PI * 2)
  ctx.fill()
  ctx.stroke()
  ctx.fillStyle = colors[kind]
  ctx.font = `700 ${8 / view.scale}px "Segoe UI", sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(label, point.x, point.y + 0.25 / view.scale)
  ctx.restore()
}

function drawAlarm(ctx: CanvasRenderingContext2D, x: number, y: number) {
  const point = mapToPixel(map, x, y)
  const size = 9 / view.scale
  ctx.save()
  ctx.translate(point.x + 12 / view.scale, point.y - 12 / view.scale)
  ctx.fillStyle = '#f04438'
  ctx.strokeStyle = '#fff'
  ctx.lineWidth = 2 / view.scale
  ctx.beginPath()
  ctx.moveTo(0, -size)
  ctx.lineTo(size, size)
  ctx.lineTo(-size, size)
  ctx.closePath()
  ctx.fill()
  ctx.stroke()
  ctx.fillStyle = '#fff'
  ctx.font = `800 ${10 / view.scale}px sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('!', 0, size * 0.35)
  ctx.restore()
}

function drawRobot(ctx: CanvasRenderingContext2D) {
  if (!props.robotPosition || !mapReady.value) return
  const { x, y, yaw } = rosCoordFromLatLng(props.robotPosition)
  const point = mapToPixel(map, x, y)
  const scale = view.scale
  ctx.save()
  ctx.translate(point.x, point.y)
  ctx.rotate(-yaw)
  ctx.shadowColor = 'rgba(23, 104, 242, 0.35)'
  ctx.shadowBlur = 12 / scale
  ctx.fillStyle = '#fff'
  ctx.strokeStyle = '#1768f2'
  ctx.lineWidth = 3 / scale
  ctx.beginPath()
  ctx.arc(0, 0, 15 / scale, 0, Math.PI * 2)
  ctx.fill()
  ctx.stroke()
  ctx.shadowBlur = 0
  ctx.fillStyle = '#1768f2'
  ctx.fillRect(-7 / scale, -6 / scale, 13 / scale, 12 / scale)
  ctx.beginPath()
  ctx.moveTo(8 / scale, 0)
  ctx.lineTo(20 / scale, -6 / scale)
  ctx.lineTo(20 / scale, 6 / scale)
  ctx.closePath()
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.beginPath()
  ctx.arc(-3 / scale, -2 / scale, 1.4 / scale, 0, Math.PI * 2)
  ctx.arc(-3 / scale, 2 / scale, 1.4 / scale, 0, Math.PI * 2)
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
  ctx.fillStyle = '#f5f8fb'
  ctx.fillRect(0, 0, rect.width, rect.height)

  ctx.save()
  ctx.translate(view.offsetX, view.offsetY)
  ctx.scale(view.scale, view.scale)
  if (mapBitmapCtx && map.width && map.height) {
    ctx.imageSmoothingEnabled = false
    ctx.shadowColor = 'rgba(31, 50, 73, 0.08)'
    ctx.shadowBlur = 18 / view.scale
    ctx.drawImage(mapBitmapCanvas, 0, 0)
    ctx.shadowBlur = 0
  }

  const doc = executorDoc.value
  if (doc) {
    drawKeepoutZones(ctx, doc)
    drawRoute(ctx, doc)
  }
  drawRobot(ctx)
  ctx.restore()
}

function onWheel(event: WheelEvent) {
  if (!mapReady.value) return
  const wrap = wrapRef.value
  if (!wrap) return
  const rect = wrap.getBoundingClientRect()
  const mouseX = event.clientX - rect.left
  const mouseY = event.clientY - rect.top
  const previousScale = view.scale
  const nextScale = Math.max(0.15, Math.min(previousScale * (event.deltaY < 0 ? 1.12 : 0.9), 12))
  view.offsetX = mouseX - (mouseX - view.offsetX) * nextScale / previousScale
  view.offsetY = mouseY - (mouseY - view.offsetY) * nextScale / previousScale
  view.scale = nextScale
  draw()
}

function onPointerDown(event: PointerEvent) {
  if (!mapReady.value || event.button !== 0) return
  pointerState.active = true
  pointerState.x = event.clientX
  pointerState.y = event.clientY
  wrapRef.value?.setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!pointerState.active) return
  view.offsetX += event.clientX - pointerState.x
  view.offsetY += event.clientY - pointerState.y
  pointerState.x = event.clientX
  pointerState.y = event.clientY
  draw()
}

function onPointerUp(event: PointerEvent) {
  pointerState.active = false
  if (wrapRef.value?.hasPointerCapture(event.pointerId)) wrapRef.value.releasePointerCapture(event.pointerId)
}

function resizeCanvas() {
  recenter()
}

function hideTrackingAlert() {
  if (trackingAlertTimer) clearTimeout(trackingAlertTimer)
  trackingAlertTimer = undefined
  showTrackingAlert.value = false
}

function showTrackingAlertTemporarily() {
  hideTrackingAlert()
  if (!props.trackingAlert || showConnectionAlert.value) return
  showTrackingAlert.value = true
  trackingAlertTimer = setTimeout(() => {
    showTrackingAlert.value = false
    trackingAlertTimer = undefined
  }, ALERT_DISPLAY_DURATION_MS)
}

watch(() => props.route, route => void loadMapFromRoute(route), { immediate: true, deep: true })
watch(() => props.offline, (offline, wasOffline) => {
  if (connectionAlertTimer) clearTimeout(connectionAlertTimer)
  connectionAlertTimer = undefined
  showConnectionAlert.value = offline
  if (offline) {
    hideTrackingAlert()
    connectionAlertTimer = setTimeout(() => {
      showConnectionAlert.value = false
      connectionAlertTimer = undefined
      showTrackingAlertTemporarily()
    }, ALERT_DISPLAY_DURATION_MS)
  } else if (wasOffline) {
    showTrackingAlertTemporarily()
  }
}, { immediate: true })
watch(() => props.trackingAlert, showTrackingAlertTemporarily, { immediate: true })
watch(
  () => [props.robotPosition, props.taskProgress, props.activeTargetId, props.alarmCheckpointIds, props.routeAbnormal],
  draw,
  { deep: true },
)

onMounted(() => {
  resizeObserver = new ResizeObserver(resizeCanvas)
  if (wrapRef.value) resizeObserver.observe(wrapRef.value)
  resizeCanvas()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  if (connectionAlertTimer) clearTimeout(connectionAlertTimer)
  if (trackingAlertTimer) clearTimeout(trackingAlertTimer)
})

defineExpose({ recenter })
</script>

<style scoped>
.ros-monitor-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 420px;
  overflow: hidden;
  cursor: grab;
  background: #f5f8fb;
  user-select: none;
  touch-action: none;
}

.ros-monitor-map.dragging { cursor: grabbing; }

canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: #526986;
  text-align: center;
  background: #f5f8fb;
}

.map-empty span { font-size: 14px; font-weight: 700; }
.map-empty small { max-width: 360px; color: #8798ad; font-size: 12px; }

.connection-alert,
.tracking-alert {
  position: absolute;
  z-index: 3;
  top: 14px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 38px;
  padding: 8px 14px;
  border: 1px solid #fda29b;
  border-radius: 6px;
  color: #b42318;
  font-size: 12px;
  background: rgba(255, 251, 250, 0.96);
  box-shadow: 0 5px 16px rgba(180, 35, 24, 0.12);
  transform: translateX(-50%);
}

.tracking-alert { border-color: #fec84b; color: #b54708; background: rgba(255, 252, 235, 0.96); }
.connection-alert strong,
.tracking-alert strong { font-size: 13px; }

.map-legend {
  position: absolute;
  z-index: 2;
  bottom: 18px;
  left: 16px;
  display: grid;
  gap: 8px;
  width: 170px;
  padding: 12px;
  border: 1px solid #dce5ef;
  border-radius: 6px;
  color: #536b88;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 6px 18px rgba(31, 50, 73, 0.09);
  pointer-events: none;
}

.map-legend strong { margin-bottom: 2px; color: #1f3551; font-size: 12px; }
.map-legend span { display: flex; align-items: center; gap: 8px; }
.map-legend .line { width: 22px; height: 0; border-top: 3px solid; }
.map-legend .line.completed { border-color: #79a8b5; }
.map-legend .line.active { border-color: #1768f2; }
.map-legend .line.pending { border-color: #4d94ff; border-top-style: dashed; }
.map-legend .line.abnormal { border-color: #f04438; }
.robot-symbol { width: 11px; height: 11px; border: 3px solid #1768f2; border-radius: 50%; background: #fff; }
.zone { width: 17px; height: 12px; border: 1px dashed #f04438; background: repeating-linear-gradient(135deg, rgba(240, 68, 56, .18) 0 2px, transparent 2px 5px); }

.map-scale {
  position: absolute;
  right: 14px;
  bottom: 12px;
  color: #7a8da6;
  font-size: 10px;
  pointer-events: none;
}

@media (max-width: 700px) {
  .map-legend { width: 154px; gap: 6px; padding: 10px; }
  .connection-alert,
  .tracking-alert { left: 12px; right: 12px; justify-content: center; transform: none; }
  .map-scale { display: none; }
}
</style>
