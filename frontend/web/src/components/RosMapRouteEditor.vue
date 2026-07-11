<template>
  <div class="ros-route-editor" @dragenter.prevent @dragover.prevent @drop.prevent="onDrop">
    <section class="workspace">
      <div class="toolbar">
        <input ref="yamlInputRef" type="file" accept=".yaml,.yml,text/yaml" hidden @change="onYamlChange" />
        <input ref="pgmInputRef" type="file" accept=".pgm,image/x-portable-graymap" hidden @change="onPgmChange" />
        <input ref="jsonInputRef" type="file" accept=".json,application/json" hidden @change="onJsonChange" />
        <el-button size="small" @click="yamlInputRef?.click()">YAML</el-button>
        <el-button size="small" @click="pgmInputRef?.click()">PGM</el-button>
        <el-button size="small" @click="jsonInputRef?.click()">导入 JSON</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" @click="fitToScreen">适配</el-button>
        <el-button size="small" @click="zoomIn">放大</el-button>
        <el-button size="small" @click="zoomOut">缩小</el-button>
        <div class="mode-group">
          <button type="button" :class="{ active: mode === 'start' }" @click="setMode('start')">起点</button>
          <button type="button" :class="{ active: mode === 'target' }" @click="setMode('target')">巡检点</button>
          <button type="button" :class="{ active: mode === 'yaw' }" @click="setMode('yaw')">方向</button>
          <button type="button" :class="{ active: mode === 'pan' }" @click="setMode('pan')">拖动</button>
        </div>
      </div>

      <div ref="wrapRef" class="map-wrap">
        <canvas
          ref="canvasRef"
          @mousedown="onMouseDown"
          @wheel="onWheel"
          @contextmenu.prevent
        />
        <div v-if="mapLoading" class="map-empty">正在加载平台地图…</div>
        <div v-else-if="!mapLoaded" class="map-empty">拖入或点击上方按钮加载 YAML + PGM 地图</div>
      </div>

      <div class="hud">
        <span>{{ mapInfo }}</span>
        <span>{{ cursorInfo }}</span>
      </div>
    </section>

    <aside class="sidebar">
      <el-tabs v-model="activeTab" class="config-tabs">
        <el-tab-pane label="巡检点" name="targets">
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
                <small>({{ target.x.toFixed(2) }}, {{ target.y.toFixed(2) }})</small>
              </span>
              <span class="target-actions" @click.stop>
                <el-button size="small" link @click="moveTarget(target.id, -1)">↑</el-button>
                <el-button size="small" link @click="moveTarget(target.id, 1)">↓</el-button>
                <el-button size="small" link type="danger" @click="deleteTarget(target.id)">删</el-button>
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
              <el-button size="small" @click="orientTarget(selectedTarget.id)">设置方向</el-button>
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
              <label>默认路线<el-input v-model="form.activeRouteId" size="small" @change="syncForm" /></label>
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
import { computed, ref, toRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRosMapRouteEditor } from '@/composables/useRosMapRouteEditor'
import type { RouteExecutorDocument } from '@/types/routeExecutor'
import { downloadRouteJson } from '@/utils/routeExecutorJson'

const props = defineProps<{
  initialJson?: RouteExecutorDocument | null
  mapId?: string | null
  defaultRouteId?: string
  defaultRouteName?: string
}>()

const emit = defineEmits<{
  change: [doc: RouteExecutorDocument]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const wrapRef = ref<HTMLElement | null>(null)
const yamlInputRef = ref<HTMLInputElement | null>(null)
const pgmInputRef = ref<HTMLInputElement | null>(null)
const jsonInputRef = ref<HTMLInputElement | null>(null)
const activeTab = ref('targets')

const editor = useRosMapRouteEditor(canvasRef, wrapRef, {
  initialJson: toRef(props, 'initialJson'),
  mapId: toRef(props, 'mapId'),
  defaultRouteId: props.defaultRouteId,
  defaultRouteName: props.defaultRouteName,
  onChange: (doc) => emit('change', doc),
})

const {
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
  needsMapUpload,
  getMapUploadPayload,
  markMapSynced,
  mapLoading,
} = editor

const mapLoaded = computed(() => mapInfo.value.includes('px'))

const selectedTarget = computed(() =>
  targets.value.find((t) => t.id === selectedTargetId.value) ?? null,
)

function syncForm() {
  emit('change', exportDocument())
}

function onRouteIdChange() {
  onFormFieldChange('routeId', form.routeId)
  emit('change', exportDocument())
}

async function onYamlChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  applyYamlText(await file.text(), file.name)
}

async function onPgmChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    applyPgmBuffer(await file.arrayBuffer(), file.name)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
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

function onDrop(event: DragEvent) {
  if (event.dataTransfer?.files?.length) {
    void handleDroppedFiles(event.dataTransfer.files)
  }
}

function confirmClearTargets() {
  if (!targets.value.length) return
  ElMessageBox.confirm('确定清空全部巡检点？', '确认', { type: 'warning' })
    .then(() => clearTargets())
    .catch(() => {})
}

async function copyJson() {
  await navigator.clipboard.writeText(jsonPreview.value)
  ElMessage.success('已复制 JSON')
}

function downloadJson() {
  downloadRouteJson(exportDocument())
}

defineExpose({ exportDocument, needsMapUpload, getMapUploadPayload, markMapSynced })
</script>

<style scoped>
.ros-route-editor {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 360px;
  gap: 12px;
  min-height: 640px;
}

.workspace,
.sidebar {
  background: #fff;
  border: 1px solid #e4e7ed;
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
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.mode-group {
  display: inline-flex;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
  margin-left: auto;
}

.mode-group button {
  border: 0;
  background: #fff;
  min-width: 56px;
  min-height: 30px;
  font-size: 13px;
  cursor: pointer;
}

.mode-group button.active {
  background: #ecfdf5;
  color: #0f766e;
  font-weight: 600;
}

.map-wrap {
  position: relative;
  min-height: 420px;
  overflow: hidden;
  background: #f5f7fa;
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
  color: #909399;
  font-size: 14px;
  pointer-events: none;
}

.hud {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border-top: 1px solid #ebeef5;
  color: #909399;
  font-size: 12px;
  background: #fff;
}

.sidebar {
  display: flex;
  flex-direction: column;
}

.config-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 640px;
}

.config-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.config-tabs :deep(.el-tabs__content) {
  flex: 1;
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
  border-color: #0f766e;
  background: #ecfdf5;
}

.target-badge {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #2563eb;
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
  }
}
</style>
