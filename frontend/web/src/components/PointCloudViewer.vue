<template>
  <section class="cloud-viewer" aria-label="三维点云查看器">
    <div class="viewer-toolbar">
      <div><span>POINT CLOUD VIEWPORT</span><strong>{{ pointLabel }}</strong></div>
      <label>点尺寸 <el-slider v-model="pointSize" :min="0.002" :max="0.08" :step="0.002" /></label>
      <el-button size="small" :disabled="!points" @click="fitCamera">适配视图</el-button>
    </div>
    <div ref="hostRef" v-loading="loading" class="viewer-stage">
      <div v-if="error" class="viewer-message error">{{ error }}</div>
      <div v-else-if="!blob && !loading" class="viewer-message">点击“加载点云预览”后在此渲染</div>
      <div class="axis-hint"><i class="x" />X <i class="y" />Y <i class="z" />Z</div>
    </div>
    <footer><span>左键旋转</span><span>右键平移</span><span>滚轮缩放</span><span>Z-UP · METER</span></footer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader.js'

const props = defineProps<{ blob?: Blob | null; pointCount?: number }>()
const hostRef = ref<HTMLElement | null>(null)
const loading = ref(false)
const error = ref('')
const pointSize = ref(0.018)
const points = shallowRef<THREE.Points | null>(null)
const pointLabel = computed(() => props.pointCount == null ? '等待加载' : `${new Intl.NumberFormat('zh-CN').format(props.pointCount)} points`)

let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let renderer: THREE.WebGLRenderer | null = null
let controls: OrbitControls | null = null
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0
let loadVersion = 0

function initialize() {
  if (!hostRef.value || renderer) return
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#07171d')
  scene.fog = new THREE.FogExp2('#07171d', 0.008)
  camera = new THREE.PerspectiveCamera(48, 1, 0.01, 10000)
  camera.up.set(0, 0, 1)
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  hostRef.value.prepend(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  const grid = new THREE.GridHelper(20, 20, '#296d78', '#163941')
  grid.rotation.x = Math.PI / 2
  scene.add(grid, new THREE.AxesHelper(1.4))
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(hostRef.value)
  resize()
  renderLoop()
}

async function loadCloud(blob?: Blob | null) {
  const version = ++loadVersion
  disposePoints()
  error.value = ''
  if (!blob) return
  loading.value = true
  try {
    await nextTick()
    const buffer = await blob.arrayBuffer()
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
    if (version !== loadVersion) return
    const geometry = new PLYLoader().parse(buffer)
    geometry.computeBoundingBox()
    const colors = geometry.hasAttribute('color')
    const material = new THREE.PointsMaterial({
      size: pointSize.value,
      sizeAttenuation: true,
      vertexColors: colors,
      color: colors ? 0xffffff : 0x72d9d0,
    })
    points.value = new THREE.Points(geometry, material)
    scene?.add(points.value)
    fitCamera()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'PLY 点云解析失败'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function fitCamera() {
  if (!points.value || !camera || !controls) return
  const box = new THREE.Box3().setFromObject(points.value)
  if (box.isEmpty()) {
    camera.position.set(3, -3, 2)
    controls.target.set(0, 0, 0)
    controls.update()
    return
  }
  const center = box.getCenter(new THREE.Vector3())
  points.value.geometry.translate(-center.x, -center.y, -center.z)
  points.value.geometry.computeBoundingSphere()
  const radius = Math.max(points.value.geometry.boundingSphere?.radius ?? 1, 0.1)
  camera.near = Math.max(radius / 1000, 0.001)
  camera.far = Math.max(radius * 100, 100)
  camera.position.set(radius * 1.45, -radius * 1.75, radius * 1.15)
  camera.updateProjectionMatrix()
  controls.target.set(0, 0, 0)
  controls.update()
}

function resize() {
  if (!hostRef.value || !camera || !renderer) return
  const width = Math.max(hostRef.value.clientWidth, 1)
  const height = Math.max(hostRef.value.clientHeight, 1)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, false)
}

function renderLoop() {
  controls?.update()
  if (scene && camera) renderer?.render(scene, camera)
  animationFrame = requestAnimationFrame(renderLoop)
}

function disposePoints() {
  if (!points.value) return
  scene?.remove(points.value)
  points.value.geometry.dispose()
  const material = points.value.material
  if (Array.isArray(material)) material.forEach(item => item.dispose())
  else material.dispose()
  points.value = null
}

watch(() => props.blob, value => void loadCloud(value), { immediate: true })
watch(pointSize, value => {
  const material = points.value?.material
  if (material && !Array.isArray(material) && material instanceof THREE.PointsMaterial) material.size = value
})

onMounted(initialize)
onBeforeUnmount(() => {
  loadVersion += 1
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  disposePoints()
  controls?.dispose()
  renderer?.dispose()
  renderer?.domElement.remove()
  scene = null; camera = null; controls = null; renderer = null
})
</script>

<style scoped>
.cloud-viewer { overflow: hidden; border: 1px solid #234c55; background: #07171d; color: #c9e2e5; }
.viewer-toolbar { min-height: 58px; padding: 9px 14px; display: grid; grid-template-columns: 1fr minmax(190px, 260px) auto; align-items: center; gap: 18px; border-bottom: 1px solid #234c55; background: linear-gradient(90deg, #0d252d, #102f36); }
.viewer-toolbar span, .viewer-toolbar strong { display: block; }.viewer-toolbar span { color: #57aab6; font-size: 9px; letter-spacing: .16em; }.viewer-toolbar strong { margin-top: 3px; font: 600 13px/1.2 ui-monospace, monospace; }.viewer-toolbar label { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 12px; color: #8fb5bb; font-size: 11px; }
.viewer-stage { position: relative; height: min(55vh, 520px); min-height: 360px; }.viewer-stage :deep(canvas) { display: block; width: 100%; height: 100%; }.viewer-message { position: absolute; z-index: 2; inset: 0; display: grid; place-items: center; color: #70969d; font-size: 12px; letter-spacing: .05em; pointer-events: none; }.viewer-message.error { color: #ff9f8e; }.axis-hint { position: absolute; z-index: 2; right: 12px; bottom: 10px; display: flex; align-items: center; gap: 4px; color: #6f949b; font: 9px ui-monospace, monospace; pointer-events: none; }.axis-hint i { width: 11px; height: 2px; }.axis-hint .x { background: #ef6a61; }.axis-hint .y { background: #67c978; }.axis-hint .z { background: #5d9eea; }
.cloud-viewer footer { padding: 8px 12px; display: flex; gap: 20px; border-top: 1px solid #193b43; color: #577b82; font: 9px ui-monospace, monospace; letter-spacing: .08em; }.cloud-viewer footer span:last-child { margin-left: auto; color: #65b6c0; }
@media (max-width: 700px) { .viewer-toolbar { grid-template-columns: 1fr auto; }.viewer-toolbar label { grid-column: 1 / -1; grid-row: 2; }.viewer-stage { min-height: 300px; }.cloud-viewer footer { gap: 8px; flex-wrap: wrap; } }
</style>
