<template>
  <div class="detection-config">
    <div class="config-toolbar">
      <span>检测项</span>
      <el-button type="primary" plain size="small" @click="openAdd">
        <el-icon><Plus /></el-icon>
        添加检测项
      </el-button>
    </div>
    <el-alert
      class="confidence-notice"
      title="当前模型不返回可校准置信度，策略按启用状态与框上目标名称执行。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-table :data="items" size="small" border class="config-table">
      <el-table-column label="检测项" min-width="120">
        <template #default="{ row }: { row: DetectionItem }">
          {{ row.name || DETECTION_LABELS[row.type] || row.type }}
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }: { row: DetectionItem }">
          <el-switch v-model="row.enabled" size="small" @change="emitChange" />
        </template>
      </el-table-column>
      <el-table-column label="风险告警" width="86" align="center">
        <template #default="{ row }: { row: DetectionItem }">
          <el-switch v-model="row.alarmEnabled" size="small" @change="emitChange" />
        </template>
      </el-table-column>
      <el-table-column label="框上目标名称" min-width="130">
        <template #default="{ row }: { row: DetectionItem }">
          <el-input
            v-model="row.displayLabel"
            size="small"
            placeholder="例如：压力表"
            @change="emitChange"
          />
        </template>
      </el-table-column>
      <el-table-column label="Prompt" min-width="140" show-overflow-tooltip>
        <template #default="{ row }: { row: DetectionItem }">{{ row.prompt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="92" fixed="right">
        <template #default="{ row, $index }: { row: DetectionItem; $index: number }">
          <el-button link type="primary" size="small" @click="openEdit(row, $index)">编辑</el-button>
          <el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingIndex === null ? '添加检测项' : '编辑检测项'" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="检测项名称" required>
          <el-input v-model="draft.name" placeholder="例如：人员检测" maxlength="60" />
        </el-form-item>
        <el-form-item label="框上名称" required>
          <el-input v-model="draft.displayLabel" placeholder="例如：人员" maxlength="40" />
        </el-form-item>
        <el-form-item label="Prompt" required>
          <el-input
            v-model="draft.prompt"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="例如：定位图像中所有清晰可见的人员"
          />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="draft.enabled" /></el-form-item>
        <el-form-item label="风险告警"><el-switch v-model="draft.alarmEnabled" /></el-form-item>
        <el-form-item label="命中即告警">
          <el-switch v-model="draft.alarmOnFinding" :disabled="!draft.alarmEnabled" />
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="draft.alarmSeverity" :disabled="!draft.alarmEnabled">
            <el-option
              v-for="option in alarmSeverityOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="告警消息">
          <el-input
            v-model="draft.alarmMessage"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            :disabled="!draft.alarmEnabled"
            placeholder="可选，留空时使用检测项默认描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { AlarmSeverity, DetectionItem } from '@/types'
import { ALARM_SEVERITY_LABELS, DETECTION_LABELS } from '@/types'

const props = defineProps<{ items: DetectionItem[] }>()
const emit = defineEmits<{ change: [DetectionItem[]] }>()
const alarmSeverityOptions = (Object.entries(ALARM_SEVERITY_LABELS) as [AlarmSeverity, string][])
  .map(([value, label]) => ({ value, label }))
const dialogVisible = ref(false)
const editingIndex = ref<number | null>(null)
const draft = reactive<DetectionItem>(emptyItem())

function emptyItem(): DetectionItem {
  const id = `custom_${Date.now().toString(36)}`
  return {
    itemId: id,
    type: id.toUpperCase(),
    name: '',
    enabled: true,
    displayLabel: '',
    prompt: '',
    threshold: 0.75,
    alarmEnabled: false,
    alarmOnFinding: false,
    alarmSeverity: 'MEDIUM',
    alarmMessage: '',
  }
}

function assignDraft(item: DetectionItem) {
  Object.assign(draft, item, {
    alarmEnabled: item.alarmEnabled ?? false,
    alarmOnFinding: item.alarmOnFinding ?? false,
    alarmSeverity: item.alarmSeverity ?? 'MEDIUM',
    alarmMessage: item.alarmMessage ?? '',
  })
}

function openAdd() {
  editingIndex.value = null
  assignDraft(emptyItem())
  dialogVisible.value = true
}

function openEdit(item: DetectionItem, index: number) {
  editingIndex.value = index
  assignDraft({ ...item })
  dialogVisible.value = true
}

function saveItem() {
  if (!draft.name?.trim() || !draft.displayLabel.trim() || !draft.prompt?.trim()) {
    ElMessage.warning('请填写检测项名称、框上名称和 Prompt')
    return
  }
  if (!alarmSeverityOptions.some((option) => option.value === draft.alarmSeverity)) {
    ElMessage.warning('请选择有效的告警级别')
    return
  }
  const items = props.items.map((item) => ({ ...item }))
  const saved = { ...draft, name: draft.name.trim(), displayLabel: draft.displayLabel.trim(), prompt: draft.prompt.trim() }
  if (editingIndex.value === null) items.push(saved)
  else items[editingIndex.value] = saved
  emit('change', items)
  dialogVisible.value = false
}

function removeItem(index: number) {
  emit('change', props.items.filter((_, itemIndex) => itemIndex !== index).map((item) => ({ ...item })))
}

function emitChange() {
  emit('change', [...props.items])
}
</script>

<style scoped>
.detection-config {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.confidence-notice {
  margin-bottom: 10px;
  flex-shrink: 0;
}

.config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.config-table {
  flex: 1;
}
</style>
