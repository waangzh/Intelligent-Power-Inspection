<template>
  <div class="alarm-page">
    <PageHeader title="告警中心" description="实时告警监控与分级转工单处理" :breadcrumbs="[{ label: '监控中心' }, { label: '告警中心' }]">
      <template #actions>
        <el-button v-if="authStore.user?.role === 'ADMIN'" @click="openPolicyDialog">
          <el-icon><Setting /></el-icon>
          转工单规则
        </el-button>
      </template>
    </PageHeader>

    <div class="stats-grid">
      <el-card
        v-for="(stat, index) in severityStats"
        :key="stat.key"
        shadow="never"
        :class="['stat-card', `stat-card-${index}`, { active: activeStatKey === stat.key }]"
        @click="selectStatCard(stat.key)"
      >
        <div class="overview-stat">
          <div class="stat-icon" :class="`tone-${index}`">
            <el-icon><component :is="statIcons[index]" /></el-icon>
          </div>
          <div>
            <div class="label">{{ stat.label }}</div>
            <div class="value">{{ stat.value }}</div>
            <div class="trend">{{ stat.footer }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-row :gutter="16" class="alarm-body">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="filter-card">
          <el-alert
            v-if="detectionRunFilter"
            class="run-filter-alert"
            type="info"
            :closable="false"
            show-icon
            :title="`仅显示检测运行 ${detectionRunFilter} 生成的告警`"
          />
          <div class="filter-bar">
            <el-input
              v-model="keyword"
              placeholder="搜索告警内容"
              clearable
              class="filter-search"
              @change="searchAlarms"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <div class="filter-item">
              <span class="filter-label">级别</span>
              <el-select v-model="severityFilter" placeholder="全部" clearable style="width: 110px" @change="onSeverityFilterChange">
                <el-option label="紧急" value="CRITICAL" />
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
              </el-select>
            </div>
            <el-button @click="resetFilters">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="table-head">
              <span class="table-title">告警列表</span>
              <span class="record-count">共 {{ alarmTotal }} 条记录</span>
            </div>
          </template>
          <el-table :data="filteredAlarms" size="small" highlight-current-row @row-click="selectAlarm">
            <el-table-column label="级别" width="80">
              <template #default="{ row }: { row: Alarm }">
                <el-tag :type="severityType(row.severity)" size="small" effect="light">
                  {{ ALARM_SEVERITY_LABELS[row.severity] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="检测项" width="130" show-overflow-tooltip>
              <template #default="{ row }: { row: Alarm }">{{ alarmItemLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="检测来源" width="110">
              <template #default="{ row }: { row: Alarm }">{{ alarmSourceLabel(row) }}</template>
            </el-table-column>
            <el-table-column prop="message" label="告警内容" min-width="180" show-overflow-tooltip />
            <el-table-column prop="routeName" label="路线" width="120" show-overflow-tooltip />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag :type="conversionTagType(row)" size="small" effect="light">{{ conversionLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="showAlarmActions" label="操作" width="260" fixed="right" class-name="actions-col">
              <template #default="{ row }">
                <div class="row-actions" @click.stop>
                  <el-button
                    v-if="can('workorder:create') && shouldShowManualConversion(row)"
                    plain
                    size="small"
                    class="action-btn action-claim"
                    @click="createWorkOrder(row)"
                  >人工转工单</el-button>
                  <el-button
                    v-if="hasWorkOrder(row)"
                    plain
                    size="small"
                    class="action-btn action-detail"
                    @click="viewWorkOrder"
                  >查看工单</el-button>
                  <el-button
                    v-if="can('task:dispatch') && row.workOrderConversionStatus === 'FAILED'"
                    plain
                    size="small"
                    class="action-btn action-danger"
                    @click="retryWorkOrder(row)"
                  >重试转单</el-button>
                  <el-button
                    v-if="can('agent:run')"
                    plain
                    size="small"
                    class="action-btn action-submit"
                    @click="openAgentForAlarm(row)"
                  >Agent 处置</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <ListPagination :total="alarmTotal" :page="alarmPage" @change="loadAlarmPage" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="detail-card" v-if="selected">
          <template #header>
            <div class="table-head">
              <span class="table-title">告警详情</span>
              <el-tag :type="severityType(selected.severity)" size="small" effect="light">
                {{ ALARM_SEVERITY_LABELS[selected.severity] }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="检测项">{{ alarmItemLabel(selected) }}</el-descriptions-item>
            <el-descriptions-item label="检测来源">{{ alarmSourceLabel(selected) }}</el-descriptions-item>
            <el-descriptions-item label="路线">{{ selected.routeName }}</el-descriptions-item>
            <el-descriptions-item label="检查点">{{ selected.checkpointName || '路线行进中' }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.checkpointId" label="检查点 ID">{{ selected.checkpointId }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.imageId" label="图片 ID">{{ selected.imageId }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.detectionRunId" label="检测运行">
              <el-button v-if="can('detection:manage')" link type="primary" @click="openDetectionRun(selected)">{{ selected.detectionRunId }}</el-button>
              <span v-else>{{ selected.detectionRunId }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.finding?.label" label="检测目标">{{ selected.finding.label }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.finding?.bbox?.length" label="边界框">{{ bboxText(selected.finding.bbox) }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ selected.message }}</el-descriptions-item>
            <el-descriptions-item label="工单转换">{{ conversionLabel(selected) }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.workOrderConversionError" label="转换错误">{{ selected.workOrderConversionError }}</el-descriptions-item>
          </el-descriptions>
          <img v-if="selected.imageUrl" :src="selected.imageUrl" class="alarm-img" alt="截图" />
          <div v-if="selected && showAlarmActions" class="detail-actions">
            <el-button
              v-if="can('workorder:create') && shouldShowManualConversion(selected)"
              plain
              size="small"
              class="action-btn action-claim"
              @click="createWorkOrder(selected)"
            >人工转工单</el-button>
            <el-button v-if="hasWorkOrder(selected)" plain size="small" class="action-btn action-detail" @click="viewWorkOrder">查看工单</el-button>
            <el-button
              v-if="can('task:dispatch') && selected.workOrderConversionStatus === 'FAILED'"
              plain
              size="small"
              class="action-btn action-danger"
              @click="retryWorkOrder(selected)"
            >重试转单</el-button>
            <el-button v-if="can('agent:run')" plain size="small" class="action-btn action-submit" @click="openAgentForAlarm(selected)">Agent 处置</el-button>
          </div>
        </el-card>
        <el-card v-else shadow="never" class="detail-card"><div class="empty-hint">点击左侧告警查看详情</div></el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="table-title">级别分布</span>
          </template>
          <ChartCard :option="chartOption" :height="200" />
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
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, InfoFilled, RefreshRight, Search, Setting, Warning, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartCard from '@/components/ChartCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useWorkOrderStore } from '@/stores/workOrder'
import type { Alarm, AlarmSeverity, AlarmWorkOrderMode } from '@/types'
import { ALARM_SEVERITY_LABELS, DETECTION_LABELS } from '@/types'

const router = useRouter()
const route = useRoute()
const alarmStore = useAlarmStore()
const authStore = useAuthStore()
const workOrderStore = useWorkOrderStore()
const { can } = usePermission()
const severityFilter = ref('')
const keyword = ref('')
const selected = ref<Alarm | null>(alarmStore.alarms[0] ?? null)
const alarmPage = ref(0)
const activeStatKey = ref('ALL')
const policyDialogVisible = ref(false)
const policySaving = ref(false)
const statIcons = [WarningFilled, Warning, InfoFilled, Bell]
const policyRows = reactive<Array<{ severity: AlarmSeverity; label: string; mode: AlarmWorkOrderMode }>>([
  { severity: 'CRITICAL', label: '紧急 CRITICAL', mode: 'AUTO' },
  { severity: 'HIGH', label: '高 HIGH', mode: 'MANUAL' },
  { severity: 'MEDIUM', label: '中 MEDIUM', mode: 'MANUAL' },
  { severity: 'LOW', label: '低 LOW', mode: 'MANUAL' },
])

const detectionRunFilter = computed(() => queryText(route.query.detectionRunId))

const severityStats = computed(() => [
  {
    key: 'CRITICAL',
    label: '紧急',
    value: alarmStore.alarms.filter((a) => a.severity === 'CRITICAL').length,
    footer: '需立即处置',
  },
  {
    key: 'HIGH',
    label: '高级别',
    value: alarmStore.alarms.filter((a) => a.severity === 'HIGH').length,
    footer: '优先关注',
  },
  {
    key: 'MEDIUM',
    label: '中级别',
    value: alarmStore.alarms.filter((a) => a.severity === 'MEDIUM').length,
    footer: '常规跟进',
  },
  {
    key: 'LOW',
    label: '低级别',
    value: alarmStore.alarms.filter((a) => a.severity === 'LOW').length,
    footer: '可延后处理',
  },
])

const filteredAlarms = computed(() => {
  let list = alarmStore.alarms
  if (detectionRunFilter.value) list = list.filter((a) => a.detectionRunId === detectionRunFilter.value)
  if (severityFilter.value) list = list.filter((a) => a.severity === severityFilter.value)
  if (keyword.value) list = list.filter((a) => a.message.includes(keyword.value))
  return list
})

const alarmTotal = computed(() => alarmStore.total)

const showAlarmActions = computed(
  () => can('workorder:create') || can('task:dispatch') || can('agent:run'),
)

function selectStatCard(key: string) {
  if (activeStatKey.value === key) {
    activeStatKey.value = 'ALL'
    severityFilter.value = ''
  } else {
    activeStatKey.value = key
    severityFilter.value = key
  }
  searchAlarms()
}

function onSeverityFilterChange() {
  activeStatKey.value = severityFilter.value || 'ALL'
  searchAlarms()
}

function resetFilters() {
  keyword.value = ''
  severityFilter.value = ''
  activeStatKey.value = 'ALL'
  searchAlarms()
}

function loadAlarmPage(page: number) {
  alarmPage.value = page
  void alarmStore.load({
    page,
    size: 20,
    q: keyword.value,
    severity: severityFilter.value || undefined,
    detectionRunId: detectionRunFilter.value || undefined,
  })
}

function searchAlarms() {
  loadAlarmPage(0)
}

const chartOption = computed(() => ({
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    data: severityStats.value.map((s) => ({ name: s.label, value: s.value || 0 })),
  }],
}))

function selectAlarm(row: Alarm) {
  selected.value = row
}

function queryText(value: unknown) {
  if (Array.isArray(value)) return value.length ? String(value[0] ?? '') : ''
  return typeof value === 'string' ? value : ''
}

function alarmItemLabel(alarm: Alarm) {
  return alarm.itemName || alarm.displayLabel || alarm.finding?.label || DETECTION_LABELS[alarm.type] || '检测项'
}

function alarmSourceLabel(alarm: Alarm) {
  if (alarm.sourceType === 'DETECTION_RUN') return '模型检测'
  return alarm.sourceType || '历史告警'
}

function bboxText(bbox: number[]) {
  return bbox.join(', ')
}

function openDetectionRun(alarm: Alarm) {
  if (!alarm.detectionRunId) return
  void router.push({ path: '/detection', query: { runId: alarm.detectionRunId } })
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

watch(filteredAlarms, (alarms) => {
  if (!selected.value || !alarms.some((alarm) => alarm.id === selected.value?.id)) {
    selected.value = alarms[0] ?? null
  }
}, { immediate: true })

watch(detectionRunFilter, () => loadAlarmPage(0), { immediate: true })
</script>

<style scoped>
.alarm-body {
  margin-bottom: 0;
}

.run-filter-alert {
  margin-bottom: 12px;
}

.detail-card {
  margin-bottom: 16px;
}

.chart-card {
  margin-bottom: 0;
}

.alarm-img {
  width: 100%;
  margin-top: 12px;
  border-radius: 8px;
  border: 1px solid var(--pi-border-soft);
}

.detail-actions {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-actions :deep(.action-btn) {
  margin: 0;
  padding: 5px 12px;
  height: 28px;
  border-radius: 6px;
  font-weight: 500;
}

@media (max-width: 1200px) {
  .detail-card,
  .chart-card {
    margin-top: 16px;
  }
}
</style>
