<template>
  <div class="policy-settings">
    <div v-for="row in policyRows" :key="row.severity" class="policy-card">
      <div class="policy-head">
        <el-tag :type="severityType(row.severity)" size="small">{{ ALARM_SEVERITY_LABELS[row.severity] }}</el-tag>
        <span class="policy-hint">{{ row.hint }}</span>
      </div>
      <el-radio-group
        :model-value="policyStore.policy[row.severity]"
        size="default"
        class="policy-radio"
        @change="(v: EscalationMode) => onPolicyChange(row.severity, v)"
      >
        <el-radio-button value="AUTO">自动转工单</el-radio-button>
        <el-radio-button value="MANUAL">人工转工单</el-radio-button>
      </el-radio-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAlarmPolicyStore, type EscalationMode } from '@/stores/alarmPolicy'
import type { AlarmSeverity } from '@/types'
import { ALARM_SEVERITY_LABELS } from '@/types'

const policyStore = useAlarmPolicyStore()

const policyRows = [
  { severity: 'CRITICAL' as AlarmSeverity, hint: '紧急告警到达后自动创建工单' },
  { severity: 'HIGH' as AlarmSeverity, hint: '高级别告警可自动或人工转工单' },
  { severity: 'MEDIUM' as AlarmSeverity, hint: '中级告警建议人工确认后转工单' },
  { severity: 'LOW' as AlarmSeverity, hint: '低级告警默认仅记录' },
]

function severityType(s: AlarmSeverity) {
  return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }[s] as 'info' | 'warning' | 'danger'
}

function onPolicyChange(severity: AlarmSeverity, mode: EscalationMode) {
  policyStore.setMode(severity, mode)
}
</script>

<style scoped>
.policy-settings {
  display: grid;
  gap: 12px;
}

.policy-card {
  padding: 16px 18px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: linear-gradient(180deg, #fafcff 0%, #fff 100%);
}

.policy-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.policy-hint {
  font-size: 12px;
  color: #909399;
}

.policy-radio {
  width: 100%;
}
</style>
