<template>
  <div class="ros-route-editor" @dragenter.prevent @dragover.prevent @drop.prevent="onDrop">
    <section class="workspace">
      <div class="toolbar">
        <label class="file-control">
          YAML 地图配置
          <input type="file" accept=".yaml,.yml,text/yaml" @change="onYamlChange" />
        </label>
        <label class="file-control">
          PGM 地图图像
          <input type="file" accept=".pgm,image/x-portable-graymap" @change="onPgmChange" />
        </label>
        <label class="file-control">
          导入路线 JSON
          <input type="file" accept=".json,application/json" @change="onJsonChange" />
        </label>
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
      </div>

      <div class="hud">
        <span>{{ mapInfo }}</span>
        <span>{{ cursorInfo }}</span>
      </div>
    </section>

    <aside class="sidebar">
      <div class="side-head">
        <h2>路线标注</h2>
        <p>点击地图设置起点或追加巡检点；选择“方向”后点选点位，再点击或拖拽确定朝向。</p>
      </div>

      <div class="side-scroll">
        <section class="section">
          <h3>起点</h3>
          <label>
            名称
            <el-input v-model="form.startName" size="small" @change="syncForm" />
          </label>
          <div class="grid-3">
            <label>x<el-input-number v-model="form.startX" size="small" :step="0.001" controls-position="right" @change="syncForm" /></label>
            <label>y<el-input-number v-model="form.startY" size="small" :step="0.001" controls-position="right" @change="syncForm" /></label>
            <label>yaw(rad)<el-input-number v-model="form.startYaw" size="small" :step="0.001" controls-position="right" @change="syncForm" /></label>
          </div>
          <label class="check-line">
            <el-checkbox v-model="form.publishInitialPose" @change="syncForm">发布 /initialpose</el-checkbox>
          </label>
          <div class="grid-3">
            <label>cov x<el-input-number v-model="form.covX" size="small" :step="0.0001" controls-position="right" @change="syncForm" /></label>
            <label>cov y<el-input-number v-model="form.covY" size="small" :step="0.0001" controls-position="right" @change="syncForm" /></label>
            <label>cov yaw<el-input-number v-model="form.covYaw" size="small" :step="0.0001" controls-position="right" @change="syncForm" /></label>
          </div>
        </section>

        <section class="section">
          <h3>路线</h3>
          <div class="grid-2">
            <label>路线 ID<el-input v-model="form.routeId" size="small" @change="onRouteIdChange" /></label>
            <label>默认路线<el-input v-model="form.activeRouteId" size="small" @change="syncForm" /></label>
          </div>
          <label>路线名称<el-input v-model="form.routeName" size="small" @change="syncForm" /></label>
          <div class="grid-2">
            <label>导航超时(s)<el-input-number v-model="form.goalTimeout" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
            <label>失败重试<el-input-number v-model="form.maxRetries" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
          </div>
          <div class="grid-2">
            <label>
              失败策略
              <el-select v-model="form.failurePolicy" size="small" @change="syncForm">
                <el-option label="abort_and_return_home" value="abort_and_return_home" />
                <el-option label="abort" value="abort" />
              </el-select>
            </label>
            <label>循环等待(s)<el-input-number v-model="form.loopWait" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
          </div>
          <div class="grid-2 check-row">
            <el-checkbox v-model="form.returnToStart" @change="syncForm">返航</el-checkbox>
            <el-checkbox v-model="form.loopEnabled" @change="syncForm">循环</el-checkbox>
          </div>
          <label>循环次数，0 表示一直循环<el-input-number v-model="form.maxCycles" size="small" :step="1" controls-position="right" @change="syncForm" /></label>
        </section>

        <section class="section">
          <h3>巡检点</h3>
          <div class="grid-2 actions-row">
            <el-button size="small" @click="addTargetAtCenter">追加中心点</el-button>
            <el-button size="small" type="danger" plain @click="confirmClearTargets">清空巡检点</el-button>
          </div>
          <div
            v-for="(target, index) in targets"
            :key="target.id"
            class="target-row"
            :class="{ selected: selectedTargetId === target.id }"
            @click="selectTarget(target.id)"
          >
            <div class="badge">{{ index + 1 }}</div>
            <div class="target-fields">
              <el-input
                v-model="target.name"
                size="small"
                @click.stop
                @change="updateTargetField(target.id, 'name', target.name)"
              />
              <div class="grid-2">
                <el-input-number
                  v-model="target.x"
                  size="small"
                  :step="0.001"
                  controls-position="right"
                  @click.stop
                  @change="updateTargetField(target.id, 'x', target.x)"
                />
                <el-input-number
                  v-model="target.y"
                  size="small"
                  :step="0.001"
                  controls-position="right"
                  @click.stop
                  @change="updateTargetField(target.id, 'y', target.y)"
                />
              </div>
              <div class="grid-2">
                <el-input-number
                  v-model="target.yaw"
                  size="small"
                  :step="0.001"
                  controls-position="right"
                  @click.stop
                  @change="updateTargetField(target.id, 'yaw', target.yaw)"
                />
                <el-input-number
                  v-model="target.taskDuration"
                  size="small"
                  :step="0.1"
                  controls-position="right"
                  @click.stop
                  @change="updateTargetField(target.id, 'taskDuration', target.taskDuration)"
                />
              </div>
              <div class="target-meta">{{ target.id }} | yaw(rad) / task_duration_sec</div>
            </div>
            <div class="row-actions">
              <el-button size="small" @click.stop="orientTarget(target.id)">↗</el-button>
              <el-button size="small" @click.stop="moveTarget(target.id, -1)">↑</el-button>
              <el-button size="small" @click.stop="moveTarget(target.id, 1)">↓</el-button>
              <el-button size="small" type="danger" plain @click.stop="deleteTarget(target.id)">×</el-button>
            </div>
          </div>
          <p class="status" :class="targetStatus.kind">{{ targetStatus.text }}</p>
        </section>

        <section class="section">
          <h3>JSON 预览</h3>
          <textarea class="json-preview" readonly :value="jsonPreview" />
        </section>
      </div>

      <div class="footer">
        <el-button @click="copyJson">复制 JSON</el-button>
        <el-button type="primary" @click="downloadJson">下载 route.json</el-button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRosMapRouteEditor } from '@/composables/useRosMapRouteEditor'
import type { RouteExecutorDocument } from '@/types/routeExecutor'
import { downloadRouteJson } from '@/utils/routeExecutorJson'

const props = defineProps<{
  initialJson?: RouteExecutorDocument | null
  defaultRouteId?: string
}>()

const emit = defineEmits<{
  change: [doc: RouteExecutorDocument]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const wrapRef = ref<HTMLElement | null>(null)

const editor = useRosMapRouteEditor(canvasRef, wrapRef, {
  initialJson: props.initialJson,
  defaultRouteId: props.defaultRouteId,
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
} = editor

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

defineExpose({ exportDocument })
</script>

<style scoped>
.ros-route-editor {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 380px;
  gap: 12px;
  min-height: 640px;
}

.workspace,
.sidebar {
  background: #fff;
  border: 1px solid #d8dee6;
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
  padding: 10px;
  border-bottom: 1px solid #d8dee6;
  background: #fbfcfd;
}

.file-control {
  display: grid;
  gap: 3px;
  font-size: 12px;
  color: #667085;
  min-width: 140px;
}

.file-control input[type='file'] {
  font-size: 12px;
}

.mode-group {
  display: inline-flex;
  border: 1px solid #d8dee6;
  border-radius: 7px;
  overflow: hidden;
  margin-left: auto;
}

.mode-group button {
  border: 0;
  background: #fff;
  min-width: 64px;
  min-height: 32px;
  cursor: pointer;
}

.mode-group button.active {
  background: #e6fffb;
  color: #115e59;
  font-weight: 700;
}

.map-wrap {
  position: relative;
  min-height: 420px;
  overflow: hidden;
  background:
    linear-gradient(45deg, #e8edf2 25%, transparent 25%),
    linear-gradient(-45deg, #e8edf2 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #e8edf2 75%),
    linear-gradient(-45deg, transparent 75%, #e8edf2 75%);
  background-size: 24px 24px;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: crosshair;
}

.hud {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-top: 1px solid #d8dee6;
  color: #667085;
  font-size: 13px;
  background: #fbfcfd;
}

.sidebar {
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.side-head {
  padding: 12px;
  border-bottom: 1px solid #d8dee6;
}

.side-head h2 {
  margin: 0;
  font-size: 16px;
}

.side-head p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}

.side-scroll {
  overflow: auto;
  padding: 12px;
}

.section {
  border-bottom: 1px solid #d8dee6;
  padding-bottom: 14px;
  margin-bottom: 14px;
}

.section h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.grid-2,
.grid-3 {
  display: grid;
  gap: 8px;
  margin-top: 8px;
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
  color: #667085;
  font-size: 12px;
}

.check-line,
.check-row {
  margin-top: 8px;
}

.actions-row {
  margin-bottom: 8px;
}

.target-row {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  gap: 8px;
  align-items: start;
  padding: 8px;
  border: 1px solid #d8dee6;
  border-radius: 7px;
  margin-bottom: 8px;
  cursor: pointer;
}

.target-row.selected {
  outline: 2px solid rgba(15, 118, 110, 0.25);
  border-color: #0f766e;
}

.badge {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}

.target-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #667085;
}

.row-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status {
  color: #667085;
  font-size: 13px;
  line-height: 1.45;
}

.status.error {
  color: #b42318;
  font-weight: 700;
}

.json-preview {
  width: 100%;
  height: 160px;
  resize: vertical;
  border: 1px solid #d8dee6;
  border-radius: 6px;
  padding: 8px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  background: #0f172a;
  color: #dbeafe;
}

.footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #d8dee6;
  background: #fbfcfd;
}

@media (max-width: 1200px) {
  .ros-route-editor {
    grid-template-columns: 1fr;
  }
}
</style>
