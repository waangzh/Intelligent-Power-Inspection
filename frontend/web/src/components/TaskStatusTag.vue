<template>
  <el-tag :type="tagType" size="small">{{ TASK_STATUS_LABELS[status] }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TaskStatus } from '@/types'
import { TASK_STATUS_LABELS } from '@/types'

const props = defineProps<{ status: TaskStatus }>()

const tagType = computed(() => {
  const map: Record<TaskStatus, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    CREATED: 'info',
    DISPATCHED: 'info',
    RUNNING: 'success',
    PAUSED: 'warning',
    MANUAL_TAKEOVER: 'danger',
    COMPLETED: 'success',
    CANCELLED: 'info',
  }
  return map[props.status]
})
</script>
