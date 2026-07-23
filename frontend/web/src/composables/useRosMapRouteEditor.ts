import { computed, onMounted, onUnmounted, reactive, ref, shallowRef, triggerRef, watch, type Ref } from 'vue'
import type { EditableKeepoutZone, EditorMode, MapAssetIdentity, RouteExecutorDocument, RosMapState } from '@/types/routeExecutor'
import {
  createDefaultMapState,
  mapToPixel,
  parsePgm,
  parseYaml,
  pixelToMap,
  rebuildMapBitmap,
  rosMapImageFileName,
  round3,
} from '@/utils/rosMap'
import {
  buildRouteJson,
  createDefaultRouteForm,
  loadRouteJson,
  type RouteExecutorTarget,
  type RouteFormState,
} from '@/utils/routeExecutorJson'
import { parseRouteDocument } from '@/utils/route/parseRouteDocument'
import {
  applyRouteSafety,
  calculateRouteSafety,
  classifyFootprint,
  classifyMapCoordinate,
  describeMapClass,
  transformFootprint,
  validateAnnotationExport,
  type MapClass,
  type RouteSafetyPoint,
} from '@/utils/route/routeSafety'

type YawTarget = { kind: 'start'; id: null } | { kind: 'target'; id: string }
type PointHit = { kind: 'start' } | { kind: 'target'; id: string } | { kind: 'zonePoint'; id: string; index: number }
type DragState =
  | { type: 'pan'; startX: number; startY: number; ox: number; oy: number }
  | { type: 'start' }
  | { type: 'target'; id: string }
  | { type: 'zonePoint'; id: string; index: number }
  | { type: 'yaw'; hit: YawTarget; moved: boolean }

export function calculateAspectFitView(
  containerWidth: number,
  containerHeight: number,
  mapWidth: number,
  mapHeight: number,
) {
  const scale = Math.max(0.05, Math.min(containerWidth / mapWidth, containerHeight / mapHeight) * 0.96)
  return {
    scale,
    offsetX: (containerWidth - mapWidth * scale) / 2,
    offsetY: (containerHeight - mapHeight * scale) / 2,
  }
}

export function calculateAspectFillView(
  containerWidth: number,
  containerHeight: number,
  mapWidth: number,
  mapHeight: number,
) {
  const scale = Math.max(0.05, Math.max(containerWidth / mapWidth, containerHeight / mapHeight))
  return {
    scale,
    offsetX: (containerWidth - mapWidth * scale) / 2,
    offsetY: (containerHeight - mapHeight * scale) / 2,
  }
}

export function useRosMapRouteEditor(
  canvasRef: Ref<HTMLCanvasElement | null>,
  wrapRef: Ref<HTMLElement | null>,
  options?: {
    initialJson?: () => RouteExecutorDocument | null | undefined
    defaultRouteId?: string
    defaultRouteName?: string
    onChange?: (doc: RouteExecutorDocument) => void
  },
) {
  const map = reactive<RosMapState>(createDefaultMapState())
  const view = reactive({ scaleX: 1, scaleY: 1, offsetX: 0, offsetY: 0 })
  const mode = ref<EditorMode>('select')
  const targets = shallowRef<RouteExecutorTarget[]>([])
  const keepoutZones = shallowRef<EditableKeepoutZone[]>([])
  const activeZoneId = ref<string | null>(null)
  const selectedTargetId = ref<string | null>(null)
  const yawTarget = ref<YawTarget>({ kind: 'start', id: null })
  const yawPreview = ref<{ x: number; y: number } | null>(null)
  const hoverPixel = ref<{ x: number; y: number } | null>(null)
  const drag = ref<DragState | null>(null)
  const nextTargetNo = ref(1)
  const sourceTemplate = shallowRef<RouteExecutorDocument | null>(null)
  const mapIdentity = shallowRef<MapAssetIdentity>({ yaml: '', image: '', resolution: 0, origin: [0, 0, 0], width: 0, height: 0, image_sha256: '' })
  const form = reactive<RouteFormState>(createDefaultRouteForm(options?.defaultRouteId, options?.defaultRouteName))
  const activeRouteIdSynced = ref(true)
  const cursorInfo = ref('map: -, -')
  const mapInfo = ref('等待加载地图')
  const showFootprint = ref(true)
  const showFootprintPadding = ref(true)

  const mapBitmapCanvas = document.createElement('canvas')
  let mapBitmapCtx = mapBitmapCanvas.getContext('2d')
  let wrapResizeObserver: ResizeObserver | null = null

  const jsonPreview = computed<string>(() => JSON.stringify(exportDocument() as unknown, null, 2))
  const isLegacyDraft = computed(() => sourceTemplate.value?.version === 2)

  function currentRoutePoints(): RouteSafetyPoint[] {
    const points: RouteSafetyPoint[] = [{ id: 'start_pose', label: '起点', x: form.startX, y: form.startY, yaw: form.startYaw }]
    targets.value.forEach((target, index) => {
      points.push({ id: target.id, label: `#${index + 1} ${target.name || target.id}`, x: target.x, y: target.y, yaw: target.yaw })
    })
    return points
  }

  const annotationProblems = computed(() => validateAnnotationExport(map, mapIdentity.value.image_sha256, keepoutZones.value, currentRoutePoints()))
  const routeSafety = computed(() => calculateRouteSafety(map, keepoutZones.value, currentRoutePoints()))

  const targetStatus = computed(() => {
    if (annotationProblems.value.length) {
      return {
        text: `保存与导出已阻止：${annotationProblems.value.join('；')}`,
        kind: 'error' as const,
      }
    }
    const label = getPointLabel(getSelectedYawHit())
    if (targets.value.length) {
      return {
        text: `共 ${targets.value.length} 个巡检点，顺序即导航顺序。当前方向点：${label}。`,
        kind: 'normal' as const,
      }
    }
    return { text: `还没有巡检点。当前方向点：${label}。`, kind: 'normal' as const }
  })

  const zoneStatus = computed(() => keepoutZones.value.length
    ? `共 ${keepoutZones.value.length} 个禁行区，当前 ${activeZoneId.value || '无'}。`
    : '无禁行区。选择“禁行区”模式后点击地图追加 polygon 顶点。')

  function getSelectedYawHit(): YawTarget {
    if (!yawTarget.value || yawTarget.value.kind === 'start') return { kind: 'start', id: null }
    const target = targets.value.find((item) => item.id === yawTarget.value.id)
    return target ? { kind: 'target', id: target.id } : { kind: 'start', id: null }
  }

  function getPointLabel(hit: YawTarget): string {
    if (!hit || hit.kind === 'start') return '起点'
    const index = targets.value.findIndex((item) => item.id === hit.id)
    return index >= 0 ? `#${index + 1} ${targets.value[index].name}` : '巡检点'
  }

  function viewUiScale() {
    return Math.min(view.scaleX, view.scaleY)
  }

  function canvasToPixel(clientX: number, clientY: number) {
    const canvas = canvasRef.value
    if (!canvas) return { x: 0, y: 0 }
    const rect = canvas.getBoundingClientRect()
    const x = (clientX - rect.left - view.offsetX) / view.scaleX
    const y = (clientY - rect.top - view.offsetY) / view.scaleY
    return { x, y }
  }

  function getCtx() {
    return canvasRef.value?.getContext('2d') ?? null
  }

  let mapInfoDirty = true

  function updateMapInfoText() {
    if (!mapInfoDirty) return
    mapInfoDirty = false
    if (!map.width) {
      mapInfo.value = map.yamlName
        ? `${map.yamlName} 已加载（image: ${map.image}），请继续选择对应的 PGM 文件`
        : '请选择或拖入 .yaml 和 .pgm 地图文件'
      return
    }
    const wMeters = (map.width * map.resolution).toFixed(2)
    const hMeters = (map.height * map.resolution).toFixed(2)
    const minX = map.origin[0]
    const maxX = map.origin[0] + map.width * map.resolution
    const minY = map.origin[1]
    const maxY = map.origin[1] + map.height * map.resolution
    const files = [map.yamlName, map.pgmName || map.image].filter(Boolean).join(' + ')
    mapInfo.value = `${files} | ${map.width}x${map.height}px | ${wMeters}m x ${hMeters}m | origin=[${map.origin.join(', ')}] | res=${map.resolution} | x=${minX.toFixed(2)}..${maxX.toFixed(2)}, y=${minY.toFixed(2)}..${maxY.toFixed(2)}`
  }

  function mapPixelToScreen(px: number, py: number) {
    return {
      x: view.offsetX + px * view.scaleX,
      y: view.offsetY + py * view.scaleY,
    }
  }

  function drawArrowAtMapPixel(ctx: CanvasRenderingContext2D, px: number, py: number, yaw: number, color: string) {
    const start = mapPixelToScreen(px, py)
    const tipPx = px + Math.cos(-yaw) * (24 / view.scaleX)
    const tipPy = py + Math.sin(-yaw) * (24 / view.scaleY)
    const tip = mapPixelToScreen(tipPx, tipPy)
    const wingPx = px + Math.cos(-yaw) * (17 / view.scaleX) - Math.sin(-yaw) * (5 / view.scaleY)
    const wingPy = py + Math.sin(-yaw) * (17 / view.scaleY) + Math.cos(-yaw) * (5 / view.scaleX)
    const wingLeft = mapPixelToScreen(wingPx, wingPy)
    const wingPx2 = px + Math.cos(-yaw) * (17 / view.scaleX) + Math.sin(-yaw) * (5 / view.scaleY)
    const wingPy2 = py + Math.sin(-yaw) * (17 / view.scaleY) - Math.cos(-yaw) * (5 / view.scaleX)
    const wingRight = mapPixelToScreen(wingPx2, wingPy2)

    ctx.strokeStyle = color
    ctx.fillStyle = color
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(start.x, start.y)
    ctx.lineTo(tip.x, tip.y)
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(tip.x, tip.y)
    ctx.lineTo(wingLeft.x, wingLeft.y)
    ctx.lineTo(wingRight.x, wingRight.y)
    ctx.closePath()
    ctx.fill()
  }

  function colorWithAlpha(hex: string, alpha: number) {
    const value = Number.parseInt(hex.slice(1), 16)
    return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`
  }

  function drawFootprint(ctx: CanvasRenderingContext2D, pose: { x: number; y: number; yaw: number }, color: string, alpha = 0.16): MapClass {
    const status = classifyFootprint(map, keepoutZones.value, pose)
    const blocked = status === 'outside' || status === 'occupied' || status === 'keepout'
    const baseColor = blocked ? '#b42318' : status === 'unknown' ? '#b54708' : color
    const path = (points: Array<{ x: number; y: number }>) => {
      const screenPoints = points.map((point) => {
        const pixel = mapToPixel(map, point.x, point.y)
        return mapPixelToScreen(pixel.x, pixel.y)
      })
      ctx.beginPath()
      ctx.moveTo(screenPoints[0].x, screenPoints[0].y)
      for (let index = 1; index < screenPoints.length; index += 1) {
        ctx.lineTo(screenPoints[index].x, screenPoints[index].y)
      }
      ctx.closePath()
    }
    if (showFootprintPadding.value) {
      path(transformFootprint(pose, true))
      ctx.fillStyle = colorWithAlpha(baseColor, alpha * 0.45)
      ctx.strokeStyle = colorWithAlpha(baseColor, 0.72)
      ctx.lineWidth = 1.2
      ctx.fill()
      ctx.stroke()
    }
    if (showFootprint.value) {
      path(transformFootprint(pose))
      ctx.fillStyle = colorWithAlpha(baseColor, alpha)
      ctx.strokeStyle = baseColor
      ctx.lineWidth = 1.4
      ctx.fill()
      ctx.stroke()
    }
    return status
  }

  function drawKeepoutZones(ctx: CanvasRenderingContext2D) {
    for (const zone of keepoutZones.value) {
      if (!zone.polygon.length) continue
      const points = zone.polygon.map((point) => {
        const pixel = mapToPixel(map, point.x, point.y)
        return mapPixelToScreen(pixel.x, pixel.y)
      })
      ctx.beginPath()
      ctx.moveTo(points[0].x, points[0].y)
      for (let index = 1; index < points.length; index += 1) ctx.lineTo(points[index].x, points[index].y)
      if (points.length >= 3) ctx.closePath()
      const active = zone.id === activeZoneId.value
      ctx.fillStyle = zone.enabled ? 'rgba(180, 35, 24, 0.22)' : 'rgba(102, 112, 133, 0.16)'
      ctx.strokeStyle = active ? '#b42318' : zone.enabled ? '#d92d20' : '#667085'
      ctx.lineWidth = active ? 3 : 2
      if (points.length >= 3) ctx.fill()
      ctx.stroke()
      points.forEach((point, index) => {
        ctx.fillStyle = active ? '#b42318' : '#d92d20'
        ctx.beginPath()
        ctx.arc(point.x, point.y, 4, 0, Math.PI * 2)
        ctx.fill()
        ctx.fillStyle = '#111827'
        ctx.font = '11px Microsoft YaHei, sans-serif'
        ctx.fillText(String(index + 1), point.x + 5, point.y - 5)
      })
      ctx.fillStyle = '#111827'
      ctx.font = '700 12px Microsoft YaHei, sans-serif'
      ctx.fillText(zone.name || zone.id, points[0].x + 10, points[0].y + 14)
    }
  }

  function drawMapOverlays(ctx: CanvasRenderingContext2D) {
    const allPoints = [
      { kind: 'start' as const, x: form.startX, y: form.startY, yaw: form.startYaw },
      ...targets.value.map((target, index) => ({ kind: 'target' as const, order: index + 1, ...target })),
    ]

    drawKeepoutZones(ctx)

    if (targets.value.length > 0) {
      ctx.strokeStyle = '#2563eb'
      ctx.lineWidth = 2
      ctx.setLineDash([7, 5])
      ctx.beginPath()
      const startPixel = mapToPixel(map, form.startX, form.startY)
      const startScreen = mapPixelToScreen(startPixel.x, startPixel.y)
      ctx.moveTo(startScreen.x, startScreen.y)
      for (const target of targets.value) {
        const pixel = mapToPixel(map, target.x, target.y)
        const screen = mapPixelToScreen(pixel.x, pixel.y)
        ctx.lineTo(screen.x, screen.y)
      }
      if (form.returnToStart) ctx.lineTo(startScreen.x, startScreen.y)
      ctx.stroke()
      ctx.setLineDash([])
    }

    for (const point of allPoints) {
      const pixel = mapToPixel(map, point.x, point.y)
      const screen = mapPixelToScreen(pixel.x, pixel.y)
      const isStart = point.kind === 'start'
      const selectedForYaw =
        yawTarget.value &&
        yawTarget.value.kind === point.kind &&
        (isStart || yawTarget.value.id === (point as RouteExecutorTarget & { kind: 'target' }).id)
      const selectedTarget = !isStart && selectedTargetId.value === (point as RouteExecutorTarget & { order: number }).id
      const footprintKind = drawFootprint(ctx, point, isStart ? '#0f766e' : '#2563eb')
      const blocked = footprintKind === 'outside' || footprintKind === 'occupied' || footprintKind === 'keepout'
      const unknown = footprintKind === 'unknown'
      const color = blocked ? '#b42318' : unknown ? '#b54708' : isStart ? '#0f766e' : '#2563eb'
      const radius = isStart ? 8 : 7
      if (selectedForYaw || selectedTarget) {
        ctx.strokeStyle = selectedForYaw ? '#f59e0b' : '#0f766e'
        ctx.lineWidth = 3
        ctx.beginPath()
        ctx.arc(screen.x, screen.y, radius + 5, 0, Math.PI * 2)
        ctx.stroke()
      }
      ctx.fillStyle = color
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 2.5
      ctx.beginPath()
      ctx.arc(screen.x, screen.y, radius, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      drawArrowAtMapPixel(ctx, pixel.x, pixel.y, point.yaw || 0, color)
      if (!isStart && 'order' in point) {
        ctx.fillStyle = '#ffffff'
        ctx.font = '700 10px Microsoft YaHei, sans-serif'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText(String(point.order), screen.x, screen.y)
        ctx.textAlign = 'left'
        ctx.textBaseline = 'alphabetic'
      }
      ctx.fillStyle = blocked ? '#b42318' : unknown ? '#b54708' : '#111827'
      ctx.font = '12px Microsoft YaHei, sans-serif'
      const label = isStart ? '起点' : `#${(point as { order: number; name: string }).order} ${point.name}`
      ctx.fillText(label, screen.x + 10, screen.y - 8)
    }

    if ((mode.value === 'start' || mode.value === 'target') && hoverPixel.value && !drag.value) {
      const position = pixelToMap(map, hoverPixel.value.x, hoverPixel.value.y)
      drawFootprint(ctx, { x: position.x, y: position.y, yaw: mode.value === 'start' ? form.startYaw : 0 }, mode.value === 'start' ? '#0f766e' : '#2563eb', 0.1)
    }

    if (mode.value === 'yaw' && yawPreview.value) {
      const hit = getSelectedYawHit()
      let originPixel
      if (hit.kind === 'start') {
        originPixel = mapToPixel(map, form.startX, form.startY)
      } else {
        const target = targets.value.find((item) => item.id === hit.id)
        if (target) originPixel = mapToPixel(map, target.x, target.y)
      }
      if (originPixel && yawPreview.value) {
        const pose = hit.kind === 'start'
          ? { x: form.startX, y: form.startY, yaw: round3(-Math.atan2(yawPreview.value.y - originPixel.y, yawPreview.value.x - originPixel.x)) }
          : targets.value.find((item) => item.id === hit.id)
        if (pose) {
          const nextPose = 'id' in pose
            ? { x: pose.x, y: pose.y, yaw: round3(-Math.atan2(yawPreview.value.y - originPixel.y, yawPreview.value.x - originPixel.x)) }
            : pose
          drawFootprint(ctx, nextPose, '#f59e0b', 0.1)
        }
        const originScreen = mapPixelToScreen(originPixel.x, originPixel.y)
        const previewScreen = mapPixelToScreen(yawPreview.value.x, yawPreview.value.y)
        ctx.strokeStyle = '#f59e0b'
        ctx.lineWidth = 2
        ctx.setLineDash([6, 4])
        ctx.beginPath()
        ctx.moveTo(originScreen.x, originScreen.y)
        ctx.lineTo(previewScreen.x, previewScreen.y)
        ctx.stroke()
        ctx.setLineDash([])
      }
    }
  }

  function draw() {
    const canvas = canvasRef.value
    const ctx = getCtx()
    if (!canvas || !ctx) return
    const rect = canvas.getBoundingClientRect()
    ctx.clearRect(0, 0, rect.width, rect.height)
    ctx.fillStyle = '#eef2f6'
    ctx.fillRect(0, 0, rect.width, rect.height)

    if (mapBitmapCtx && map.width && map.height) {
      ctx.save()
      ctx.translate(view.offsetX, view.offsetY)
      ctx.scale(view.scaleX, view.scaleY)
      ctx.imageSmoothingEnabled = false
      ctx.drawImage(mapBitmapCanvas, 0, 0)
      ctx.restore()
    }

    drawMapOverlays(ctx)
    updateMapInfoText()
  }

  function resizeCanvas() {
    const wrap = wrapRef.value
    const canvas = canvasRef.value
    if (!wrap || !canvas) return
    const rect = wrap.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) return
    const ratio = window.devicePixelRatio || 1
    const cssWidth = Math.max(1, Math.floor(rect.width))
    const cssHeight = Math.max(1, Math.floor(rect.height))
    canvas.style.width = `${cssWidth}px`
    canvas.style.height = `${cssHeight}px`
    canvas.width = Math.max(1, Math.floor(cssWidth * ratio))
    canvas.height = Math.max(1, Math.floor(cssHeight * ratio))
    const ctx = getCtx()
    ctx?.setTransform(ratio, 0, 0, ratio, 0, 0)
    draw()
  }

  function fitToScreen() {
    const wrap = wrapRef.value
    if (!wrap || !map.width || !map.height) {
      resizeCanvas()
      return
    }
    const rect = wrap.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) return
    resizeCanvas()
    const filled = calculateAspectFillView(rect.width, rect.height, map.width, map.height)
    view.scaleX = filled.scale
    view.scaleY = filled.scale
    view.offsetX = filled.offsetX
    view.offsetY = filled.offsetY
    draw()
  }

  function fitMapToScreen() {
    const wrap = wrapRef.value
    if (!wrap || !map.width || !map.height) {
      resizeCanvas()
      return
    }
    const rect = wrap.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) return
    resizeCanvas()
    const fitted = calculateAspectFitView(rect.width, rect.height, map.width, map.height)
    view.scaleX = fitted.scale
    view.scaleY = fitted.scale
    view.offsetX = fitted.offsetX
    view.offsetY = fitted.offsetY
    draw()
  }

  let fitFrameId = 0
  function scheduleMapFitToScreen() {
    fitFrameId += 1
    const frame = fitFrameId
    requestAnimationFrame(() => {
      if (frame !== fitFrameId) return
      fitMapToScreen()
    })
  }

  function setMode(next: EditorMode) {
    mode.value = next
    yawPreview.value = null
    hoverPixel.value = null
    draw()
  }

  function getPointHit(px: number, py: number): PointHit | null {
    const threshold = 12 / viewUiScale()
    if (mode.value === 'keepout') {
      for (const zone of keepoutZones.value) {
        for (let index = 0; index < zone.polygon.length; index += 1) {
          const point = mapToPixel(map, zone.polygon[index].x, zone.polygon[index].y)
          if (Math.hypot(px - point.x, py - point.y) <= threshold) return { kind: 'zonePoint', id: zone.id, index }
        }
      }
    }
    const startP = mapToPixel(map, form.startX, form.startY)
    if (Math.hypot(px - startP.x, py - startP.y) <= threshold) return { kind: 'start' }
    for (const target of targets.value) {
      const p = mapToPixel(map, target.x, target.y)
      if (Math.hypot(px - p.x, py - p.y) <= threshold) return { kind: 'target', id: target.id }
    }
    return null
  }

  function pointHitToYaw(hit: PointHit): YawTarget | null {
    if (hit.kind === 'target') return { kind: 'target', id: hit.id }
    if (hit.kind === 'start') return { kind: 'start', id: null }
    return null
  }

  function setStartFromPixel(px: number, py: number) {
    const pos = pixelToMap(map, px, py)
    form.startX = pos.x
    form.startY = pos.y
    emitChange()
    draw()
  }

  function addTargetFromPixel(px: number, py: number) {
    const pos = pixelToMap(map, px, py)
    const no = nextTargetNo.value
    nextTargetNo.value += 1
    const id = `target_${String(no).padStart(3, '0')}`
    targets.value.push({
      id,
      name: `巡检点${no}`,
      x: pos.x,
      y: pos.y,
      yaw: 0,
      taskDuration: 5,
    })
    selectedTargetId.value = id
    yawTarget.value = { kind: 'target', id }
    emitChange()
    draw()
  }

  function activeZone() {
    return keepoutZones.value.find((zone) => zone.id === activeZoneId.value) ?? null
  }

  function nextZoneId() {
    const used = new Set(keepoutZones.value.map((zone) => zone.id))
    let number = keepoutZones.value.length + 1
    while (used.has(`keepout_${String(number).padStart(3, '0')}`)) number += 1
    return `keepout_${String(number).padStart(3, '0')}`
  }

  function addKeepoutZone() {
    const id = nextZoneId()
    keepoutZones.value.push({ id, name: `禁行区${keepoutZones.value.length + 1}`, type: 'hard_keepout', enabled: true, maskPaddingM: map.resolution, polygon: [] })
    activeZoneId.value = id
    setMode('keepout')
    emitChange()
    draw()
  }

  function addZonePointFromPixel(px: number, py: number) {
    if (!activeZoneId.value) addKeepoutZone()
    const zone = activeZone()
    if (!zone) return
    zone.polygon.push(pixelToMap(map, px, py))
    emitChange()
    draw()
  }

  function selectZone(id: string) {
    if (!keepoutZones.value.some((zone) => zone.id === id)) return
    activeZoneId.value = id
    setMode('keepout')
  }

  function deleteActiveZone() {
    if (!activeZoneId.value) return
    keepoutZones.value = keepoutZones.value.filter((zone) => zone.id !== activeZoneId.value)
    activeZoneId.value = keepoutZones.value[0]?.id ?? null
    emitChange()
    draw()
  }

  function deleteLastZonePoint() {
    const zone = activeZone()
    if (!zone) return
    zone.polygon.pop()
    emitChange()
    draw()
  }

  function clearZonePoints() {
    const zone = activeZone()
    if (!zone) return
    zone.polygon = []
    emitChange()
    draw()
  }

  function updateZoneField(id: string, field: 'name' | 'id' | 'enabled' | 'maskPaddingM', value: string | number | boolean) {
    const zone = keepoutZones.value.find((item) => item.id === id)
    if (!zone) return
    if (field === 'id') {
      const nextId = String(value).trim()
      if (!nextId || keepoutZones.value.some((item) => item.id === nextId && item !== zone)) throw new Error('禁行区 ID 必须非空且唯一')
      if (activeZoneId.value === zone.id) activeZoneId.value = nextId
      zone.id = nextId
    } else if (field === 'name') zone.name = String(value)
    else if (field === 'enabled') zone.enabled = Boolean(value)
    else zone.maskPaddingM = Math.min(map.resolution, Math.max(0, Number(value) || 0))
    emitChange()
    draw()
  }

  function updateZonePoint(id: string, index: number, axis: 'x' | 'y', value: number) {
    const point = keepoutZones.value.find((zone) => zone.id === id)?.polygon[index]
    if (!point) return
    point[axis] = Number(value)
    emitChange()
    draw()
  }

  function setYawFromPointer(hit: YawTarget, px: number, py: number) {
    let origin
    if (hit.kind === 'start') {
      origin = mapToPixel(map, form.startX, form.startY)
      yawTarget.value = { kind: 'start', id: null }
    } else {
      const target = targets.value.find((item) => item.id === hit.id)
      if (!target) return
      origin = mapToPixel(map, target.x, target.y)
      selectedTargetId.value = target.id
      yawTarget.value = { kind: 'target', id: target.id }
    }
    const yaw = round3(-Math.atan2(py - origin.y, px - origin.x))
    if (hit.kind === 'start') {
      form.startYaw = yaw
    } else {
      const target = targets.value.find((item) => item.id === hit.id)
      if (target) target.yaw = yaw
    }
    emitChange()
    draw()
  }

  function moveTarget(id: string, delta: number) {
    const index = targets.value.findIndex((target) => target.id === id)
    const next = index + delta
    if (index < 0 || next < 0 || next >= targets.value.length) return
    const [item] = targets.value.splice(index, 1)
    targets.value.splice(next, 0, item)
    emitChange()
    draw()
  }

  function deleteTarget(id: string) {
    targets.value = targets.value.filter((target) => target.id !== id)
    if (selectedTargetId.value === id) selectedTargetId.value = targets.value[0]?.id ?? null
    if (yawTarget.value?.id === id) {
      yawTarget.value = selectedTargetId.value
        ? { kind: 'target', id: selectedTargetId.value }
        : { kind: 'start', id: null }
    }
    emitChange()
    draw()
  }

  function clearTargets() {
    targets.value = []
    selectedTargetId.value = null
    yawTarget.value = { kind: 'start', id: null }
    emitChange()
    draw()
  }

  function clearRouteAnnotations() {
    targets.value = []
    keepoutZones.value = []
    activeZoneId.value = null
    selectedTargetId.value = null
    yawTarget.value = { kind: 'start', id: null }
    nextTargetNo.value = 1
    sourceTemplate.value = null
    emitChange()
    draw()
  }

  function addTargetAtCenter() {
    const wrap = wrapRef.value
    if (!wrap) return
    const rect = wrap.getBoundingClientRect()
    const px = (rect.width / 2 - view.offsetX) / view.scaleX
    const py = (rect.height / 2 - view.offsetY) / view.scaleY
    addTargetFromPixel(px, py)
  }

  function applyYamlText(text: string, fileName?: string) {
    const patch = parseYaml(text)
    const resolution = patch.resolution
    if (typeof resolution !== 'number' || !Number.isFinite(resolution) || resolution <= 0) {
      throw new Error('地图 YAML 缺少有效 resolution，已停止加载以避免沿用旧地图参数。')
    }
    if (!patch.origin || patch.origin.length !== 3 || !patch.origin.every(Number.isFinite)) {
      throw new Error('地图 YAML 缺少有效 origin，必须包含 x、y、yaw 三个有限数。')
    }
    if (!patch.image?.trim()) throw new Error('地图 YAML 缺少 image，无法建立路线与地图的身份绑定。')
    const keepsExistingPgm =
      map.pgmName &&
      patch.image &&
      rosMapImageFileName(patch.image).toLowerCase() === rosMapImageFileName(map.pgmName).toLowerCase()
    if (!keepsExistingPgm) {
      map.width = 0
      map.height = 0
      map.pixels = null
      map.pgmName = ''
    }
    Object.assign(map, patch)
    if (fileName) map.yamlName = fileName
    mapInfoDirty = true
    if (map.pixels) {
      mapBitmapCtx = mapBitmapCanvas.getContext('2d')
      rebuildMapBitmap(map, mapBitmapCanvas)
      scheduleMapFitToScreen()
    }
    emitChange()
    draw()
  }

  function applyPgmBuffer(buffer: ArrayBuffer, fileName?: string) {
    if (
      fileName &&
      map.yamlName &&
      rosMapImageFileName(map.image).toLowerCase() !== rosMapImageFileName(fileName).toLowerCase()
    ) {
      throw new Error(`YAML 引用 ${map.image}，请选择对应的 PGM 文件。`)
    }
    if (fileName) map.pgmName = fileName
    mapInfoDirty = true
    const parsed = parsePgm(buffer)
    map.width = parsed.width
    map.height = parsed.height
    map.pixels = parsed.pixels
    mapBitmapCtx = mapBitmapCanvas.getContext('2d')
    rebuildMapBitmap(map, mapBitmapCanvas)
    fitMapToScreen()
    scheduleMapFitToScreen()
    emitChange()
  }

  function validateImportedMapIdentity(document: RouteExecutorDocument) {
    if (document.version !== 3) return
    const routeMap = document.map
    const valid = routeMap && routeMap.yaml && routeMap.image && Number.isFinite(routeMap.resolution) && routeMap.resolution > 0
      && Array.isArray(routeMap.origin) && routeMap.origin.length === 3 && routeMap.origin.every(Number.isFinite)
      && Number.isInteger(routeMap.width) && routeMap.width > 0 && Number.isInteger(routeMap.height) && routeMap.height > 0
      && /^[0-9a-f]{64}$/.test(routeMap.image_sha256)
    if (!valid) throw new Error('v3 路线缺少有效地图身份。')
    if (!map.width || !map.height || !map.pixels) return
    const currentHash = mapIdentity.value.image_sha256
    const sameMap = routeMap.yaml === map.yamlName
      && routeMap.image === map.image
      && rosMapImageFileName(map.image).toLowerCase() === rosMapImageFileName(map.pgmName).toLowerCase()
      && routeMap.resolution === map.resolution
      && routeMap.width === map.width
      && routeMap.height === map.height
      && routeMap.origin.every((value, index) => value === map.origin[index])
      && /^[0-9a-f]{64}$/.test(currentHash)
      && routeMap.image_sha256 === currentHash
    if (!sameMap) throw new Error('导入路线不属于当前地图。请加载其对应 YAML/PGM，或在当前地图重新标注起点、巡检点、路线和禁行区。')
  }

  function importRouteJson(input: unknown, emit = true) {
    const doc = parseRouteDocument(input)
    validateImportedMapIdentity(doc)
    const loaded = loadRouteJson(doc, form)
    sourceTemplate.value = doc
    targets.value = loaded.targets
    keepoutZones.value = loaded.keepoutZones
    activeZoneId.value = keepoutZones.value[0]?.id ?? null
    nextTargetNo.value = loaded.nextTargetNo
    selectedTargetId.value = targets.value[0]?.id ?? null
    yawTarget.value = selectedTargetId.value
      ? { kind: 'target', id: selectedTargetId.value }
      : { kind: 'start', id: null }
    activeRouteIdSynced.value = true
    if (emit) emitChange()
    if (map.width && map.height) scheduleMapFitToScreen()
    else draw()
  }

  function exportDocument(): RouteExecutorDocument {
    const document = buildRouteJson(form, targets.value, keepoutZones.value, mapIdentity.value, sourceTemplate.value)
    return applyRouteSafety(document, routeSafety.value)
  }

  function validateForExport() {
    return annotationProblems.value
  }

  function setMapAssetIdentity(identity: MapAssetIdentity) {
    mapIdentity.value = identity
    emitChange()
  }

  function emitChange() {
    triggerRef(targets)
    triggerRef(keepoutZones)
    options?.onChange?.(exportDocument())
  }

  function onFormFieldChange(field: keyof RouteFormState, value: unknown) {
    ;(form as Record<string, unknown>)[field] = value
    if (field === 'activeRouteId') activeRouteIdSynced.value = false
    if (field === 'routeId' && activeRouteIdSynced.value) {
      form.activeRouteId = String(value)
    }
    if (field === 'routeId' || field === 'activeRouteId') {
      if (form.activeRouteId.trim() === '' || activeRouteIdSynced.value) {
        form.activeRouteId = form.routeId
        activeRouteIdSynced.value = true
      }
    }
    emitChange()
    draw()
  }

  function selectTarget(id: string) {
    selectedTargetId.value = id
    yawTarget.value = { kind: 'target', id }
    draw()
  }

  function orientTarget(id: string) {
    selectedTargetId.value = id
    yawTarget.value = { kind: 'target', id }
    setMode('yaw')
  }

  function updateTargetField(id: string, field: keyof RouteExecutorTarget, value: string | number) {
    const target = targets.value.find((item) => item.id === id)
    if (!target) return
    if (field === 'name') target.name = String(value)
    else if (field === 'x') target.x = Number(value)
    else if (field === 'y') target.y = Number(value)
    else if (field === 'yaw') target.yaw = Number(value)
    else if (field === 'taskDuration') target.taskDuration = Number(value)
    emitChange()
    draw()
  }

  function onMouseDown(event: MouseEvent) {
    const p = canvasToPixel(event.clientX, event.clientY)
    const hit = getPointHit(p.x, p.y)
    if (event.button === 2) {
      if (hit?.kind === 'zonePoint') return
      const yawHit = hit ? pointHitToYaw(hit) : getSelectedYawHit()
      if (yawHit) setYawFromPointer(yawHit, p.x, p.y)
      return
    }
    if (mode.value === 'yaw') {
      if (hit?.kind === 'zonePoint') return
      yawPreview.value = p
      const yawHit = hit ? pointHitToYaw(hit) : getSelectedYawHit()
      if (!yawHit) return
      if (hit?.kind === 'target') selectedTargetId.value = hit.id
      if (hit) {
        yawTarget.value = yawHit
        drag.value = { type: 'yaw', hit: yawHit, moved: false }
      } else {
        setYawFromPointer(yawHit, p.x, p.y)
        drag.value = { type: 'yaw', hit: yawHit, moved: true }
      }
      return
    }
    if (hit?.kind === 'zonePoint') {
      activeZoneId.value = hit.id
      drag.value = { type: 'zonePoint', id: hit.id, index: hit.index }
      draw()
      return
    }
    if (hit && mode.value !== 'pan' && mode.value !== 'keepout') {
      drag.value = hit.kind === 'target' ? { type: 'target', id: hit.id } : { type: 'start' }
      if (hit.kind === 'target') {
        selectedTargetId.value = hit.id
        yawTarget.value = { kind: 'target', id: hit.id }
      } else {
        yawTarget.value = { kind: 'start', id: null }
      }
      return
    }
    if (mode.value === 'pan') {
      drag.value = {
        type: 'pan',
        startX: event.clientX,
        startY: event.clientY,
        ox: view.offsetX,
        oy: view.offsetY,
      }
    } else if (mode.value === 'start') {
      setStartFromPixel(p.x, p.y)
    } else if (mode.value === 'target') {
      addTargetFromPixel(p.x, p.y)
    } else if (mode.value === 'keepout') {
      addZonePointFromPixel(p.x, p.y)
    }
  }

  function onMouseMove(event: MouseEvent) {
    const p = canvasToPixel(event.clientX, event.clientY)
    hoverPixel.value = p
    if (map.width) {
      const pos = pixelToMap(map, p.x, p.y)
      const kind = mode.value === 'start' || mode.value === 'target'
        ? classifyFootprint(map, keepoutZones.value, { x: pos.x, y: pos.y, yaw: mode.value === 'start' ? form.startYaw : 0 })
        : classifyMapCoordinate(map, pos.x, pos.y)
      cursorInfo.value = `map: x=${pos.x}, y=${pos.y} | ${describeMapClass(kind)}`
    }
    if (mode.value === 'yaw' || mode.value === 'start' || mode.value === 'target') {
      yawPreview.value = p
      draw()
    }
    if (!drag.value) return
    if (drag.value.type === 'pan') {
      view.offsetX = drag.value.ox + event.clientX - drag.value.startX
      view.offsetY = drag.value.oy + event.clientY - drag.value.startY
      draw()
    } else if (drag.value.type === 'start') {
      setStartFromPixel(p.x, p.y)
    } else if (drag.value.type === 'target') {
      const targetId = drag.value.id
      const target = targets.value.find((item) => item.id === targetId)
      if (!target) return
      const pos = pixelToMap(map, p.x, p.y)
      target.x = pos.x
      target.y = pos.y
      emitChange()
      draw()
    } else if (drag.value.type === 'zonePoint') {
      const zoneDrag = drag.value
      const zone = keepoutZones.value.find((item) => item.id === zoneDrag.id)
      const index = zoneDrag.index
      if (!zone?.polygon[index]) return
      zone.polygon[index] = pixelToMap(map, p.x, p.y)
      emitChange()
      draw()
    } else if (drag.value.type === 'yaw') {
      drag.value.moved = true
      setYawFromPointer(drag.value.hit, p.x, p.y)
    }
  }

  function onMouseUp() {
    drag.value = null
    yawPreview.value = null
    draw()
  }

  function onMouseLeave() {
    hoverPixel.value = null
    if (!drag.value) yawPreview.value = null
    draw()
  }

  function onWheel(event: WheelEvent) {
    event.preventDefault()
    const canvas = canvasRef.value
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const before = canvasToPixel(event.clientX, event.clientY)
    const factor = event.deltaY < 0 ? 1.15 : 0.87
    view.scaleX = Math.min(20, Math.max(0.04, view.scaleX * factor))
    view.scaleY = Math.min(20, Math.max(0.04, view.scaleY * factor))
    const cx = event.clientX - rect.left
    const cy = event.clientY - rect.top
    view.offsetX = cx - before.x * view.scaleX
    view.offsetY = cy - before.y * view.scaleY
    draw()
  }

  function zoomIn() {
    view.scaleX *= 1.2
    view.scaleY *= 1.2
    draw()
  }

  function zoomOut() {
    view.scaleX /= 1.2
    view.scaleY /= 1.2
    draw()
  }

  async function handleDroppedFiles(files: FileList | File[]) {
    const selected = Array.from(files)
    const yamlFiles = selected.filter((file) => /\.ya?ml$/i.test(file.name))
    const pgmFiles = selected.filter((file) => /\.pgm$/i.test(file.name))
    if (yamlFiles.length > 1) throw new Error('一次只能导入一个 YAML 地图配置。')
    if (yamlFiles[0]) applyYamlText(await yamlFiles[0].text(), yamlFiles[0].name)

    if (pgmFiles.length) {
      const expectedName = rosMapImageFileName(map.image).toLowerCase()
      const matches = map.yamlName
        ? pgmFiles.filter((file) => rosMapImageFileName(file.name).toLowerCase() === expectedName)
        : pgmFiles
      if (matches.length !== 1) {
        throw new Error(
          map.yamlName
            ? `请选择 YAML 中 image 指向的 ${map.image}。`
            : '一次只能导入一个 PGM 地图图像。',
        )
      }
      applyPgmBuffer(await matches[0].arrayBuffer(), matches[0].name)
    }

    for (const file of selected.filter((item) => /\.json$/i.test(item.name))) {
      importRouteJson(JSON.parse(await file.text()))
    }
  }

  watch(
    () => options?.initialJson?.(),
    (doc) => {
      if (doc) importRouteJson(doc, false)
    },
    { immediate: true },
  )

  watch([showFootprint, showFootprintPadding], draw)

  function onViewportResize() {
    if (map.width && map.height) fitMapToScreen()
    else resizeCanvas()
  }

  onMounted(() => {
    onViewportResize()
    if (typeof ResizeObserver !== 'undefined' && wrapRef.value) {
      wrapResizeObserver = new ResizeObserver(() => onViewportResize())
      wrapResizeObserver.observe(wrapRef.value)
    }
    window.addEventListener('resize', onViewportResize)
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  })

  onUnmounted(() => {
    wrapResizeObserver?.disconnect()
    wrapResizeObserver = null
    window.removeEventListener('resize', onViewportResize)
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  })

  return {
    map,
    form,
    mode,
    targets,
    keepoutZones,
    activeZoneId,
    selectedTargetId,
    cursorInfo,
    mapInfo,
    jsonPreview,
    isLegacyDraft,
    targetStatus,
    zoneStatus,
    showFootprint,
    showFootprintPadding,
    setMode,
    fitToScreen,
    fitMapToScreen,
    zoomIn,
    zoomOut,
    applyYamlText,
    applyPgmBuffer,
    importRouteJson,
    exportDocument,
    validateForExport,
    setMapAssetIdentity,
    onFormFieldChange,
    selectTarget,
    orientTarget,
    updateTargetField,
    moveTarget,
    deleteTarget,
    clearTargets,
    clearRouteAnnotations,
    addTargetAtCenter,
    addKeepoutZone,
    selectZone,
    deleteActiveZone,
    deleteLastZonePoint,
    clearZonePoints,
    updateZoneField,
    updateZonePoint,
    handleDroppedFiles,
    onMouseDown,
    onMouseLeave,
    onWheel,
    resizeCanvas,
    draw,
  }
}
