<template>
  <div class="ros-route-editor" :class="{ embedded }" @dragenter.prevent @dragover.prevent @drop.prevent="onDrop">
    <section class="workspace">
      <div class="toolbar">
        <input ref="yamlInputRef" type="file" accept=".yaml,.yml,.pgm,text/yaml,image/x-portable-graymap" multiple hidden @change="onMapFilesChange" />
        <input ref="pgmInputRef" type="file" accept=".pgm,image/x-portable-graymap" hidden @change="onPgmChange" />
        <input ref="jsonInputRef" type="file" accept=".json,application/json" hidden @change="onJsonChange" />
        <div class="toolbar-group">
          <el-dropdown trigger="click" @command="handleUploadCommand">
            <el-button size="small" plain><el-icon><Upload /></el-icon>上传地图<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="yaml">上传 YAML + PGM</el-dropdown-item>
                <el-dropdown-item command="pgm">上传 PGM</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" plain @click="jsonInputRef?.click()"><el-icon><Download /></el-icon>导入路线</el-button>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-button size="small" plain title="适应窗口" @click="fitMapToScreen"><el-icon><FullScreen /></el-icon>适应窗口</el-button>
          <el-button size="small" plain title="原始比例" @click="fitToScreen"><el-icon><ScaleToOriginal /></el-icon>原始比例</el-button>
          <el-button size="small" plain title="放大" aria-label="放大" @click="zoomIn"><el-icon><ZoomIn /></el-icon></el-button>
          <el-button size="small" plain title="缩小" aria-label="缩小" @click="zoomOut"><el-icon><ZoomOut /></el-icon></el-button>
        </div>
        <div class="mode-group">
          <button type="button" :class="{ active: mode === 'select' }" title="选择并拖动对象" @click="activateMode('select')"><el-icon><Mouse /></el-icon><span>选择</span></button>
          <button type="button" :class="{ active: mode === 'start' }" title="设置路线起点" @click="activateMode('start')"><el-icon><Flag /></el-icon><span>起点</span></button>
          <button type="button" :class="{ active: mode === 'target' }" title="添加巡检点" @click="activateMode('target')"><el-icon><MapLocation /></el-icon><span>巡检点</span></button>
          <button type="button" :class="{ active: mode === 'yaw' }" title="设置点位朝向" @click="activateMode('yaw')"><el-icon><Position /></el-icon><span>方向</span></button>
          <button type="button" :class="{ active: mode === 'keepout' }" title="绘制禁行区域" @click="activateMode('keepout')"><el-icon><Warning /></el-icon><span>禁行区</span></button>
          <button type="button" :class="{ active: mode === 'pan' }" title="拖动画布" @click="activateMode('pan')"><el-icon><Rank /></el-icon><span>拖动</span></button>
          <button type="button" title="删除当前对象" :disabled="!canDeleteSelection" @click="deleteSelection"><el-icon><Delete /></el-icon><span>删除</span></button>
          <span class="mode-separator" />
          <button type="button" title="撤销" :disabled="!canUndo" @click="undo"><el-icon><RefreshLeft /></el-icon><span>撤销</span></button>
          <button type="button" title="重做" :disabled="!canRedo" @click="redo"><el-icon><RefreshRight /></el-icon><span>重做</span></button>
        </div>
      </div>

      <div ref="wrapRef" class="map-wrap">
        <canvas
          ref="canvasRef"
          @mousedown="onMouseDown"
          @mouseleave="onMouseLeave"
          @wheel="onWheel"
          @contextmenu.prevent
        />
        <div v-if="!mapLoaded" class="map-empty">
          <div class="map-empty-inner">
            <strong>加载 ROS 地图</strong>
            <span>拖入 YAML + PGM，或点击上方按钮导入</span>
          </div>
        </div>
      </div>

      <div class="hud">
        <span>{{ mapInfo }}</span>
        <span>{{ cursorInfo }}</span>
      </div>
    </section>

    <aside class="sidebar">
      <el-alert v-if="isLegacyDraft" type="warning" :closable="false" title="旧版草稿：保存后将转换为 v3，才能创建路线修订" />
      <el-tabs v-model="activeTab" class="config-tabs">
        <el-tab-pane label="对象" name="targets">
          <div class="tab-toolbar">
            <el-button size="small" @click="addTargetAtCenter">追加中心点</el-button>
            <el-button size="small" type="danger" plain :disabled="!targets.length" @click="confirmClearTargets">
              清空
            </el-button>
          </div>

          <div v-if="targets.length" class="target-list">
            <button
              v-for="(target, index) in targets"
              :key="target.id"
              type="button"
              class="target-item"
              :class="{ selected: selectedTargetId === target.id }"
              @click="selectTarget(target.id)"
            >
              <span class="target-badge">{{ index + 1 }}</span>
              <span class="target-summary">
                <strong>{{ target.name }}</strong>
                <small>x {{ target.x.toFixed(2) }} · y {{ target.y.toFixed(2) }} · yaw {{ target.yaw.toFixed(1) }}</small>
              </span>
              <span class="target-actions" @click.stop>
                <el-button size="small" link title="上移" aria-label="上移" @click="moveTarget(target.id, -1)"><el-icon><Top /></el-icon></el-button>
                <el-button size="small" link title="下移" aria-label="下移" @click="moveTarget(target.id, 1)"><el-icon><Bottom /></el-icon></el-button>
                <el-button size="small" link type="danger" title="删除" aria-label="删除" @click="deleteTarget(target.id)"><el-icon><Delete /></el-icon></el-button>
              </span>
            </button>
          </div>
          <div v-else class="tab-empty">在地图上点击添加巡检点</div>

          <template v-if="selectedTarget">
            <div class="edit-block">
              <label>名称<el-input v-model="selectedTarget.name" size="small" @change="updateTargetField(selectedTarget.id, 'name', selectedTarget.name)" /></label>
              <div class="grid-2">
                <label>x<el-input-number v-model="selectedTarget.x" size="small" :step="0.001" controls-position="right" @change="updateTargetField(selectedTarget.id, 'x', selectedTarget.x)" /></label>
                <label>y<el-input-number v-model="selectedTarget.y" size="small" :step="0.001" controls-position="right" @change="updateTargetField(selectedTarget.id, 'y', selectedTarget.y)" /></label>
              </div>
              <div class="grid-2">
                <label>yaw(rad)<el-input-number v-model="selectedTarget.yaw" size="small" :step="0.001" controls-position="right" @change="updateTargetField(selectedTarget.id, 'yaw', selectedTarget.yaw)" /></label>
                <label>停留(s)<el-input-number v-model="selectedTarget.taskDuration" size="small" :step="0.1" controls-position="right" @change="updateTargetField(selectedTarget.id, 'taskDuration', selectedTarget.taskDuration)" /></label>
              </div>
              <el-button size="small" @click="orientTarget(selectedTarget.id)"><el-icon><Position /></el-icon>在地图上设置朝向</el-button>
            </div>
          </template>

          <p class="status" :class="targetStatus.kind">{{ targetStatus.text }}</p>
        </el-tab-pane>

        <el-tab-pane label="起点" name="start">
          <div class="edit-block">
            <label>名称<el-input v-model="form.startName" size="small" @change="syncForm" /></label>
            <div class="grid-3">
              <label>x<el-input-number v-model="form.startX" size="small" :step="0.001" controls-position="right" @change="syncForm" /></label>
              <label>y<el-input-number v-model="form.startY" size="small" :step="0.001" controls-position="right" @change="syncForm" /></label>
              <label>yaw<el-input-number v-model="form.startYaw" size="small" :step="0.001" controls-position="right" @change="syncForm" /></label>
            </div>
            <el-checkbox v-model="form.publishInitialPose" @change="syncForm">发布 /initialpose</el-checkbox>
            <el-collapse>
              <el-collapse-item title="高级选项" name="advanced">
                <div class="grid-3">
                  <label>cov x<el-input-number v-model="form.covX" size="small" :step="0.0001" controls-position="right" @change="syncForm" /></label>
                  <label>cov y<el-input-number v-model="form.covY" size="small" :step="0.0001" controls-position="right" @change="syncForm" /></label>
                  <label>cov yaw<el-input-number v-model="form.covYaw" size="small" :step="0.0001" controls-position="right" @change="syncForm" /></label>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-tab-pane>

        <el-tab-pane label="路线" name="route">
          <div class="edit-block">
            <label>路线名称<el-input v-model="form.routeName" size="small" @change="syncForm" /></label>
            <div class="grid-2">
              <label>路线 ID<el-input v-model="form.routeId" size="small" @change="onRouteIdChange" /></label>
              <label>默认路线<el-input :model-value="form.routeId" size="small" disabled /></label>
            </div>
            <div class="grid-2">
              <label>超时(s)<el-input-number v-model="form.goalTimeout" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
              <label>失败重试<el-input-number v-model="form.maxRetries" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
            </div>
            <label>
              失败策略
              <el-select v-model="form.failurePolicy" size="small" @change="syncForm">
                <el-option label="abort_and_return_home" value="abort_and_return_home" />
                <el-option label="abort" value="abort" />
              </el-select>
            </label>
            <div class="check-row">
              <el-checkbox v-model="form.returnToStart" @change="syncForm">返航</el-checkbox>
              <el-checkbox v-model="form.loopEnabled" @change="syncForm">循环</el-checkbox>
            </div>
            <template v-if="form.loopEnabled">
              <div class="grid-2">
                <label>循环等待(s)<el-input-number v-model="form.loopWait" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
                <label>循环次数<el-input-number v-model="form.maxCycles" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
              </div>
            </template>
          </div>
        </el-tab-pane>

        <el-tab-pane label="禁行区" name="keepout">
          <p class="zone-help">每个禁行区必须是至少 3 个顶点构成的闭合 polygon。选择“禁行区”模式后，在地图点击追加顶点；可直接拖动顶点微调。</p>
          <div class="tab-toolbar">
            <el-button size="small" @click="addKeepoutZone">新增禁区</el-button>
            <el-button size="small" type="danger" plain :disabled="!activeKeepoutZone" @click="deleteActiveZone">删除禁区</el-button>
          </div>

          <div v-if="keepoutZones.length" class="target-list">
            <button
              v-for="zone in keepoutZones"
              :key="zone.id"
              type="button"
              class="target-item"
              :class="{ selected: activeZoneId === zone.id }"
              @click="selectZone(zone.id)"
            >
              <span class="target-badge">{{ zone.polygon.length }}</span>
              <span class="target-summary">
                <strong>{{ zone.name || zone.id }}</strong>
                <small class="zone-meta">{{ zone.id }} · {{ zone.enabled ? '启用' : '停用' }} · {{ zone.polygon.length }} 顶点</small>
              </span>
            </button>
          </div>

          <template v-if="activeKeepoutZone">
            <div class="edit-block">
              <label>名称<el-input :model-value="activeKeepoutZone.name" size="small" @change="onZoneFieldChange('name', $event)" /></label>
              <div class="grid-2">
                <label>ID<el-input :model-value="activeKeepoutZone.id" size="small" @change="onZoneFieldChange('id', $event)" /></label>
                <label>类型<el-input model-value="hard_keepout" size="small" disabled /></label>
              </div>
              <el-checkbox :model-value="activeKeepoutZone.enabled" @change="onZoneFieldChange('enabled', $event)">启用禁行区</el-checkbox>
              <label>边界补偿(m)
                <el-input-number
                  :model-value="activeKeepoutZone.maskPaddingM ?? map.resolution"
                  :min="0"
                  :max="map.resolution"
                  :step="map.resolution || 0.001"
                  size="small"
                  controls-position="right"
                  @change="onZoneFieldChange('maskPaddingM', $event ?? map.resolution)"
                />
              </label>
              <p class="zone-help">边界补偿仅用于禁行 polygon 栅格化；最终避让距离仍由 Nav2 InflationLayer 决定。</p>
              <div class="tab-toolbar">
                <el-button size="small" :disabled="!activeKeepoutZone.polygon.length" @click="deleteLastZonePoint">删除最后顶点</el-button>
                <el-button size="small" type="danger" plain :disabled="!activeKeepoutZone.polygon.length" @click="clearZonePoints">清空 polygon</el-button>
              </div>
              <div class="zone-points">
                <div v-for="(point, index) in activeKeepoutZone.polygon" :key="index" class="grid-3">
                  <label>#<el-input :model-value="String(index + 1)" size="small" disabled /></label>
                  <label>x<el-input-number :model-value="point.x" size="small" :step="0.001" controls-position="right" @change="updateZonePoint(activeKeepoutZone.id, index, 'x', $event ?? point.x)" /></label>
                  <label>y<el-input-number :model-value="point.y" size="small" :step="0.001" controls-position="right" @change="updateZonePoint(activeKeepoutZone.id, index, 'y', $event ?? point.y)" /></label>
                </div>
              </div>
            </div>
          </template>
          <p class="status">{{ zoneStatus }}</p>
          <el-divider content-position="left">车体模型</el-divider>
          <el-checkbox v-model="showFootprint">显示 ros2_DL Nav2 footprint</el-checkbox>
          <el-checkbox v-model="showFootprintPadding">显示 1cm 安全边界</el-checkbox>
          <p class="zone-help">点位会以偏心圆柱 16 边形车体轮廓校验占用栅格、未知区和禁行区。</p>
        </el-tab-pane>

        <el-tab-pane label="JSON" name="json">
          <textarea class="json-preview" readonly :value="jsonPreview" />
          <div class="json-actions">
            <el-button size="small" @click="copyJson">复制</el-button>
            <el-button size="small" type="primary" @click="downloadJson">下载 route.json</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourcesApi } from '@/api/resources'
import { useRosMapRouteEditor } from '@/composables/useRosMapRouteEditor'
import type { MapAssetUploadFiles } from '@/types'
import type { RouteExecutorDocument } from '@/types/routeExecutor'
import { downloadRouteJson } from '@/utils/routeExecutorJson'
import { rosMapImageFileName } from '@/utils/rosMap'

const props = defineProps<{
  initialJson?: RouteExecutorDocument | null
  defaultRouteId?: string
  defaultRouteName?: string
  mapId?: string | null
  embedded?: boolean
}>()

const emit = defineEmits<{
  change: [doc: RouteExecutorDocument]
  mapFilesChange: [files: MapAssetUploadFiles]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const wrapRef = ref<HTMLElement | null>(null)
const yamlInputRef = ref<HTMLInputElement | null>(null)
const pgmInputRef = ref<HTMLInputElement | null>(null)
const jsonInputRef = ref<HTMLInputElement | null>(null)
const activeTab = ref('targets')
const yamlSourceFile = ref<File | null>(null)
const pgmSourceFile = ref<File | null>(null)
const history = ref<string[]>([])
const historyIndex = ref(-1)
const replayingHistory = ref(false)
let mapLoadVersion = 0

const editor = useRosMapRouteEditor(canvasRef, wrapRef, {
  initialJson: () => props.initialJson,
  defaultRouteId: props.defaultRouteId,
  defaultRouteName: props.defaultRouteName,
  onChange: handleEditorChange,
})

const {
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
} = editor

const mapLoaded = computed(() => Boolean(map.pixels && map.width && map.height))

const selectedTarget = computed(() =>
  targets.value.find((t) => t.id === selectedTargetId.value) ?? null,
)
const activeKeepoutZone = computed(() =>
  keepoutZones.value.find((zone) => zone.id === activeZoneId.value) ?? null,
)
const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value >= 0 && historyIndex.value < history.value.length - 1)
const canDeleteSelection = computed(() => activeTab.value === 'targets' ? Boolean(selectedTarget.value) : activeTab.value === 'keepout' ? Boolean(activeKeepoutZone.value) : false)

function resetHistory(document: RouteExecutorDocument | null | undefined) {
  history.value = document ? [JSON.stringify(document as unknown)] : []
  historyIndex.value = document ? 0 : -1
  replayingHistory.value = false
}

function handleEditorChange(document: RouteExecutorDocument) {
  emit('change', document)
  if (replayingHistory.value) {
    replayingHistory.value = false
    return
  }
  const serialized = JSON.stringify(document as unknown)
  const current = history.value[historyIndex.value]
  if (current === serialized) return
  const nextHistory = [...history.value.slice(0, historyIndex.value + 1), serialized].slice(-50)
  history.value = nextHistory
  historyIndex.value = nextHistory.length - 1
}

function activateMode(next: typeof mode.value) {
  setMode(next)
  if (next === 'target') activeTab.value = 'targets'
  if (next === 'start') activeTab.value = 'start'
  if (next === 'keepout') activeTab.value = 'keepout'
}

function undo() {
  if (!canUndo.value) return
  historyIndex.value -= 1
  replayingHistory.value = true
  importRouteJson(JSON.parse(history.value[historyIndex.value]) as RouteExecutorDocument)
}

function redo() {
  if (!canRedo.value) return
  historyIndex.value += 1
  replayingHistory.value = true
  importRouteJson(JSON.parse(history.value[historyIndex.value]) as RouteExecutorDocument)
}

async function deleteSelection() {
  if (!canDeleteSelection.value) return
  try {
    await ElMessageBox.confirm(activeTab.value === 'keepout' ? '确定删除当前禁行区？' : '确定删除当前巡检点？', '删除对象', { type: 'warning' })
  } catch {
    return
  }
  if (activeTab.value === 'keepout') deleteActiveZone()
  else if (selectedTarget.value) deleteTarget(selectedTarget.value.id)
}

function syncForm() {
  emit('change', exportDocument())
}

function onRouteIdChange() {
  onFormFieldChange('routeId', form.routeId)
  emit('change', exportDocument())
}

function handleUploadCommand(command: string) {
  if (command === 'yaml') yamlInputRef.value?.click()
  if (command === 'pgm') pgmInputRef.value?.click()
}

function rememberMapFiles(files: FileList | File[]) {
  const selected = Array.from(files)
  const yaml = selected.find((file) => /\.ya?ml$/i.test(file.name))
  const pgm = selected.find((file) => /\.pgm$/i.test(file.name))
  if (yaml) yamlSourceFile.value = yaml
  if (pgm) pgmSourceFile.value = pgm
  const expectedPgmName = rosMapImageFileName(map.image).toLowerCase()
  if (pgmSourceFile.value && pgmSourceFile.value.name.toLowerCase() !== expectedPgmName) {
    pgmSourceFile.value = null
  }
  if (yamlSourceFile.value && pgmSourceFile.value) {
    emit('mapFilesChange', { yaml: yamlSourceFile.value, pgm: pgmSourceFile.value })
  }
}

async function syncMapIdentity(imageSha256?: string) {
  if (!map.yamlName || !map.width || !map.height) return
  const source = pgmSourceFile.value
  const hash = imageSha256 ?? (source ? await sha256(await source.arrayBuffer()) : '')
  setMapAssetIdentity({
    yaml: map.yamlName,
    image: map.image,
    resolution: map.resolution,
    origin: [...map.origin] as [number, number, number],
    width: map.width,
    height: map.height,
    image_sha256: hash,
  })
}

async function sha256(buffer: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('')
}

async function loadPersistedMap(mapId?: string | null) {
  const version = ++mapLoadVersion
  if (!mapId) return
  mapInfo.value = '正在从平台加载地图...'
  try {
    const [asset, yamlBlob, pgmBlob] = await Promise.all([
      resourcesApi.getMapAsset(mapId),
      resourcesApi.getMapAssetYaml(mapId),
      resourcesApi.getMapAssetPgm(mapId),
    ])
    if (version !== mapLoadVersion) return
    const yamlFile = new File([yamlBlob], asset.yamlName, { type: 'application/yaml' })
    const pgmFile = new File([pgmBlob], asset.pgmName, { type: 'image/x-portable-graymap' })
    applyYamlText(await yamlFile.text(), yamlFile.name)
    applyPgmBuffer(await pgmFile.arrayBuffer(), pgmFile.name)
    await syncMapIdentity(asset.pgmSha256)
    yamlSourceFile.value = yamlFile
    pgmSourceFile.value = pgmFile
  } catch (error) {
    if (version !== mapLoadVersion) return
    mapInfo.value = '平台地图加载失败'
    ElMessage.error(error instanceof Error ? error.message : String(error))
  }
}

async function onMapFilesChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files?.length) return
  try {
    clearRouteAnnotations()
    await handleDroppedFiles(files)
    rememberMapFiles(files)
    await syncMapIdentity()
    ElMessage.success(mapLoaded.value ? 'YAML/PGM 地图已导入' : `YAML 已导入，请继续选择 ${map.image}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  } finally {
    input.value = ''
  }
}

async function onPgmChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    clearRouteAnnotations()
    applyPgmBuffer(await file.arrayBuffer(), file.name)
    rememberMapFiles([file])
    await syncMapIdentity()
    ElMessage.success('PGM 地图已导入')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  } finally {
    input.value = ''
  }
}

async function onJsonChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    importRouteJson(JSON.parse(await file.text()))
    emit('change', exportDocument())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  }
}

async function onDrop(event: DragEvent) {
  if (event.dataTransfer?.files?.length) {
    try {
      const files = Array.from(event.dataTransfer.files)
      const mapFiles = files.filter((file) => /\.ya?ml$|\.pgm$/i.test(file.name))
      const routeFiles = files.filter((file) => /\.json$/i.test(file.name))
      if (mapFiles.length) clearRouteAnnotations()
      await handleDroppedFiles(mapFiles)
      rememberMapFiles(mapFiles)
      if (mapFiles.length) await syncMapIdentity()
      for (const file of routeFiles) importRouteJson(JSON.parse(await file.text()))
      if (mapFiles.length) {
        ElMessage.success(mapLoaded.value ? '地图文件已导入' : `YAML 已导入，请继续选择 ${map.image}`)
      } else if (routeFiles.length) {
        ElMessage.success('路线 JSON 已导入')
      }
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : String(error))
    }
  }
}

function confirmClearTargets() {
  if (!targets.value.length) return
  ElMessageBox.confirm('确定清空全部巡检点？', '确认', { type: 'warning' })
    .then(() => clearTargets())
    .catch(() => {})
}

async function copyJson() {
  if (!ensureExportable()) return
  await navigator.clipboard.writeText(jsonPreview.value)
  ElMessage.success('已复制 JSON')
}

function downloadJson() {
  if (!ensureExportable()) return
  downloadRouteJson(exportDocument())
}

function ensureExportable() {
  const problems = validateForExport()
  if (!problems.length) return true
  ElMessage.error(`不能保存或导出路线：${problems.join('；')}`)
  return false
}

function onZoneFieldChange(field: 'name' | 'id' | 'enabled' | 'maskPaddingM', value: string | number | boolean) {
  const zone = activeKeepoutZone.value
  if (!zone) return
  try {
    updateZoneField(zone.id, field, value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  }
}

watch(() => props.mapId, (mapId) => void loadPersistedMap(mapId), { immediate: true })
watch(() => props.initialJson, (document) => resetHistory(document ?? exportDocument()), { immediate: true })

defineExpose({ exportDocument, validateForExport, downloadJson, openRouteImport: () => jsonInputRef.value?.click() })
</script>

<style scoped>
.ros-route-editor {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 340px;
  gap: 0;
  height: clamp(520px, 72vh, 780px);
}

.ros-route-editor.embedded {
  min-height: 0;
}

.ros-route-editor.embedded .workspace {
  border: none;
  border-radius: 0;
}

.ros-route-editor.embedded .sidebar {
  border: none;
  border-left: 1px solid var(--pi-border-soft, #e4e7ed);
  border-radius: 0;
}

.workspace,
.sidebar {
  background: #fff;
  border: 1px solid var(--pi-border-soft, #e4e7ed);
  border-radius: 8px;
  min-height: 0;
  overflow: hidden;
}

.workspace {
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 10px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--pi-border-soft, #ebeef5);
  background: #fafbfc;
}

.toolbar-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.toolbar-label {
  color: var(--pi-muted, #909399);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  margin-right: 2px;
}

.mode-group {
  display: inline-flex;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  margin-left: auto;
  background: #fff;
}

.mode-group button {
  border: 0;
  background: #fff;
  min-width: 58px;
  min-height: 30px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.mode-group button + button {
  border-left: 1px solid #ebeef5;
}

.mode-group button:hover {
  background: #f5f7fa;
}

.mode-group button.active {
  background: #e6f4ff;
  color: var(--pi-primary, #1677ff);
  font-weight: 600;
}

.map-wrap {
  position: relative;
  min-height: 0;
  overflow: hidden;
  background: #eef2f6;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: crosshair;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  pointer-events: none;
  background: repeating-linear-gradient(
    -45deg,
    rgba(0, 0, 0, 0.02),
    rgba(0, 0, 0, 0.02) 8px,
    rgba(0, 0, 0, 0.04) 8px,
    rgba(0, 0, 0, 0.04) 16px
  );
}

.map-empty-inner {
  display: grid;
  gap: 6px;
  padding: 16px 22px;
  border: 1px dashed #c0c4cc;
  border-radius: 10px;
  text-align: center;
  color: #606266;
  background: rgba(255, 255, 255, 0.88);
}

.map-empty-inner strong {
  font-size: 15px;
  color: #303133;
}

.map-empty-inner span {
  font-size: 13px;
  color: #909399;
}

.hud {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 12px;
  border-top: 1px solid #e4e7ed;
  color: #606266;
  font-size: 11px;
  font-family: Consolas, 'Courier New', monospace;
  background: #f5f7fa;
}

.sidebar {
  display: flex;
  flex-direction: column;
}

.config-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.config-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  background: #fafbfc;
  border-bottom: 1px solid var(--pi-border-soft, #ebeef5);
}

.config-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}

.tab-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.target-list {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
}

.target-item {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.target-item.selected {
  border-color: var(--pi-primary, #1677ff);
  background: #e6f4ff;
}

.target-badge {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--pi-primary, #1677ff);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
}

.target-summary {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.target-summary strong {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.target-summary small {
  color: #909399;
  font-size: 12px;
}

.target-actions {
  display: flex;
  gap: 2px;
}

.tab-empty {
  padding: 24px 8px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}

.edit-block {
  display: grid;
  gap: 10px;
  padding-top: 4px;
}

.grid-2,
.grid-3 {
  display: grid;
  gap: 8px;
}

.grid-2 {
  grid-template-columns: 1fr 1fr;
}

.grid-3 {
  grid-template-columns: 1fr 1fr 1fr;
}

label {
  display: grid;
  gap: 4px;
  color: #606266;
  font-size: 12px;
}

.check-row {
  display: flex;
  gap: 16px;
}

.status {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.status.error {
  color: #f56c6c;
}

.zone-help {
  margin: 0 0 12px;
  color: #7a8895;
  font-size: 12px;
  line-height: 1.55;
}

.zone-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zone-points {
  display: grid;
  gap: 6px;
}

.config-tabs :deep(.el-checkbox) {
  margin: 2px 0;
}

.json-preview {
  width: 100%;
  height: 420px;
  resize: vertical;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  background: #fafafa;
  color: #303133;
  box-sizing: border-box;
}

.json-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 1200px) {
  .ros-route-editor {
    grid-template-columns: 1fr;
    height: auto;
  }

  .ros-route-editor.embedded .sidebar {
    border-left: none;
    border-top: 1px solid var(--pi-border-soft, #e4e7ed);
  }

  .mode-group {
    margin-left: 0;
    width: 100%;
  }

  .mode-group button {
    flex: 1;
  }

  .map-wrap {
    min-height: 560px;
  }

  .config-tabs {
    height: 520px;
  }
}

/* Dense industrial route editor */
.ros-route-editor {
  grid-template-columns: minmax(430px, 1fr) 350px;
  height: clamp(600px, 70vh, 760px);
  color: #1d334f;
}

.workspace {
  position: relative;
  grid-template-rows: 47px minmax(0, 1fr) 34px;
  background: #e9edf2;
}

.toolbar {
  position: relative;
  z-index: 4;
  flex-wrap: nowrap;
  min-width: 0;
  padding: 8px 10px;
  border-bottom-color: #dbe3ed;
  background: #fbfcfe;
}

.toolbar-group {
  min-width: 0;
  gap: 6px;
}

.toolbar-group:nth-of-type(2) {
  margin-left: auto;
}

.toolbar :deep(.el-button) {
  height: 30px;
  margin: 0;
  border-radius: 5px;
  color: #405a78;
}

.toolbar :deep(.el-button:hover) {
  color: #1768e5;
  background: #eef5ff;
}

.mode-group {
  position: absolute;
  z-index: 5;
  top: 57px;
  left: 8px;
  display: grid;
  width: 84px;
  margin: 0;
  border: 1px solid #cbd6e4;
  border-radius: 6px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 14px rgba(34, 55, 82, 0.12);
}

.mode-group button {
  display: grid;
  grid-template-columns: 22px 1fr;
  align-items: center;
  gap: 4px;
  min-width: 0;
  min-height: 46px;
  padding: 0 9px;
  color: #40536b;
  font-size: 13px;
  text-align: left;
}

.mode-group button + button {
  border-top: 1px solid #e4eaf1;
  border-left: 0;
}

.mode-group button .el-icon {
  font-size: 18px;
}

.mode-group button.active {
  color: #1768e5;
  background: #eaf3ff;
  box-shadow: inset 3px 0 #1768e5;
}

.mode-group button:disabled {
  color: #aeb9c8;
  background: #f8fafc;
  cursor: not-allowed;
}

.mode-separator {
  height: 1px;
  margin: 5px 9px;
  background: #d9e1eb;
}

.map-wrap {
  margin: 7px;
  border: 1px solid #cbd5e1;
  background-color: #e9edf2;
  background-image:
    linear-gradient(rgba(118, 137, 160, .08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(118, 137, 160, .08) 1px, transparent 1px);
  background-size: 24px 24px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .45);
}

canvas {
  filter: drop-shadow(0 2px 4px rgba(28, 47, 70, .12));
}

.map-empty {
  background: transparent;
}

.map-empty-inner {
  border-radius: 6px;
}

.hud {
  align-items: center;
  min-width: 0;
  padding: 7px 11px;
  border-top-color: #d5dee9;
  color: #4b607b;
  background: #f8fafc;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-variant-numeric: tabular-nums;
}

.hud span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar {
  border-left-color: #dbe3ed !important;
  background: #fff;
}

.config-tabs :deep(.el-tabs__header) {
  height: 47px;
  padding: 0 12px;
  border-bottom-color: #dbe3ed;
  background: #fbfcfe;
}

.config-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #dbe3ed;
}

.config-tabs :deep(.el-tabs__item) {
  height: 47px;
  padding: 0 12px;
  color: #60748e;
  font-size: 12px;
  font-weight: 600;
}

.config-tabs :deep(.el-tabs__item.is-active) {
  color: #1768e5;
}

.config-tabs :deep(.el-tabs__content) {
  padding: 12px 10px 16px;
  scrollbar-color: #becadd transparent;
  scrollbar-width: thin;
}

.tab-toolbar {
  justify-content: flex-end;
  margin-bottom: 9px;
}

.target-list {
  gap: 6px;
  margin-bottom: 14px;
}

.target-item {
  min-height: 52px;
  padding: 7px 8px;
  border-color: #dfe6ef;
  border-radius: 6px;
}

.target-item:hover {
  border-color: #b8cae0;
  background: #f8fbff;
}

.target-item.selected {
  border-color: #5d9df6;
  background: #edf5ff;
  box-shadow: inset 3px 0 #1768e5;
}

.target-badge {
  width: 22px;
  height: 22px;
  background: #1768e5;
  font-size: 11px;
}

.target-summary strong {
  color: #253b57;
  font-size: 12px;
}

.target-summary small {
  color: #6d819b;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.target-actions {
  opacity: 0;
  transition: opacity .15s ease;
}

.target-item:hover .target-actions,
.target-item.selected .target-actions {
  opacity: 1;
}

.target-actions :deep(.el-button) {
  width: 22px;
  height: 22px;
  margin: 0;
  padding: 0;
}

.edit-block {
  gap: 12px;
  padding: 12px 2px 0;
  border-top: 1px solid #e3e9f1;
}

.edit-block::before {
  content: "属性配置";
  color: #354c68;
  font-size: 12px;
  font-weight: 700;
}

label {
  color: #60748e;
  font-size: 11px;
}

.edit-block :deep(.el-input__wrapper),
.edit-block :deep(.el-select__wrapper) {
  min-height: 31px;
  border-radius: 5px;
}

.status {
  padding: 8px;
  border-radius: 5px;
  background: #f6f8fb;
}

.json-preview {
  border-radius: 5px;
  background: #f6f8fb;
  font-family: "SFMono-Regular", Consolas, monospace;
}

@media (max-width: 1200px) {
  .ros-route-editor {
    grid-template-columns: 1fr;
    height: auto;
  }

  .workspace {
    min-height: 600px;
  }

  .mode-group {
    top: 57px;
    width: 78px;
  }

  .config-tabs {
    height: 520px;
  }
}

@media (max-width: 680px) {
  .toolbar-group:nth-of-type(2) {
    display: none;
  }

  .workspace {
    min-height: 520px;
  }

  .mode-group {
    width: 42px;
  }

  .mode-group button {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .mode-group button span {
    display: none;
  }
}
</style>
