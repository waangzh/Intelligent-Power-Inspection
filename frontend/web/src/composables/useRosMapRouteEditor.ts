import { computed, onMounted, onUnmounted, reactive, ref, shallowRef, watch, type Ref } from 'vue'
import type { EditorMode, RouteExecutorDocument, RosMapState } from '@/types/routeExecutor'
import {
  createDefaultMapState,
  isMapCoordinateInside,
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

type YawTarget = { kind: 'start'; id: null } | { kind: 'target'; id: string }
type PointHit = { kind: 'start' } | { kind: 'target'; id: string }
type DragState =
  | { type: 'pan'; startX: number; startY: number; ox: number; oy: number }
  | { type: 'start' }
  | { type: 'target'; id: string }
  | { type: 'yaw'; hit: YawTarget; moved: boolean }

export function useRosMapRouteEditor(
  canvasRef: Ref<HTMLCanvasElement | null>,
  wrapRef: Ref<HTMLElement | null>,
  options?: {
    initialJson?: RouteExecutorDocument | null
    defaultRouteId?: string
    onChange?: (doc: RouteExecutorDocument) => void
  },
) {
  const map = reactive<RosMapState>(createDefaultMapState())
  const view = reactive({ scale: 1, offsetX: 0, offsetY: 0 })
  const mode = ref<EditorMode>('start')
  const targets = shallowRef<RouteExecutorTarget[]>([])
  const selectedTargetId = ref<string | null>(null)
  const yawTarget = ref<YawTarget>({ kind: 'start', id: null })
  const yawPreview = ref<{ x: number; y: number } | null>(null)
  const drag = ref<DragState | null>(null)
  const nextTargetNo = ref(1)
  const sourceTemplate = shallowRef<RouteExecutorDocument | null>(null)
  const form = reactive<RouteFormState>(createDefaultRouteForm(options?.defaultRouteId))
  const activeRouteIdSynced = ref(true)
  const cursorInfo = ref('map: -, -')
  const mapInfo = ref('等待加载地图')

  const mapBitmapCanvas = document.createElement('canvas')
  let mapBitmapCtx = mapBitmapCanvas.getContext('2d')

  const jsonPreview = computed<string>(() => JSON.stringify(exportDocument() as unknown, null, 2))

  const outOfBounds = computed(() => {
    const items: string[] = []
    if (!isMapCoordinateInside(map, form.startX, form.startY)) items.push('起点')
    targets.value.forEach((target, index) => {
      if (!isMapCoordinateInside(map, target.x, target.y)) items.push(`#${index + 1}`)
    })
    return items
  })

  const targetStatus = computed(() => {
    if (outOfBounds.value.length) {
      return {
        text: `越界提示：${outOfBounds.value.join('、')} 不在当前地图范围内，请换回对应地图或重新标定。`,
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

  function canvasToPixel(clientX: number, clientY: number) {
    const canvas = canvasRef.value
    if (!canvas) return { x: 0, y: 0 }
    const rect = canvas.getBoundingClientRect()
    const x = (clientX - rect.left - view.offsetX) / view.scale
    const y = (clientY - rect.top - view.offsetY) / view.scale
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
    const files = [map.yamlName, map.pgmName || map.image].filter(Boolean).join(' + ')
    mapInfo.value = `${files} | ${map.width}x${map.height}px | ${wMeters}m x ${hMeters}m | res=${map.resolution}`
  }

  function drawArrow(ctx: CanvasRenderingContext2D, x: number, y: number, yaw: number, color: string) {
    const len = 24
    const angle = -yaw
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(angle)
    ctx.strokeStyle = color
    ctx.fillStyle = color
    ctx.lineWidth = 2 / view.scale
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(len / view.scale, 0)
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(len / view.scale, 0)
    ctx.lineTo((len - 7) / view.scale, -5 / view.scale)
    ctx.lineTo((len - 7) / view.scale, 5 / view.scale)
    ctx.closePath()
    ctx.fill()
    ctx.restore()
  }

  function draw() {
    const canvas = canvasRef.value
    const ctx = getCtx()
    if (!canvas || !ctx) return
    const rect = canvas.getBoundingClientRect()
    ctx.clearRect(0, 0, rect.width, rect.height)
    ctx.save()
    ctx.translate(view.offsetX, view.offsetY)
    ctx.scale(view.scale, view.scale)

    if (mapBitmapCtx && map.width && map.height) {
      ctx.imageSmoothingEnabled = false
      ctx.drawImage(mapBitmapCanvas, 0, 0)
    } else {
      ctx.fillStyle = '#f8fafc'
      ctx.fillRect(0, 0, Math.max(1, rect.width), Math.max(1, rect.height))
    }

    const allPoints = [
      { kind: 'start' as const, x: form.startX, y: form.startY, yaw: form.startYaw },
      ...targets.value.map((target, index) => ({ kind: 'target' as const, order: index + 1, ...target })),
    ]

    if (targets.value.length > 0) {
      ctx.strokeStyle = '#2563eb'
      ctx.lineWidth = 2 / view.scale
      ctx.setLineDash([7 / view.scale, 5 / view.scale])
      ctx.beginPath()
      const startPx = mapToPixel(map, form.startX, form.startY)
      ctx.moveTo(startPx.x, startPx.y)
      for (const target of targets.value) {
        const p = mapToPixel(map, target.x, target.y)
        ctx.lineTo(p.x, p.y)
      }
      if (form.returnToStart) ctx.lineTo(startPx.x, startPx.y)
      ctx.stroke()
      ctx.setLineDash([])
    }

    for (const point of allPoints) {
      const p = mapToPixel(map, point.x, point.y)
      const isStart = point.kind === 'start'
      const selectedForYaw =
        yawTarget.value &&
        yawTarget.value.kind === point.kind &&
        (isStart || yawTarget.value.id === (point as RouteExecutorTarget & { kind: 'target' }).id)
      const selectedTarget = !isStart && selectedTargetId.value === (point as RouteExecutorTarget & { order: number }).id
      const inside = isMapCoordinateInside(map, point.x, point.y)
      const color = inside ? (isStart ? '#0f766e' : '#2563eb') : '#b42318'
      const radius = isStart ? 8 / view.scale : 7 / view.scale
      if (selectedForYaw || selectedTarget) {
        ctx.strokeStyle = selectedForYaw ? '#f59e0b' : '#0f766e'
        ctx.lineWidth = 3 / view.scale
        ctx.beginPath()
        ctx.arc(p.x, p.y, radius + 5 / view.scale, 0, Math.PI * 2)
        ctx.stroke()
      }
      ctx.fillStyle = color
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 2.5 / view.scale
      ctx.beginPath()
      ctx.arc(p.x, p.y, radius, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      drawArrow(ctx, p.x, p.y, point.yaw || 0, color)
      if (!isStart && 'order' in point) {
        ctx.fillStyle = '#ffffff'
        ctx.font = `700 ${10 / view.scale}px Microsoft YaHei, sans-serif`
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText(String(point.order), p.x, p.y)
        ctx.textAlign = 'left'
        ctx.textBaseline = 'alphabetic'
      }
      ctx.fillStyle = inside ? '#111827' : '#b42318'
      ctx.font = `${12 / view.scale}px Microsoft YaHei, sans-serif`
      const label = isStart ? '起点' : `#${(point as { order: number; name: string }).order} ${point.name}`
      ctx.fillText(label, p.x + 10 / view.scale, p.y - 8 / view.scale)
    }

    if (mode.value === 'yaw' && yawPreview.value) {
      const hit = getSelectedYawHit()
      let origin
      if (hit.kind === 'start') {
        origin = mapToPixel(map, form.startX, form.startY)
      } else {
        const target = targets.value.find((item) => item.id === hit.id)
        if (target) origin = mapToPixel(map, target.x, target.y)
      }
      if (origin && yawPreview.value) {
        ctx.strokeStyle = '#f59e0b'
        ctx.lineWidth = 2 / view.scale
        ctx.setLineDash([6 / view.scale, 4 / view.scale])
        ctx.beginPath()
        ctx.moveTo(origin.x, origin.y)
        ctx.lineTo(yawPreview.value.x, yawPreview.value.y)
        ctx.stroke()
        ctx.setLineDash([])
      }
    }

    ctx.restore()
    updateMapInfoText()
  }

  function resizeCanvas() {
    const wrap = wrapRef.value
    const canvas = canvasRef.value
    if (!wrap || !canvas) return
    const rect = wrap.getBoundingClientRect()
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.max(1, Math.floor(rect.width * ratio))
    canvas.height = Math.max(1, Math.floor(rect.height * ratio))
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
    const scaleX = rect.width / map.width
    const scaleY = rect.height / map.height
    view.scale = Math.max(0.05, Math.min(scaleX, scaleY) * 0.96)
    view.offsetX = (rect.width - map.width * view.scale) / 2
    view.offsetY = (rect.height - map.height * view.scale) / 2
    draw()
  }

  function setMode(next: EditorMode) {
    mode.value = next
    yawPreview.value = null
    draw()
  }

  function getPointHit(px: number, py: number): PointHit | null {
    const threshold = 12 / view.scale
    const startP = mapToPixel(map, form.startX, form.startY)
    if (Math.hypot(px - startP.x, py - startP.y) <= threshold) return { kind: 'start' }
    for (const target of targets.value) {
      const p = mapToPixel(map, target.x, target.y)
      if (Math.hypot(px - p.x, py - p.y) <= threshold) return { kind: 'target', id: target.id }
    }
    return null
  }

  function pointHitToYaw(hit: PointHit): YawTarget {
    return hit.kind === 'start' ? { kind: 'start', id: null } : { kind: 'target', id: hit.id }
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

  function addTargetAtCenter() {
    const wrap = wrapRef.value
    if (!wrap) return
    const rect = wrap.getBoundingClientRect()
    const px = (rect.width / 2 - view.offsetX) / view.scale
    const py = (rect.height / 2 - view.offsetY) / view.scale
    addTargetFromPixel(px, py)
  }

  function applyYamlText(text: string, fileName?: string) {
    const patch = parseYaml(text)
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
    fitToScreen()
    emitChange()
  }

  function importRouteJson(input: unknown) {
    const doc = parseRouteDocument(input)
    const loaded = loadRouteJson(doc, form)
    sourceTemplate.value = doc
    targets.value = loaded.targets
    nextTargetNo.value = loaded.nextTargetNo
    selectedTargetId.value = targets.value[0]?.id ?? null
    yawTarget.value = selectedTargetId.value
      ? { kind: 'target', id: selectedTargetId.value }
      : { kind: 'start', id: null }
    activeRouteIdSynced.value = true
    emitChange()
    draw()
  }

  function exportDocument(): RouteExecutorDocument {
    return buildRouteJson(form, targets.value, sourceTemplate.value)
  }

  function emitChange() {
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
      setYawFromPointer(hit ? pointHitToYaw(hit) : getSelectedYawHit(), p.x, p.y)
      return
    }
    if (mode.value === 'yaw') {
      yawPreview.value = p
      const yawHit: YawTarget = hit ? pointHitToYaw(hit) : getSelectedYawHit()
      if (hit?.kind === 'target') selectedTargetId.value = hit.id
      if (hit) {
        yawTarget.value = pointHitToYaw(hit)
        drag.value = { type: 'yaw', hit: yawHit, moved: false }
      } else {
        setYawFromPointer(yawHit, p.x, p.y)
        drag.value = { type: 'yaw', hit: yawHit, moved: true }
      }
      return
    }
    if (hit && mode.value !== 'pan') {
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
    }
  }

  function onMouseMove(event: MouseEvent) {
    const p = canvasToPixel(event.clientX, event.clientY)
    if (map.width) {
      const pos = pixelToMap(map, p.x, p.y)
      cursorInfo.value = `map: x=${pos.x}, y=${pos.y}`
    }
    if (mode.value === 'yaw') {
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

  function onWheel(event: WheelEvent) {
    event.preventDefault()
    const canvas = canvasRef.value
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const before = canvasToPixel(event.clientX, event.clientY)
    const factor = event.deltaY < 0 ? 1.15 : 0.87
    view.scale = Math.min(20, Math.max(0.04, view.scale * factor))
    const cx = event.clientX - rect.left
    const cy = event.clientY - rect.top
    view.offsetX = cx - before.x * view.scale
    view.offsetY = cy - before.y * view.scale
    draw()
  }

  function zoomIn() {
    view.scale *= 1.2
    draw()
  }

  function zoomOut() {
    view.scale /= 1.2
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
    () => options?.initialJson,
    (doc) => {
      if (doc) importRouteJson(doc)
    },
    { immediate: true },
  )

  onMounted(() => {
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resizeCanvas)
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  })

  return {
    map,
    form,
    mode,
    targets,
    selectedTargetId,
    cursorInfo,
    mapInfo,
    jsonPreview,
    targetStatus,
    setMode,
    fitToScreen,
    zoomIn,
    zoomOut,
    applyYamlText,
    applyPgmBuffer,
    importRouteJson,
    exportDocument,
    onFormFieldChange,
    selectTarget,
    orientTarget,
    updateTargetField,
    moveTarget,
    deleteTarget,
    clearTargets,
    addTargetAtCenter,
    handleDroppedFiles,
    onMouseDown,
    onWheel,
    resizeCanvas,
    draw,
  }
}
