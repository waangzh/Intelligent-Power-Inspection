<template>
  <div>
    <PageHeader title="告警中心" description="实时告警监控、确认与分级处理" :breadcrumbs="[{ label: '监控中心' }, { label: '告警中心' }]">
      <template #actions>
        <el-button v-if="authStore.user?.role === 'ADMIN'" @click="openPolicyDialog">转工单规则</el-button>
        <el-button v-if="can('alarm:ack')" @click="alarmStore.acknowledgeAll()" :disabled="!alarmStore.unacknowledgedCount">全部确认</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in severityStats" :key="s.label">
        <el-card shadow="never" class="sev-card">
          <div class="sev-val" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="sev-lbl">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="filter-bar">
              <el-input v-model="keyword" placeholder="搜索告警内容" clearable style="width: 200px" size="small" />
              <el-radio-group v-model="filter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="pending">未确认</el-radio-button>
              </el-radio-group>
              <el-select v-model="severityFilter" placeholder="级别" clearable size="small" style="width: 100px">
                <el-option label="紧急" value="CRITICAL" />
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
              </el-select>
            </div>
          </template>
          <el-table :data="filteredAlarms" size="small" @row-click="selectAlarm">
            <el-table-column label="级别" width="80">
              <template #default="{ row }: { row: Alarm }">
                <el-tag :type="severityType(row.severity)" size="small">{{ ALARM_SEVERITY_LABELS[row.severity] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }: { row: Alarm }">{{ DETECTION_LABELS[row.type] }}</template>
            </el-table-column>
            <el-table-column prop="message" label="告警内容" show-overflow-tooltip />
            <el-table-column prop="routeName" label="路线" width="120" show-overflow-tooltip />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <div class="status-tags">
                  <el-tag :type="row.acknowledged ? 'info' : 'danger'" size="small">{{ row.acknowledged ? '已确认' : '待处理' }}</el-tag>
                  <el-tag :type="conversionTagType(row)" size="small">{{ conversionLabel(row) }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="can('alarm:ack') || can('task:dispatch')" label="操作" width="250">
              <template #default="{ row }">
                <el-button v-if="!row.acknowledged" text type="primary" size="small" @click.stop="alarmStore.acknowledge(row.id)">确认</el-button>
                <el-button
                  v-if="can('task:dispatch') && shouldShowManualConversion(row)"
                  text
                  type="warning"
                  size="small"
                  @click.stop="createWorkOrder(row)"
                >人工转工单</el-button>
                <el-button v-if="hasWorkOrder(row)" text type="warning" size="small" @click.stop="viewWorkOrder">查看工单</el-button>
                <el-button v-if="can('task:dispatch') && row.workOrderConversionStatus === 'FAILED'" text type="danger" size="small" @click.stop="retryWorkOrder(row)">重试转单</el-button>
                <el-button
                  v-if="can('task:dispatch')"
                  text
                  type="success"
                  size="small"
                  @click.stop="openAgentForAlarm(row)"
                >Agent 处置</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" v-if="selected">
          <template #header>告警详情</template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="级别">
              <el-tag :type="severityType(selected.severity)">{{ ALARM_SEVERITY_LABELS[selected.severity] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="类型">{{ DETECTION_LABELS[selected.type] }}</el-descriptions-item>
            <el-descriptions-item label="路线">{{ selected.routeName }}</el-descriptions-item>
            <el-descriptions-item label="检查点">{{ selected.checkpointName || '路线行进中' }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ selected.message }}</el-descriptions-item>
            <el-descriptions-item label="工单转换">{{ conversionLabel(selected) }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.workOrderConversionError" label="转换错误">{{ selected.workOrderConversionError }}</el-descriptions-item>
          </el-descriptions>
          <img v-if="selected.imageUrl" :src="selected.imageUrl" class="alarm-img" alt="截图" />
          <div v-if="selected && (can('alarm:ack') || can('task:dispatch'))" style="margin-top: 12px; display: flex; gap: 8px">
            <el-button v-if="!selected.acknowledged" type="primary" size="small" @click="alarmStore.acknowledge(selected.id)">确认告警</el-button>
            <el-button
              v-if="can('task:dispatch') && shouldShowManualConversion(selected)"
              type="warning"
              size="small"
              @click="createWorkOrder(selected)"
            >人工转工单</el-button>
            <el-button v-if="hasWorkOrder(selected)" size="small" @click="viewWorkOrder">查看工单</el-button>
            <el-button v-if="can('task:dispatch') && selected.workOrderConversionStatus === 'FAILED'" type="danger" size="small" @click="retryWorkOrder(selected)">重试转单</el-button>
            <el-button v-if="can('task:dispatch')" type="success" size="small" @click="openAgentForAlarm(selected)">Agent 处置</el-button>
          </div>
        </el-card>
        <el-card v-else shadow="never"><div class="empty-hint">点击告警查看详情</div></el-card>
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>级别分布</template>
          <ChartCard :option="chartOption" :height="180" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="policyDialogVisible" title="告警转工单规则" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="规则保存后只对新产生的告警生效，历史告警仍按人工方式处理。"
        style="margin-bottom: 16px"
      />
      <el-table :data="policyRows" size="small" border>
        <el-table-column prop="label" label="告警级别" width="150" />
        <el-table-column label="转单方式">
          <template #default="{ row }">
            <el-select v-model="row.mode" style="width: 100%">
              <el-option label="自动转工单" value="AUTO" />
              <el-option label="人工转工单" value="MANUAL" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="policyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="savePolicy">保存规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChartCard from '@/components/ChartCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useWorkOrderStore } from '@/stores/workOrder'
import type { Alarm, AlarmSeverity, AlarmWorkOrderMode } from '@/types'
import { ALARM_SEVERITY_LABELS, DETECTION_LABELS } from '@/types'

const router = useRouter()
const alarmStore = useAlarmStore()
const authStore = useAuthStore()
const workOrderStore = useWorkOrderStore()
const { can } = usePermission()
const filter = ref<'all' | 'pending'>('all')
const severityFilter = ref('')
const keyword = ref('')
const selected = ref<Alarm | null>(alarmStore.alarms[0] ?? null)
const policyDialogVisible = ref(false)
const policySaving = ref(false)
const policyRows = reactive<Array<{ severity: AlarmSeverity; label: string; mode: AlarmWorkOrderMode }>>([
  { severity: 'CRITICAL', label: '紧急 CRITICAL', mode: 'AUTO' },
  { severity: 'HIGH', label: '高 HIGH', mode: 'MANUAL' },
  { severity: 'MEDIUM', label: '中 MEDIUM', mode: 'MANUAL' },
  { severity: 'LOW', label: '低 LOW', mode: 'MANUAL' },
])

const severityStats = computed(() => [
  { label: '紧急', value: alarmStore.alarms.filter((a) => a.severity === 'CRITICAL').length, color: '#f56c6c' },
  { label: '高', value: alarmStore.alarms.filter((a) => a.severity === 'HIGH').length, color: '#e6a23c' },
  { label: '中', value: alarmStore.alarms.filter((a) => a.severity === 'MEDIUM').length, color: '#409eff' },
  { label: '未确认', value: alarmStore.unacknowledgedCount, color: '#909399' },
])

const filteredAlarms = computed(() => {
  let list = alarmStore.alarms
  if (filter.value === 'pending') list = list.filter((a) => !a.acknowledged)
  if (severityFilter.value) list = list.filter((a) => a.severity === severityFilter.value)
  if (keyword.value) list = list.filter((a) => a.message.includes(keyword.value))
  return list
})

const chartOption = computed(() => ({
  series: [{
    type: 'pie',
    radius: '65%',
    data: severityStats.value.slice(0, 3).map((s) => ({ name: s.label, value: s.value || 0 })),
  }],
}))

function selectAlarm(row: Alarm) {
  selected.value = row
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function severityType(s: AlarmSeverity) {
  return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }[s] as 'info' | 'warning' | 'danger'
}

async function createWorkOrder(alarm: Alarm) {
  if (!authStore.user) return
  try {
    const order = await workOrderStore.createFromAlarm(alarm)
    alarm.workOrderModeApplied = 'MANUAL'
    alarm.workOrderConversionSource = 'MANUAL'
    alarm.workOrderConversionStatus = 'SUCCEEDED'
    alarm.workOrderId = order.id
    ElMessage.success(`工单 ${order.id} 已创建`)
    router.push('/workorders')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  }
}

function hasWorkOrder(alarm: Alarm) {
  return Boolean(alarm.workOrderId || workOrderStore.getByAlarmId(alarm.id))
}

function isLegacyAlarm(alarm: Alarm) {
  return !alarm.workOrderModeApplied
}

function shouldShowManualConversion(alarm: Alarm) {
  if (hasWorkOrder(alarm) || alarm.workOrderConversionStatus === 'FAILED') return false
  return isLegacyAlarm(alarm) || alarm.workOrderModeApplied === 'MANUAL'
}

function conversionLabel(alarm: Alarm) {
  if (hasWorkOrder(alarm)) {
    const source = alarm.workOrderConversionSource || workOrderStore.getByAlarmId(alarm.id)?.source
    if (!source) return '已有工单'
    return source === 'AUTO' ? '已自动转工单' : source === 'AGENT' ? '已由 Agent 转工单' : '已人工转工单'
  }
  if (alarm.workOrderConversionStatus === 'FAILED') return '自动转单失败'
  if (isLegacyAlarm(alarm)) return '历史告警/未应用转单规则'
  if (alarm.workOrderConversionStatus === 'PROCESSING') return '自动转单中'
  if (alarm.workOrderModeApplied === 'AUTO') return '等待自动转单'
  return '等待人工转单'
}

function conversionTagType(alarm: Alarm) {
  if (alarm.workOrderConversionStatus === 'FAILED') return 'danger'
  if (hasWorkOrder(alarm)) return 'success'
  if (alarm.workOrderModeApplied === 'AUTO') return 'warning'
  return 'info'
}

async function retryWorkOrder(alarm: Alarm) {
  try {
    const updated = await alarmStore.retryWorkOrder(alarm.id)
    selected.value = selected.value?.id === updated.id ? updated : selected.value
    if (updated.workOrderConversionStatus === 'SUCCEEDED') {
      await workOrderStore.load()
      ElMessage.success('自动转工单成功')
    } else {
      ElMessage.error(updated.workOrderConversionError || '自动转工单失败')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试失败')
  }
}

async function viewWorkOrder() {
  await workOrderStore.load().catch(() => {})
  router.push('/workorders')
}

function openPolicyDialog() {
  policyRows.forEach((row) => {
    row.mode = alarmStore.workOrderPolicy.rules[row.severity]
  })
  policyDialogVisible.value = true
}

async function savePolicy() {
  policySaving.value = true
  try {
    const rules = Object.fromEntries(policyRows.map((row) => [row.severity, row.mode])) as Record<AlarmSeverity, AlarmWorkOrderMode>
    await alarmStore.saveWorkOrderPolicy(rules)
    policyDialogVisible.value = false
    ElMessage.success('转工单规则已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    policySaving.value = false
  }
}

function openAgentForAlarm(alarm: Alarm) {
  router.push({ path: '/agents', query: { alarmId: alarm.id } })
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.sev-card .sev-val {
  font-size: 26px;
  font-weight: 700;
}

.sev-card .sev-lbl {
  font-size: 12px;
  color: #909399;
}

.alarm-img {
  width: 100%;
  margin-top: 12px;
  border-radius: 6px;
}

.status-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
</style>
