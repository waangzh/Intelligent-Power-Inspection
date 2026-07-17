<template>
  <div class="detection-page">
    <PageHeader
      title="检测策略"
      description="维护检查点检测模板，并通过 LocateAnything 真实模型服务验证上传图片的定位结果。"
      :breadcrumbs="[{ label: '资产感知' }, { label: '检测策略' }]"
    >
      <template #actions>
        <el-button v-if="can('detection:manage')" type="primary" @click="openCreateTemplate">
          <el-icon><Plus /></el-icon>
          新建模板
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" class="top-row">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <span class="card-title">手动上传检测</span>
                <p>上传巡检图片，选择检测项后调用后端 HTTP 网关转发至 Python 模型服务。</p>
              </div>
              <el-tag type="success" effect="plain">HTTP 模型网关</el-tag>
            </div>
          </template>

          <div class="manual-grid">
            <div class="upload-panel" @click="triggerFileInput">
              <input ref="fileInputRef" class="file-input" type="file" accept="image/jpeg,image/png,image/webp,image/bmp" @change="onFileChange" />
              <img v-if="previewUrl" :src="previewUrl" class="preview-img" alt="待检测图片" />
              <div v-else class="upload-empty">
                <el-icon><UploadFilled /></el-icon>
                <strong>选择检测图片</strong>
                <span>支持 JPG、PNG、WEBP、BMP</span>
              </div>
            </div>

            <div class="manual-controls">
              <DetectionConfig :items="manualItems" @change="updateManualItems" />
              <div class="action-bar">
                <el-button @click="resetManualDetection">清空</el-button>
                <el-button type="primary" :loading="detecting" @click="submitManualDetection">
                  <el-icon><Search /></el-icon>
                  调用模型检测
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header compact">
              <span class="card-title">检测结果</span>
              <el-tag v-if="manualResult" :type="manualStatusTagType" effect="plain">{{ manualStatusLabel }}</el-tag>
            </div>
          </template>

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
                <img :src="previewUrl || manualResult.inputImageUrl" alt="待检测原图" />
              </figure>
            </div>
            <el-empty v-else-if="manualResult.status === 'SUCCEEDED'" description="模型未定位到目标" :image-size="56" />
            <el-table :data="manualResult.findings" size="small" border>
              <el-table-column label="类型" width="110">
                <template #default="{ row }: { row: LocateAnythingFinding }">{{ DETECTION_LABELS[row.type] || row.type }}</template>
              </el-table-column>
              <el-table-column prop="prompt" label="提示词" min-width="120" show-overflow-tooltip />
              <el-table-column label="bbox" min-width="130">
                <template #default="{ row }: { row: LocateAnythingFinding }">{{ bboxText(row.bbox) }}</template>
              </el-table-column>
              <el-table-column label="模型输出" min-width="160" show-overflow-tooltip>
                <template #default="{ row }: { row: LocateAnythingFinding }">{{ rawSummary(row) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <el-empty v-else description="上传图片后查看模型返回的标注图和检测框" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="top-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>LocateAnything 说明</template>
          <p class="info-text">LocateAnything 支持自然语言提示定位复杂目标。检查点检测中可通过目标外观和所在区域描述提示词，用于开放词汇定位。</p>
          <el-link href="https://huggingface.co/nvidia/LocateAnything-3B" target="_blank" type="primary">查看模型文档</el-link>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>检测层级</template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="路线级">人员、安全帽、障碍物、火源（行进中持续）</el-descriptions-item>
            <el-descriptions-item label="检查点级">开关、表计、漏油、烟火、异物（到点触发）</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>检测模板</template>
      <el-table :data="detectionStore.templates" size="small">
        <el-table-column prop="name" label="模板名称" min-width="140" />
        <el-table-column label="适用范围" width="110">
          <template #default="{ row }"><el-tag size="small">{{ row.scope === 'ROUTE' ? '路线级' : '检查点级' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="检测项" min-width="200">
          <template #default="{ row }: { row: import('@/types').DetectionTemplate }">
            <el-tag
              v-for="item in row.items"
              :key="item.type"
              :type="item.enabled ? 'success' : 'info'"
              size="small"
              style="margin: 2px"
            >
              {{ DETECTION_LABELS[item.type] }}{{ item.enabled ? '' : '（停用）' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="提示词" min-width="180">
          <template #default="{ row }: { row: DetectionTemplate }">
            <span v-for="item in row.items" :key="item.type" class="prompt">{{ DETECTION_LABELS[item.type] }}: {{ item.prompt || '-' }} </span>
          </template>
        </el-table-column>
        <el-table-column v-if="can('detection:manage')" label="操作" width="210">
          <template #default="{ row }: { row: DetectionTemplate }">
            <el-button v-if="row.scope === 'CHECKPOINT' && can('route:edit')" text type="success" size="small" @click="openApplyTemplate(row)">应用</el-button>
            <el-button text type="primary" size="small" @click="openEditTemplate(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="removeTemplate(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPagination :total="detectionStore.total" :page="templatePage" @change="loadTemplatePage" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingTemplateId ? '编辑检测模板' : '新建检测模板'" width="760px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="范围">
          <el-radio-group v-model="form.scope" @change="resetTemplateItems"><el-radio value="ROUTE">路线级</el-radio><el-radio value="CHECKPOINT">检查点级</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="检测项"><DetectionConfig :items="form.items" @change="updateTemplateItems" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="savingTemplate" @click="saveTemplate">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="applyDialogVisible" title="应用模板到检查点" width="520px">
      <el-form label-width="90px">
        <el-form-item label="模板"><el-input :model-value="applyTemplateName" disabled /></el-form-item>
        <el-form-item label="路线">
          <el-select v-model="applyForm.routeId" placeholder="选择路线" style="width: 100%" @change="applyForm.checkpointId = ''">
            <el-option v-for="route in checkpointRoutes" :key="route.id" :label="route.name" :value="route.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查点">
          <el-select v-model="applyForm.checkpointId" placeholder="选择检查点" style="width: 100%">
            <el-option v-for="checkpoint in applyCheckpoints" :key="checkpoint.id" :label="checkpoint.name" :value="checkpoint.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="applyDialogVisible = false">取消</el-button><el-button type="primary" :loading="applyingTemplate" @click="applyTemplateToCheckpoint">应用</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import DetectionConfig from '@/components/DetectionConfig.vue'
import { resourcesApi } from '@/api/resources'
import { usePermission } from '@/composables/usePermission'
import { useDetectionStore } from '@/stores/detection'
import { useRouteStore } from '@/stores/route'
import {
  CHECKPOINT_DETECTIONS,
  DETECTION_LABELS,
  DETECTION_TARGET_LABELS,
  ROUTE_DETECTIONS,
  type DetectionItem,
  type DetectionTemplate,
  type DetectionType,
  type LocateAnythingFinding,
  type ManualDetectionResponse,
} from '@/types'

const detectionStore = useDetectionStore()
const routeStore = useRouteStore()
const templatePage = ref(0)

function loadTemplatePage(page: number) {
  templatePage.value = page
  void detectionStore.load({ page, size: 20 })
}
const { can } = usePermission()
const dialogVisible = ref(false)
const editingTemplateId = ref<string | null>(null)
const savingTemplate = ref(false)
const form = reactive({
  name: '',
  scope: 'CHECKPOINT' as 'ROUTE' | 'CHECKPOINT',
  description: '',
  items: defaultDetectionItems('CHECKPOINT'),
})
const applyDialogVisible = ref(false)
const applyingTemplate = ref(false)
const applyForm = reactive({ templateId: '', routeId: '', checkpointId: '' })
const fileInputRef = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const detecting = ref(false)
const manualResult = ref<ManualDetectionResponse | null>(null)
const manualItems = ref<DetectionItem[]>(CHECKPOINT_DETECTIONS.map((type) => defaultDetectionItem(type)))
let manualPollingTimer: ReturnType<typeof setInterval> | null = null

const checkpointRoutes = computed(() => routeStore.routes.filter((route) => route.checkpoints.length > 0))
const applyCheckpoints = computed(() => routeStore.getRouteById(applyForm.routeId)?.checkpoints ?? [])
const applyTemplateName = computed(() => detectionStore.templates.find((template) => template.id === applyForm.templateId)?.name ?? '')

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

function defaultDetectionItem(type: DetectionType): DetectionItem {
  const promptMap: Record<DetectionType, string> = {
    PERSON: '巡检区域内的人员',
    HELMET: '人员头部佩戴的安全帽',
    OBSTACLE: '机器人行进路线上的障碍物',
    FIRE: '图像中清晰可见的火焰、火光或明显烟雾区域',
    SWITCH: '变电设备上的刀闸开关操作手柄、连杆及触头区域',
    METER: '圆形机械压力表的完整表盘和指针区域',
    OIL_LEAK: '变压器或电气设备表面、法兰、阀门、接口及底部可见的油渍、油迹或积油区域',
    FOREIGN_OBJECT: '设备操作区域内不属于设备本体的遗留物，例如工具、纸箱、塑料袋、布料或其他杂物',
  }
  return {
    type,
    enabled: true,
    displayLabel: DETECTION_TARGET_LABELS[type],
    prompt: promptMap[type],
    threshold: 0.75,
  }
}

function defaultDetectionItems(scope: 'ROUTE' | 'CHECKPOINT') {
  const types = scope === 'ROUTE' ? ROUTE_DETECTIONS : CHECKPOINT_DETECTIONS
  return types.map((type) => defaultDetectionItem(type))
}

function cloneDetectionItems(items: DetectionItem[]) {
  return items.map((item) => ({ ...item, threshold: 0.75 }))
}

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
  if (!selectedFile.value) {
    ElMessage.warning('请先上传检测图片')
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

  const payload = new FormData()
  payload.append('image', selectedFile.value)
  payload.append('detections', JSON.stringify(manualItems.value))
  detecting.value = true
  try {
    manualResult.value = await resourcesApi.manualLocateDetection(payload)
    ElMessage.success('检测任务已提交')
    startManualDetectionPolling(manualResult.value.requestId)
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
    const result = await resourcesApi.getManualLocateDetection(requestId)
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
  manualResult.value = null
  detecting.value = false
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function bboxText(bbox: number[]) {
  return bbox && bbox.length ? bbox.join(', ') : '-'
}

function rawSummary(row: LocateAnythingFinding) {
  const rawAnswer = row.rawResult?.rawAnswer
  return typeof rawAnswer === 'string' ? rawAnswer : '-'
}

function openCreateTemplate() {
  editingTemplateId.value = null
  form.name = ''
  form.scope = 'CHECKPOINT'
  form.description = ''
  form.items = defaultDetectionItems('CHECKPOINT')
  dialogVisible.value = true
}

function openEditTemplate(template: DetectionTemplate) {
  editingTemplateId.value = template.id
  form.name = template.name
  form.scope = template.scope
  form.description = template.description
  form.items = cloneDetectionItems(template.items)
  dialogVisible.value = true
}

function resetTemplateItems() {
  form.items = defaultDetectionItems(form.scope)
}

function updateTemplateItems(items: DetectionItem[]) {
  form.items = items
}

async function saveTemplate() {
  if (!form.name) {
    ElMessage.warning('请填写名称')
    return
  }
  if (form.items.some((item) => item.enabled && !item.prompt?.trim())) {
    ElMessage.warning('已启用检测项必须填写提示词')
    return
  }
  if (form.items.some((item) => item.enabled && !item.displayLabel?.trim())) {
    ElMessage.warning('已启用检测项必须填写框上目标名称')
    return
  }
  const items = cloneDetectionItems(form.items)
  const payload = {
    name: form.name.trim(),
    scope: form.scope,
    description: form.description.trim(),
    items,
    types: items.map((item) => item.type),
    prompts: Object.fromEntries(items.filter((item) => item.prompt?.trim()).map((item) => [item.type, item.prompt!.trim()])),
  }
  savingTemplate.value = true
  try {
    if (editingTemplateId.value) {
      await detectionStore.updateTemplate(editingTemplateId.value, payload)
      ElMessage.success('模板已更新')
    } else {
      await detectionStore.addTemplate(payload)
      ElMessage.success('模板已创建')
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模板保存失败')
  } finally {
    savingTemplate.value = false
  }
}

async function removeTemplate(id: string) {
  try {
    await detectionStore.removeTemplate(id)
    ElMessage.success('模板已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模板删除失败')
  }
}

function openApplyTemplate(template: DetectionTemplate) {
  applyForm.templateId = template.id
  applyForm.routeId = checkpointRoutes.value[0]?.id ?? ''
  applyForm.checkpointId = applyCheckpoints.value[0]?.id ?? ''
  applyDialogVisible.value = true
}

async function applyTemplateToCheckpoint() {
  const template = detectionStore.templates.find((item) => item.id === applyForm.templateId)
  if (!template || !applyForm.routeId || !applyForm.checkpointId) {
    ElMessage.warning('请选择路线和检查点')
    return
  }
  applyingTemplate.value = true
  try {
    await routeStore.updateCheckpoint(applyForm.routeId, applyForm.checkpointId, {
      detections: cloneDetectionItems(template.items),
    })
    applyDialogVisible.value = false
    ElMessage.success('检测模板已应用到检查点')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '应用模板失败')
  } finally {
    applyingTemplate.value = false
  }
}

onBeforeUnmount(() => {
  stopManualDetectionPolling()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>
<style scoped>
.detection-page {
  --panel-border: #d7dde8;
  --panel-soft: #f6f8fb;
}

.top-row {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-header.compact {
  align-items: center;
}

.card-title {
  font-weight: 700;
  color: #1f2a37;
}

.card-header p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.manual-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(320px, 1.2fr);
  gap: 16px;
}

.upload-panel {
  min-height: 322px;
  border: 1px dashed var(--panel-border);
  background: var(--panel-soft);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
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

.preview-img,
.image-compare img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0f172a;
}

.manual-controls {
  min-width: 0;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.result-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.image-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.image-compare figure {
  margin: 0;
  border: 1px solid var(--panel-border);
  background: #f8fafc;
}

.image-compare figcaption {
  padding: 8px 10px;
  font-size: 12px;
  color: #667085;
  border-bottom: 1px solid var(--panel-border);
}

.image-compare img,
.no-result {
  aspect-ratio: 4 / 3;
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

.no-result {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #98a2b3;
}

.info-text {
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
  margin: 0 0 12px;
}

.prompt {
  font-size: 12px;
  color: #909399;
}

@media (max-width: 960px) {
  .manual-grid,
  .image-compare {
    grid-template-columns: 1fr;
  }
}
</style>
