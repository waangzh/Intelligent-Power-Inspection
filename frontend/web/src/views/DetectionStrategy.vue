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
            <div class="template-toolbar">
              <el-select
                v-model="selectedTemplateId"
                clearable
                placeholder="选择检测模板"
                @change="applySelectedTemplate"
              >
                <el-option
                  v-for="template in checkpointTemplates"
                  :key="template.id"
                  :label="template.name"
                  :value="template.id"
                />
              </el-select>
              <div class="template-actions">
                <el-button size="small" :disabled="!selectedTemplateId" :loading="templateSaving" @click="saveSelectedTemplate">
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
                <el-button size="small" @click="openSaveAsTemplate">
                  <el-icon><CopyDocument /></el-icon>
                  另存为
                </el-button>
                <el-tooltip content="删除当前模板" placement="top">
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    circle
                    :disabled="!selectedTemplateId"
                    aria-label="删除当前检测模板"
                    @click="removeSelectedTemplate"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>
            <div v-if="sourceMode === 'ROBOT' && selectedRobotImage" class="config-source">
              <span>{{ manualConfigSource }}</span>
              <el-button
                size="small"
                :loading="checkpointSaving"
                @click="saveCheckpointDetectionConfig"
              >
                <el-icon><Check /></el-icon>
                保存到检查点
              </el-button>
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
              <div
                v-if="manualResult.status === 'RUNNING'"
                class="running-progress"
                role="status"
                aria-live="polite"
              >
                <div class="running-progress__header">
                  <strong>模型检测中</strong>
                  <span>{{ manualElapsedLabel }}</span>
                </div>
                <div class="running-progress__track" aria-hidden="true">
                  <span class="running-progress__indicator" />
                </div>
                <small>结果会自动刷新</small>
              </div>
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
              <div v-if="manualResult.status === 'SUCCEEDED'" class="result-alarm-summary">
                <span>本次生成 {{ manualResult.alarmCount ?? 0 }} 条告警</span>
                <el-button
                  v-if="(manualResult.alarmCount ?? 0) > 0"
                  link
                  type="primary"
                  @click="openRunAlarms(manualResult)"
                >查看告警</el-button>
              </div>
              <div v-if="manualResult.resultImageUrl" class="result-image">
                <figure>
                  <figcaption>检测结果（所有目标已合并标注）</figcaption>
                  <div class="result-image__canvas">
                    <el-image
                      ref="resultImageRef"
                      :src="manualResult.resultImageUrl"
                      :preview-src-list="[manualResult.resultImageUrl]"
                      fit="contain"
                      class="result-image__preview"
                      alt="合并标注结果图"
                    />
                    <el-tooltip content="放大查看" placement="top">
                      <el-button
                        class="result-image__zoom"
                        circle
                        size="small"
                        aria-label="放大查看检测结果"
                        @click="openResultImagePreview"
                      >
                        <el-icon><ZoomIn /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
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
              <template #default="{ row }: { row: DetectionRun }">{{ runSourceLabel(row.sourceType) }}</template>
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
          <el-table-column label="告警" width="90">
            <template #default="{ row }: { row: DetectionRun }">
              <el-button
                v-if="row.status === 'SUCCEEDED' && row.alarmCount > 0"
                link
                type="primary"
                size="small"
                @click="openRunAlarms(row)"
              >{{ row.alarmCount }}</el-button>
              <span v-else>{{ row.status === 'SUCCEEDED' ? row.alarmCount : '-' }}</span>
            </template>
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

    <el-dialog v-model="saveTemplateDialogVisible" title="另存为检测模板" width="520px">
      <el-form label-width="90px">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.name" maxlength="80" placeholder="例如：人员专项检测" />
        </el-form-item>
        <el-form-item label="模板说明">
          <el-input v-model="templateForm.description" type="textarea" :rows="3" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveTemplateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateSaving" @click="createDetectionTemplate">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, CopyDocument, Delete, Search, UploadFilled, ZoomIn } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import DetectionConfig from '@/components/DetectionConfig.vue'
import { resourcesApi } from '@/api/resources'
import { useDetectionStore } from '@/stores/detection'
import { useRouteStore } from '@/stores/route'
import { useRobotStore } from '@/stores/robot'
import { useTaskStore } from '@/stores/task'
import {
  cloneDetectionItems,
  defaultCheckpointDetectionItems,
  ensureDetectionPrompts,
  formatDetectionElapsed,
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
const route = useRoute()
const router = useRouter()
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
const selectedTemplateId = ref('')
const saveTemplateDialogVisible = ref(false)
const templateSaving = ref(false)
const checkpointSaving = ref(false)
const templateForm = reactive({ name: '', description: '' })
const resultImageRef = ref<{ showPreview?: () => void } | null>(null)
const manualRunStartedAt = ref<number | null>(null)
const elapsedNow = ref(Date.now())
let manualPollingTimer: ReturnType<typeof setInterval> | null = null
let manualElapsedTimer: ReturnType<typeof setInterval> | null = null

const selectedRobotImage = computed(() => detectionStore.images.find((item) => item.id === selectedRobotImageId.value))
const activePreviewUrl = computed(() => sourceMode.value === 'LOCAL' ? previewUrl.value : selectedRobotImage.value?.imageUrl ?? '')
const robotFilterTask = computed(() => taskStore.getTaskById(robotFilter.taskId))
const robotFilterRoute = computed(() => robotFilterTask.value ? routeStore.getRouteById(robotFilterTask.value.routeId) : undefined)
const robotFilterCheckpoints = computed(() => robotFilterRoute.value?.checkpoints ?? [])
const importTask = computed(() => taskStore.getTaskById(importForm.taskId))
const importRoute = computed(() => importTask.value ? routeStore.getRouteById(importTask.value.routeId) : undefined)
const importCheckpoints = computed(() => importRoute.value?.checkpoints ?? [])
const importRobotName = computed(() => robotStore.getRobotById(importForm.robotId)?.name ?? importForm.robotId)
const checkpointTemplates = computed(() => detectionStore.templates.filter((template) => template.scope === 'CHECKPOINT'))

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

const manualElapsedLabel = computed(() => formatDetectionElapsed(
  manualResult.value?.startedAt ?? manualRunStartedAt.value ?? undefined,
  elapsedNow.value,
))

function openResultImagePreview() {
  resultImageRef.value?.showPreview?.()
}

function resultRunId(result: ManualDetectionResponse | DetectionRun) {
  return 'runId' in result ? result.runId : result.requestId
}

function openRunAlarms(result: ManualDetectionResponse | DetectionRun) {
  void router.push({ path: '/alarms', query: { detectionRunId: resultRunId(result) } })
}

function updateManualItems(items: DetectionItem[]) {
  manualItems.value = items
}

function templatePayload(name: string, description: string) {
  const items = manualItems.value.map((item) => ({ ...item }))
  return {
    name: name.trim(),
    scope: 'CHECKPOINT' as const,
    description: description.trim(),
    items,
    types: items.map((item) => item.type),
    prompts: Object.fromEntries(items.filter((item) => item.prompt).map((item) => [item.type, item.prompt!])),
  }
}

function applySelectedTemplate() {
  const template = checkpointTemplates.value.find((item) => item.id === selectedTemplateId.value)
  if (!template) return
  manualItems.value = cloneDetectionItems(template.items)
  manualConfigSource.value = `已加载模板“${template.name}”，本次修改需点击保存才会更新模板`
}

async function saveSelectedTemplate() {
  const template = checkpointTemplates.value.find((item) => item.id === selectedTemplateId.value)
  if (!template || !validateTemplateItems()) return
  templateSaving.value = true
  try {
    await detectionStore.updateTemplate(template.id, templatePayload(template.name, template.description))
    ElMessage.success('检测模板已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '检测模板保存失败')
  } finally {
    templateSaving.value = false
  }
}

async function saveCheckpointDetectionConfig() {
  const image = selectedRobotImage.value
  if (!image || !validateTemplateItems()) return
  checkpointSaving.value = true
  try {
    let sourceRoute = routeStore.getRouteById(image.routeId)
    if (!sourceRoute) sourceRoute = await routeStore.loadOne(image.routeId)
    const checkpoint = sourceRoute.checkpoints.find((item) => item.id === image.checkpointId)
    if (!checkpoint) throw new Error('图片关联的检查点不存在')
    await routeStore.updateCheckpoint(sourceRoute.id, checkpoint.id, {
      detections: cloneDetectionItems(manualItems.value),
    })
    manualConfigSource.value = `已保存为检查点“${checkpoint.name}”的正式任务检测配置`
    ElMessage.success('检查点检测配置已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '检查点检测配置保存失败')
  } finally {
    checkpointSaving.value = false
  }
}

function openSaveAsTemplate() {
  if (!validateTemplateItems()) return
  const selected = checkpointTemplates.value.find((item) => item.id === selectedTemplateId.value)
  templateForm.name = selected ? `${selected.name} 副本` : ''
  templateForm.description = selected?.description ?? ''
  saveTemplateDialogVisible.value = true
}

async function createDetectionTemplate() {
  if (!templateForm.name.trim() || !validateTemplateItems()) {
    if (!templateForm.name.trim()) ElMessage.warning('请填写模板名称')
    return
  }
  templateSaving.value = true
  try {
    const saved = await detectionStore.addTemplate(templatePayload(templateForm.name, templateForm.description))
    selectedTemplateId.value = saved.id
    manualConfigSource.value = `已加载模板“${saved.name}”`
    saveTemplateDialogVisible.value = false
    ElMessage.success('检测模板已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '检测模板创建失败')
  } finally {
    templateSaving.value = false
  }
}

async function removeSelectedTemplate() {
  const template = checkpointTemplates.value.find((item) => item.id === selectedTemplateId.value)
  if (!template) return
  try {
    await ElMessageBox.confirm(`确认删除检测模板“${template.name}”？`, '删除模板', { type: 'warning' })
  } catch {
    return
  }
  await detectionStore.removeTemplate(template.id)
  selectedTemplateId.value = ''
  manualItems.value = defaultCheckpointDetectionItems()
  manualConfigSource.value = '使用默认检查点检测配置'
  ElMessage.success('检测模板已删除')
}

function validateTemplateItems() {
  if (!manualItems.value.length) {
    ElMessage.warning('模板至少需要一个检测项')
    return false
  }
  manualItems.value = cloneDetectionItems(manualItems.value)
  const invalid = manualItems.value.find((item) => !item.name?.trim() || !item.displayLabel.trim() || !item.prompt?.trim())
  if (invalid) {
    ElMessage.warning('每个检测项都必须填写名称、框上名称和 Prompt')
    return false
  }
  return true
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
  manualItems.value = cloneDetectionItems(manualItems.value)
  const enabled = manualItems.value.filter((item) => item.enabled)
  if (!enabled.length) {
    ElMessage.warning('请至少启用一个检测项')
    return
  }
  if (enabled.some((item) => !item.name?.trim() || !item.displayLabel?.trim() || !item.prompt?.trim())) {
    ElMessage.warning('已启用检测项必须填写名称、框上名称和 Prompt')
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
  manualRunStartedAt.value ??= Date.now()
  elapsedNow.value = Date.now()
  if (!manualElapsedTimer) {
    manualElapsedTimer = setInterval(() => {
      elapsedNow.value = Date.now()
    }, 1000)
  }
  manualPollingTimer = setInterval(() => {
    void refreshManualDetection(requestId)
  }, 3000)
  void refreshManualDetection(requestId)
}

async function refreshManualDetection(requestId: string) {
  try {
    const result = requestId.startsWith('manual_det_')
      ? await resourcesApi.getManualLocateDetection(requestId)
      : await detectionStore.getRun(requestId)
    manualResult.value = result
    if (result.status === 'RUNNING') return
    stopManualDetectionPolling()
    stopManualElapsedTimer()
    manualRunStartedAt.value = null
    detecting.value = false
    if (result.status === 'SUCCEEDED') {
      ElMessage.success('模型检测完成')
    } else {
      ElMessage.error(result.errorMessage || '模型检测失败')
    }
  } catch (error) {
    stopManualDetectionPolling()
    stopManualElapsedTimer()
    manualRunStartedAt.value = null
    detecting.value = false
    ElMessage.error(error instanceof Error ? error.message : '查询检测结果失败')
  }
}

function stopManualElapsedTimer() {
  if (manualElapsedTimer) {
    clearInterval(manualElapsedTimer)
    manualElapsedTimer = null
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
  stopManualElapsedTimer()
  manualRunStartedAt.value = null
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
  stopManualElapsedTimer()
  manualRunStartedAt.value = null
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

function runSourceLabel(sourceType: DetectionRun['sourceType']) {
  if (sourceType === 'ROBOT_IMAGE') return '机器人图片'
  if (sourceType === 'TASK_CHECKPOINT') return '正式任务'
  return '本地上传'
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
  stopManualElapsedTimer()
  manualRunStartedAt.value = null
  manualResult.value = run
  detecting.value = run.status === 'RUNNING'
  if (run.status === 'RUNNING') startManualDetectionPolling(run.runId)
}

function bboxText(bbox: number[]) {
  return bbox && bbox.length ? bbox.join(', ') : '-'
}

async function openRunFromQuery() {
  const rawRunId = route.query.runId
  const runId = Array.isArray(rawRunId) ? rawRunId[0] : rawRunId
  if (!runId) return
  try {
    const result = runId.startsWith('manual_det_')
      ? await resourcesApi.getManualLocateDetection(runId)
      : await detectionStore.getRun(runId)
    manualResult.value = result
    detecting.value = result.status === 'RUNNING'
    if (result.status === 'RUNNING') startManualDetectionPolling(result.requestId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询检测结果失败')
  }
}

onMounted(() => {
  void detectionStore.load({ size: 100 })
  void detectionStore.loadRuns({ size: 20 })
})

watch(() => route.query.runId, () => void openRunFromQuery(), { immediate: true })

onBeforeUnmount(() => {
  stopManualDetectionPolling()
  stopManualElapsedTimer()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

watch(sourceMode, (mode) => {
  resetManualDetection()
  manualItems.value = defaultCheckpointDetectionItems()
  selectedTemplateId.value = ''
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

.template-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.template-toolbar .el-select {
  flex: 1;
  min-width: 0;
}

.template-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.config-source {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
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

.result-alarm-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  padding: 6px 10px;
  border: 1px solid #b3e19d;
  border-radius: 6px;
  background: #f0f9eb;
  color: #529b2e;
  font-size: 13px;
}

.running-progress {
  padding: 12px 14px;
  border: 1px solid #a0cfff;
  border-radius: 6px;
  background: #ecf5ff;
  color: #337ecc;
}

.running-progress__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.running-progress__header span,
.running-progress small {
  font-size: 12px;
}

.running-progress__track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #d9ecff;
}

.running-progress__indicator {
  display: block;
  width: 28%;
  height: 100%;
  border-radius: inherit;
  background: #409eff;
  animation: running-progress-slide 1.5s ease-in-out infinite;
}

.running-progress small {
  display: block;
  margin-top: 8px;
  color: #606266;
}

@keyframes running-progress-slide {
  from {
    transform: translateX(-110%);
  }

  to {
    transform: translateX(360%);
  }
}

.result-image figure {
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  background: #f8fafc;
}

.result-image figcaption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 12px;
  color: #667085;
  border-bottom: 1px solid var(--panel-border);
}

.result-image__canvas {
  position: relative;
}

.result-image__preview {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #0f172a;
}

.result-image__preview :deep(img) {
  display: block;
  width: 100%;
  object-fit: contain;
}

.result-image__zoom {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1;
  border-color: rgba(255, 255, 255, 0.8);
  background: rgba(15, 23, 42, 0.7);
  color: #fff;
}

.result-image__zoom:hover,
.result-image__zoom:focus-visible {
  border-color: #fff;
  background: rgba(15, 23, 42, 0.9);
  color: #fff;
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
