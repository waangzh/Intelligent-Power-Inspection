<template>
  <div class="detection-config">
    <el-alert
      class="confidence-notice"
      title="当前模型不返回可校准置信度，策略按启用状态与框上目标名称执行。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-table :data="items" size="small" border class="config-table">
      <el-table-column label="检测项" width="140">
        <template #default="{ row }: { row: DetectionItem }">
          {{ DETECTION_LABELS[row.type] }}
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }: { row: DetectionItem }">
          <el-switch v-model="row.enabled" size="small" @change="emitChange" />
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
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { DetectionItem } from '@/types'
import { DETECTION_LABELS } from '@/types'

const props = defineProps<{ items: DetectionItem[] }>()
const emit = defineEmits<{ change: [DetectionItem[]] }>()

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

.config-table {
  flex: 1;
}
</style>
