<template>
  <el-tag :type="tagType" size="small">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TaskStatus } from '@/types'
import { TASK_STATUS_LABELS } from '@/types'

const props = defineProps<{ status: TaskStatus; manualReconciliationRequired?: boolean }>()

const label = computed(() => props.manualReconciliationRequired ? '待人工对账' : TASK_STATUS_LABELS[props.status])

const tagType = computed(() => {
  if (props.manualReconciliationRequired) return 'warning'
  const map: Record<TaskStatus, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    CREATED: 'info',
    DISPATCHED: 'info',
    STARTING: 'warning',
    WAITING_LOCAL_CONFIRM: 'warning',
    RUNNING: 'success',
    PAUSING: 'warning',
    PAUSED: 'warning',
    RESUMING: 'warning',
    CANCELLING: 'warning',
    ESTOPPING: 'danger',
    TAKEOVER_PENDING: 'warning',
    MANUAL_TAKEOVER: 'danger',
    COMPLETED: 'success',
    CANCELLED: 'info',
    ESTOPPED: 'danger',
    START_FAILED: 'danger',
    FAILED: 'danger',
    DISCONNECTED: 'warning',
    RECOVERING: 'warning',
  }
  return map[props.status]
})
</script>
