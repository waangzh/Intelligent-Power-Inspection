<template>
  <div class="detection-config">
    <el-table :data="items" size="small" border>
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
      <el-table-column label="置信度阈值" width="130">
        <template #default="{ row }: { row: DetectionItem }">
          <el-slider
            v-model="row.threshold"
            :min="0.5"
            :max="0.99"
            :step="0.01"
            :show-tooltip="false"
            size="small"
            @change="emitChange"
          />
        </template>
      </el-table-column>
      <el-table-column label="LocateAnything 提示词" min-width="180">
        <template #default="{ row }: { row: DetectionItem }">
          <el-input
            v-if="needsPrompt(row.type)"
            v-model="row.prompt"
            size="small"
            placeholder="自然语言描述检测目标"
            @change="emitChange"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { DetectionItem, DetectionType } from '@/types'
import { DETECTION_LABELS } from '@/types'

const props = defineProps<{ items: DetectionItem[] }>()
const emit = defineEmits<{ change: [DetectionItem[]] }>()

function needsPrompt(type: DetectionType) {
  return ['SWITCH', 'OIL_LEAK', 'METER', 'FOREIGN_OBJECT'].includes(type)
}

function emitChange() {
  emit('change', [...props.items])
}
</script>

<style scoped>
.muted {
  color: #c0c4cc;
}
</style>
