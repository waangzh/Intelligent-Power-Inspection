<template>
  <div class="detection-page">
    <PageHeader
      title="检测策略"
      description="上传或选择巡检图片，配置检测项并调用 LocateAnything 模型验证定位结果。"
      :breadcrumbs="[{ label: '资产感知' }, { label: '检测策略' }]"
    />

    <el-card shadow="never" class="workbench-card">
      <template #header>
        <div class="card-header">
          <div>
            <span class="table-title">图片检测</span>
            <p>选择本地图片或机器人巡检图片，配置检测项后发起模型检测。</p>
          </div>
          <el-segmented v-model="sourceMode" :options="sourceOptions" />
        </div>
      </template>

      <div class="detection-workbench">
        <section class="media-pane">
          <div v-if="sourceMode === 'LOCAL'" class="upload-panel" @click="triggerFileInput">
            <input ref="fileInputRef" class="file-input" type="file" accept="image/jpeg,image/png,image/webp,image/bmp" @change="onFileChange" />
            <img v-if="previewUrl" :src="previewUrl" class="preview-img" alt="待检测图片" />
            <div v-else class="upload-empty">
              <el-icon><UploadFilled /></el-icon>
              <strong>选择检测图片</strong>
              <span>支持 JPG、PNG、WEBP、BMP</span>
            </div>
          </div>

          <div v-else class="robot-source-panel">
            <div class="robot-filters">
              <el-select v-model="robotFilter.taskId" clearable placeholder="最近任务" @change="onRobotTaskChange">
                <el-option v-for="item in taskStore.tasks" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
              <el-select v-model="robotFilter.checkpointId" clearable placeholder="检查点" @change="() => loadRobotImages()">
                <el-option v-for="item in robotFilterCheckpoints" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
              <el-select v-model="robotFilter.robotId" clearable placeholder="机器人" @change="() => loadRobotImages()">
                <el-option v-for="item in robotStore.robots" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
              <el-button type="primary" plain @click="openImportDialog">导入测试图片</el-button>
            </div>
            <div v-if="selectedRobotImage" class="robot-preview">
              <img v-if="selectedRobotImage.originalAvailable" :src="selectedRobotImage.imageUrl" alt="选中的机器人巡检图片" />
              <el-empty v-else description="原始图片已按保留策略清理" :image-size="48" />
              <div>{{ selectedRobotImage.checkpointName }} · {{ fmt(selectedRobotImage.capturedAt) }}</div>
            </div>
            <div class="robot-image-list">
              <button
                v-for="item in detectionStore.images"
                :key="item.id"
                type="button"
                :class="['robot-image-item', { selected: item.id === selectedRobotImageId }]"
                @click="selectRobotImage(item)"
              >
                <img v-if="item.originalAvailable" :src="item.imageUrl" alt="机器人巡检图片缩略图" />
                <span v-else>原图已清理</span>
                <small>{{ item.checkpointName }}</small>
              </button>
            </div>
            <ListPagination :total="detectionStore.imageTotal" :page="imagePage" @change="loadImagePage" />
          </div>
        </section>

        <section class="config-pane">
          <div class="pane-head">
            <span class="pane-title">检测配置</span>
          </div>
          <div class="config-body">
            <div v-if="sourceMode === 'ROBOT' && selectedRobotImage" class="config-source">
              {{ manualConfigSource }}
            </div>
            <DetectionConfig :items="manualItems" @change="updateManualItems" />
          </div>
          <div class="action-bar">
            <el-button @click="resetManualDetection">清空</el-button>
            <el-button type="primary" :loading="detecting" @click="submitManualDetection">
              <el-icon><Search /></el-icon>
              调用模型检测
            </el-button>
          </div>
        </section>

        <section class="result-pane">
          <div class="pane-head">
            <span class="pane-title">检测结果</span>
            <el-tag v-if="manualResult" :type="manualStatusTagType" effect="plain">{{ manualStatusLabel }}</el-tag>
          </div>
          <div class="result-body">
            <div v-if="manualResult" class="result-stack">
              <el-alert
                v-if="manualResult.status === 'RUNNING'"
                title="模型检测中，结果会自动刷新"
                type="info"
                show-icon
                :closable="false"
              />
              <el-alert
                v-if="manualResult.status === 'FAILED'"
                :title="manualResult.errorMessage || '模型检测失败'"
                type="error"
                show-icon
                :closable="false"
              />
              <el-alert
                v-for="(warning, index) in manualResult.warnings"
                :key="`${index}:${warning}`"
                :title="warning"
                type="warning"
                show-icon
                :closable="false"
              />
              <el-progress
                v-if="manualResult.status === 'RUNNING'"
                :percentage="100"
                :indeterminate="true"
                :duration="3"
              />
              <div v-if="manualResult.resultImageUrl" class="result-image">
                <figure>
                  <figcaption>检测结果（所有目标已合并标注）</figcaption>
                  <img :src="manualResult.resultImageUrl" alt="合并标注结果图" />
                </figure>
              </div>
              <div v-else-if="manualResult.status === 'RUNNING'" class="result-image">
                <figure>
                  <figcaption>待检测原图</figcaption>
                  <img :src="activePreviewUrl || manualResult.inputImageUrl" alt="待检测原图" />
                </figure>
              </div>
              <el-empty v-else-if="manualResult.status === 'SUCCEEDED'" description="模型未定位到目标" :image-size="56" />
              <el-table :data="manualResult.findings" size="small" border>
                <el-table-column label="类型" width="110">
                  <template #default="{ row }: { row: LocateAnythingFinding }">{{ DETECTION_LABELS[row.type] || row.type }}</template>
                </el-table-column>
                <el-table-column label="框上名称" min-width="100" show-overflow-tooltip>
                  <template #default="{ row }: { row: LocateAnythingFinding }">{{ row.label || '-' }}</template>
                </el-table-column>
                <el-table-column label="bbox" min-width="120">
                  <template #default="{ row }: { row: LocateAnythingFinding }">{{ bboxText(row.bbox) }}</template>
                </el-table-column>
              </el-table>
            </div>
            <el-empty v-else description="上传图片并发起检测后，在此查看标注结果" :image-size="64" />
          </div>
        </section>
      </div>
    </el-card>

    <el-collapse v-model="historyPanel" class="history-panel">
      <el-collapse-item name="history">
        <template #title>
          <span class="table-title">最近检测</span>
        </template>
        <div class="history-toolbar">
          <el-button plain size="small" @click="detectionStore.loadRuns({ size: 20 })">刷新</el-button>
        </div>
        <el-table :data="detectionStore.runs" size="small">
          <el-table-column label="来源" width="110">
            <template #default="{ row }: { row: DetectionRun }">{{ row.sourceType === 'ROBOT_IMAGE' ? '机器人图片' : '本地上传' }}</template>
          </el-table-column>
          <el-table-column label="任务 / 检查点" min-width="180">
            <template #default="{ row }: { row: DetectionRun }">{{ runAssociation(row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }: { row: DetectionRun }"><el-tag :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }: { row: DetectionRun }">{{ row.findings.length }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }: { row: DetectionRun }">{{ row.createdAt ? fmt(row.createdAt) : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" class-name="actions-col" fixed="right">
            <template #default="{ row }: { row: DetectionRun }">
              <el-button link type="primary" size="small" @click="openRun(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-dialog v-model="importDialogVisible" title="导入机器人测试图片" width="560px">
      <el-form label-width="90px">
        <el-form-item label="任务">
          <el-select v-model="importForm.taskId" style="width: 100%" @change="onImportTaskChange">
            <el-option v-for="item in taskStore.tasks" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="机器人"><el-input :model-value="importRobotName" disabled /></el-form-item>
        <el-form-item label="检查点">
          <el-select v-model="importForm.checkpointId" style="width: 100%">
            <el-option v-for="item in importCheckpoints" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="图片"><input type="file" accept="image/jpeg,image/png,image/webp,image/bmp" @change="onImportFileChange" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importingImage" @click="importRobotImage">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import DetectionConfig from '@/components/DetectionConfig.vue'
import { resourcesApi } from '@/api/resources'
import { useDetectionStore } from '@/stores/detection'
import { useRouteStore } from '@/stores/route'
import { useRobotStore } from '@/stores/robot'
import { useTaskStore } from '@/stores/task'
import {
  defaultCheckpointDetectionItems,
  ensureDetectionPrompts,
  resolveRobotImageDetectionItems,
} from '@/utils/detectionStrategy'
import {
  DETECTION_LABELS,
  type DetectionItem,
  type DetectionRun,
  type LocateAnythingFinding,
  type ManualDetectionResponse,
  type RobotInspectionImage,
} from '@/types'

const detectionStore = useDetectionStore()
const routeStore = useRouteStore()
const robotStore = useRobotStore()
const taskStore = useTaskStore()
const imagePage = ref(0)
const historyPanel = ref<string[]>([])
const sourceMode = ref<'LOCAL' | 'ROBOT'>('LOCAL')
const sourceOptions = [
  { label: '本地上传', value: 'LOCAL' },
  { label: '机器人图片', value: 'ROBOT' },
]

const fileInputRef = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const detecting = ref(false)
const manualResult = ref<(ManualDetectionResponse | DetectionRun) | null>(null)
const selectedRobotImageId = ref('')
const robotFilter = reactive({ taskId: '', checkpointId: '', robotId: '' })
const importDialogVisible = ref(false)
const importingImage = ref(false)
const importForm = reactive({ taskId: '', robotId: '', checkpointId: '', file: null as File | null })
const manualItems = ref<DetectionItem[]>(defaultCheckpointDetectionItems())
const manualConfigSource = ref('使用默认检查点检测配置')
let manualPollingTimer: ReturnType<typeof setInterval> | null = null

const selectedRobotImage = computed(() => detectionStore.images.find((item) => item.id === selectedRobotImageId.value))
const activePreviewUrl = computed(() => sourceMode.value === 'LOCAL' ? previewUrl.value : selectedRobotImage.value?.imageUrl ?? '')
const robotFilterTask = computed(() => taskStore.getTaskById(robotFilter.taskId))
const robotFilterRoute = computed(() => robotFilterTask.value ? routeStore.getRouteById(robotFilterTask.value.routeId) : undefined)
const robotFilterCheckpoints = computed(() => robotFilterRoute.value?.checkpoints ?? [])
const importTask = computed(() => taskStore.getTaskById(importForm.taskId))
const importRoute = computed(() => importTask.value ? routeStore.getRouteById(importTask.value.routeId) : undefined)
const importCheckpoints = computed(() => importRoute.value?.checkpoints ?? [])
const importRobotName = computed(() => robotStore.getRobotById(importForm.robotId)?.name ?? importForm.robotId)

const manualStatusLabel = computed(() => {
  if (!manualResult.value) return ''
  if (manualResult.value.status === 'RUNNING') return '检测中'
  if (manualResult.value.status === 'SUCCEEDED') return `${manualResult.value.findings.length} 个结果`
  return '检测失败'
})

const manualStatusTagType = computed(() => {
  if (!manualResult.value) return 'info'
  if (manualResult.value.status === 'SUCCEEDED') return 'success'
  if (manualResult.value.status === 'FAILED') return 'danger'
  return 'warning'
})

function updateManualItems(items: DetectionItem[]) {
  manualItems.value = items
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/bmp'].includes(file.type)) {
    ElMessage.warning('仅支持 JPG、PNG、WEBP、BMP 图片')
    input.value = ''
    return
  }
  selectedFile.value = file
  manualResult.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file)
}

async function submitManualDetection() {
  if (sourceMode.value === 'LOCAL' && !selectedFile.value) {
    ElMessage.warning('请先上传检测图片')
    return
  }
  if (sourceMode.value === 'ROBOT' && !selectedRobotImage.value) {
    ElMessage.warning('请先选择机器人图片')
    return
  }
  if (sourceMode.value === 'ROBOT' && !selectedRobotImage.value?.originalAvailable) {
    ElMessage.warning('该原始图片已按保留策略清理，不能再次检测')
    return
  }
  const enabled = manualItems.value.filter((item) => item.enabled)
  if (!enabled.length) {
    ElMessage.warning('请至少启用一个检测项')
    return
  }
  if (enabled.some((item) => !item.displayLabel?.trim())) {
    ElMessage.warning('已启用检测项必须填写框上目标名称')
    return
  }

  detecting.value = true
  const detectionPayload = ensureDetectionPrompts(manualItems.value)
  try {
    if (sourceMode.value === 'ROBOT') {
      manualResult.value = await detectionStore.detectImage(selectedRobotImage.value!.id, detectionPayload)
    } else {
      const payload = new FormData()
      payload.append('image', selectedFile.value!)
      payload.append('detections', JSON.stringify(detectionPayload))
      manualResult.value = await resourcesApi.manualLocateDetection(payload)
    }
    ElMessage.success('检测任务已提交')
    const submitted = manualResult.value
    if (submitted) startManualDetectionPolling(submitted.requestId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型检测失败')
    detecting.value = false
  }
}

function startManualDetectionPolling(requestId: string) {
  stopManualDetectionPolling()
  manualPollingTimer = setInterval(() => {
    void refreshManualDetection(requestId)
  }, 3000)
  void refreshManualDetection(requestId)
}

async function refreshManualDetection(requestId: string) {
  try {
    const result = await detectionStore.getRun(requestId)
    manualResult.value = result
    if (result.status === 'RUNNING') return
    stopManualDetectionPolling()
    detecting.value = false
    if (result.status === 'SUCCEEDED') {
      ElMessage.success('模型检测完成')
    } else {
      ElMessage.error(result.errorMessage || '模型检测失败')
    }
  } catch (error) {
    stopManualDetectionPolling()
    detecting.value = false
    ElMessage.error(error instanceof Error ? error.message : '查询检测结果失败')
  }
}

function stopManualDetectionPolling() {
  if (manualPollingTimer) {
    clearInterval(manualPollingTimer)
    manualPollingTimer = null
  }
}

function resetManualDetection() {
  stopManualDetectionPolling()
  selectedFile.value = null
  selectedRobotImageId.value = ''
  manualResult.value = null
  detecting.value = false
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function loadImagePage(page: number) {
  imagePage.value = page
  void loadRobotImages()
}

async function loadRobotImages() {
  await detectionStore.loadImages({
    page: imagePage.value,
    size: 12,
    taskId: robotFilter.taskId,
    checkpointId: robotFilter.checkpointId,
    robotId: robotFilter.robotId,
  })
}

function onRobotTaskChange() {
  const task = taskStore.getTaskById(robotFilter.taskId)
  robotFilter.robotId = task?.robotId ?? ''
  robotFilter.checkpointId = ''
  imagePage.value = 0
  void loadRobotImages()
}

async function selectRobotImage(image: RobotInspectionImage) {
  stopManualDetectionPolling()
  selectedRobotImageId.value = image.id
  manualResult.value = null
  detecting.value = false
  let route = routeStore.getRouteById(image.routeId)
  if (!route) {
    try {
      route = await routeStore.loadOne(image.routeId)
    } catch {
      route = undefined
    }
  }
  if (selectedRobotImageId.value !== image.id) return
  const checkpoint = route?.checkpoints.find((item) => item.id === image.checkpointId)
  manualItems.value = resolveRobotImageDetectionItems(image, route)
  manualConfigSource.value = checkpoint?.detections?.length
    ? `已加载检查点“${checkpoint.name}”的默认检测配置，可在本次检测前临时修改`
    : '该检查点尚未配置检测项，已使用默认 5 项检测配置'
}

function openImportDialog() {
  const task = taskStore.getTaskById(robotFilter.taskId) ?? taskStore.tasks[0]
  importForm.taskId = task?.id ?? ''
  importForm.robotId = task?.robotId ?? ''
  importForm.checkpointId = importCheckpoints.value[0]?.id ?? ''
  importForm.file = null
  importDialogVisible.value = true
}

function onImportTaskChange() {
  importForm.robotId = importTask.value?.robotId ?? ''
  importForm.checkpointId = importCheckpoints.value[0]?.id ?? ''
}

function onImportFileChange(event: Event) {
  importForm.file = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function importRobotImage() {
  if (!importForm.taskId || !importForm.robotId || !importForm.checkpointId || !importForm.file) {
    ElMessage.warning('请选择任务、检查点和图片')
    return
  }
  const payload = new FormData()
  payload.append('image', importForm.file)
  payload.append('taskId', importForm.taskId)
  payload.append('robotId', importForm.robotId)
  payload.append('checkpointId', importForm.checkpointId)
  payload.append('capturedAt', new Date().toISOString())
  importingImage.value = true
  try {
    const saved = await detectionStore.importImage(payload)
    robotFilter.taskId = saved.taskId
    robotFilter.robotId = saved.robotId
    robotFilter.checkpointId = saved.checkpointId
    selectedRobotImageId.value = saved.id
    importDialogVisible.value = false
    await loadRobotImages()
    await selectRobotImage(saved)
    ElMessage.success('测试图片已导入')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试图片导入失败')
  } finally {
    importingImage.value = false
  }
}

function fmt(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function statusLabel(status: DetectionRun['status']) {
  return status === 'RUNNING' ? '检测中' : status === 'SUCCEEDED' ? '成功' : '失败'
}

function statusTagType(status: DetectionRun['status']) {
  return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
}

function runAssociation(run: DetectionRun) {
  if (!run.taskId) return '-'
  const taskName = taskStore.getTaskById(run.taskId)?.name ?? run.taskId
  const checkpointName = routeStore.routes.flatMap((route) => route.checkpoints)
    .find((item) => item.id === run.checkpointId)?.name ?? run.checkpointId ?? '-'
  return `${taskName} / ${checkpointName}`
}

function openRun(run: DetectionRun) {
  stopManualDetectionPolling()
  manualResult.value = run
  detecting.value = run.status === 'RUNNING'
  if (run.status === 'RUNNING') startManualDetectionPolling(run.runId)
}

function bboxText(bbox: number[]) {
  return bbox && bbox.length ? bbox.join(', ') : '-'
}

onMounted(() => {
  void detectionStore.loadRuns({ size: 20 })
})

onBeforeUnmount(() => {
  stopManualDetectionPolling()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

watch(sourceMode, (mode) => {
  resetManualDetection()
  manualItems.value = defaultCheckpointDetectionItems()
  manualConfigSource.value = '使用默认检查点检测配置'
  if (mode === 'ROBOT') void loadRobotImages()
})
</script>
<style scoped>
.detection-page {
  --panel-border: var(--pi-border-soft, #d7dde8);
  --panel-soft: #f6f8fb;
}

.workbench-card {
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
}

.workbench-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--pi-border-soft);
  background: #fafbfc;
}

.workbench-card :deep(.el-card__body) {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-header p {
  margin: 6px 0 0;
  color: var(--pi-muted);
  font-size: 13px;
}

.detection-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(300px, 1fr) minmax(300px, 1fr);
  gap: 16px;
  align-items: stretch;
}

.media-pane,
.config-pane,
.result-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 480px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.config-pane,
.result-pane {
  background: var(--panel-soft);
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--panel-border);
  background: #fafbfc;
  flex-shrink: 0;
}

.pane-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--pi-text, #1f2a37);
}

.config-body,
.result-body {
  flex: 1;
  min-height: 0;
  padding: 12px 14px;
  overflow: auto;
}

.config-body {
  display: flex;
  flex-direction: column;
}

.config-source {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
  flex-shrink: 0;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--panel-border);
  background: #fafbfc;
  flex-shrink: 0;
}

.upload-panel {
  flex: 1;
  min-height: 0;
  border: none;
  background: var(--panel-soft);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.robot-source-panel {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
  overflow: auto;
}

.robot-filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
  flex-shrink: 0;
}

.robot-preview {
  border: 1px solid var(--panel-border);
  background: var(--panel-soft);
  margin-bottom: 10px;
  flex-shrink: 0;
}

.robot-preview img {
  display: block;
  width: 100%;
  height: 180px;
  object-fit: contain;
  background: #0f172a;
}

.robot-preview > div {
  padding: 7px 9px;
  font-size: 12px;
  color: #667085;
}

.robot-image-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  flex-shrink: 0;
}

.robot-image-item {
  min-width: 0;
  height: 92px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  background: #f8fafc;
  cursor: pointer;
  color: #667085;
}

.robot-image-item.selected {
  border: 2px solid #2f6fed;
}

.robot-image-item img {
  display: block;
  width: 100%;
  height: 64px;
  object-fit: cover;
}

.robot-image-item small {
  display: block;
  padding: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-input {
  display: none;
}

.upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #667085;
}

.upload-empty .el-icon {
  font-size: 34px;
  color: #2f6fed;
}

.upload-empty strong {
  color: #1f2a37;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0f172a;
}

.result-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-image figure {
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  background: #f8fafc;
}

.result-image figcaption {
  padding: 8px 10px;
  font-size: 12px;
  color: #667085;
  border-bottom: 1px solid var(--panel-border);
}

.result-image img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: contain;
  background: #0f172a;
}

.history-panel {
  margin-top: 16px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  overflow: hidden;
}

.history-panel :deep(.el-collapse-item__header) {
  padding: 0 16px;
  height: 48px;
  background: #fafbfc;
  border-bottom: 1px solid var(--pi-border-soft);
}

.history-panel :deep(.el-collapse-item__wrap) {
  border: none;
}

.history-panel :deep(.el-collapse-item__content) {
  padding: 12px 16px 16px;
}

.history-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

@media (max-width: 1200px) {
  .detection-workbench {
    grid-template-columns: 1fr;
  }

  .media-pane,
  .config-pane,
  .result-pane {
    min-height: auto;
  }

  .upload-panel {
    min-height: 280px;
  }

  .robot-source-panel {
    min-height: 360px;
  }
}
</style>
