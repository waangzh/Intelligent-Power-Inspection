<template>
  <div class="agent-page">
    <PageHeader
      title="智能巡检研判助手"
      description="按处置案件保留独立分析版本、证据引用与受控动作"
      :breadcrumbs="[{ label: '运维中心' }, { label: '巡检研判助手' }]"
    >
      <template #actions>
        <el-button v-if="can('agent:run')" :disabled="!agentStore.activeCase || agentStore.loading" @click="agentStore.startActiveRun()">
          <el-icon><Refresh /></el-icon>
          重新分析
        </el-button>
      </template>
    </PageHeader>

    <div class="agent-grid">
      <section class="panel input-panel">
        <div class="panel-title">新建处置案件</div>
        <el-form label-position="top" size="small">
          <el-form-item label="关联告警">
            <el-select v-model="form.alarmId" clearable filterable placeholder="选择需要处置的告警" style="width: 100%" @change="onAlarmChange">
              <el-option v-for="alarm in alarmOptions" :key="alarm.id" :label="alarmOptionLabel(alarm)" :value="alarm.id">
                <div class="option-main"><span>{{ alarm.message }}</span><el-tag size="small" :type="alarmSeverityType(alarm.severity)">{{ alarmSeverityLabel(alarm.severity) }}</el-tag></div>
                <div class="option-sub">{{ alarm.routeName }} · {{ fmt(alarm.createdAt) }}</div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="关联任务">
            <el-select v-model="form.taskId" clearable filterable placeholder="选择巡检任务" style="width: 100%" @change="onTaskChange">
              <el-option v-for="task in taskOptions" :key="task.id" :label="taskOptionLabel(task)" :value="task.id">
                <div class="option-main"><span>{{ task.name }}</span><el-tag size="small">{{ taskStatusLabel(task.status) }}</el-tag></div>
                <div class="option-sub">{{ fmt(task.createdAt) }}</div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-alert v-if="isAlarmOnlyAnalysis" class="degraded-alert" type="warning" :closable="false" show-icon title="未找到关联任务，将仅基于告警证据分析。" />
          <div v-if="selectedAlarm || selectedTask" class="context-box">
            <div v-if="selectedAlarm" class="context-row"><span>告警</span><strong>{{ selectedAlarm.message }}</strong></div>
            <div v-if="selectedTask" class="context-row"><span>任务</span><strong>{{ selectedTask.name }}</strong></div>
          </div>
          <el-form-item label="处置目标">
            <el-input v-model="form.goal" type="textarea" :rows="3" resize="none" placeholder="例如：判断告警是否需要创建高优先级工单" />
          </el-form-item>
          <el-form-item label="补充说明（作为不可信证据）">
            <el-input v-model="form.operatorNote" type="textarea" :rows="3" resize="none" />
          </el-form-item>
          <el-button v-if="can('agent:run')" type="primary" :loading="agentStore.loading" class="run-button" @click="createCase">
            <el-icon><CaretRight /></el-icon>
            启动研判
          </el-button>
        </el-form>

        <div class="panel-title history-title">处置案件</div>
        <el-radio-group v-model="caseFilter" size="small" class="case-filter">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="ANALYZING">分析中</el-radio-button>
          <el-radio-button value="WAITING_HUMAN">待补充</el-radio-button>
          <el-radio-button value="WAITING_APPROVAL">待审批</el-radio-button>
          <el-radio-button value="RESOLVED">已解决</el-radio-button>
          <el-radio-button value="FAILED">失败</el-radio-button>
        </el-radio-group>
        <div class="session-list">
          <button v-for="item in filteredCases" :key="item.id" class="session-item" :class="{ active: item.id === agentStore.activeCaseSummary?.id }" @click="agentStore.loadCase(item.id)">
            <span>{{ item.title }}</span>
            <el-tag size="small" :type="caseStatusType(item.status)">{{ caseStatusLabel(item.status) }}</el-tag>
          </button>
        </div>
      </section>

      <section class="panel run-panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">运行轨迹</div>
            <div class="muted">{{ activeSubtitle }}</div>
          </div>
          <el-tag v-if="agentStore.activeRun" :type="runStatusType(agentStore.activeRun.run.status)">{{ runStatusLabel(agentStore.activeRun.run.status) }}</el-tag>
        </div>
        <el-select v-if="agentStore.activeCase?.runs.length" class="run-select" :model-value="agentStore.activeRunId" size="small" @change="agentStore.selectRun(String($event))">
          <el-option v-for="run in agentStore.activeCase.runs" :key="run.id" :label="`Run #${run.runNumber} · ${runStatusLabel(run.status)}`" :value="run.id" />
        </el-select>
        <el-empty v-if="!agentStore.activeRun" description="暂无运行" />
        <el-timeline v-else class="step-timeline">
          <el-timeline-item v-for="step in agentStore.activeRun.steps" :key="step.id" :timestamp="fmt(step.createdAt)" :type="stepType(step.type)">
            <div class="step-row"><span>{{ step.summary }}</span><code>#{{ step.sequenceNo }} · {{ step.type }}</code></div>
          </el-timeline-item>
        </el-timeline>

        <div class="panel-title action-title">本次运行的动作</div>
        <el-table :data="agentStore.activeRun?.actions ?? []" size="small" empty-text="本次运行暂无动作">
          <el-table-column label="动作" min-width="140"><template #default="{ row }: { row: AuditedAgentAction }"><div class="action-name">{{ row.title }}</div><div class="muted">{{ row.reason }}</div></template></el-table-column>
          <el-table-column label="风险" width="72"><template #default="{ row }: { row: AuditedAgentAction }"><el-tag size="small" :type="riskLevelType(row.riskLevel)">{{ riskLevelLabel(row.riskLevel) }}</el-tag></template></el-table-column>
          <el-table-column label="置信度" width="72"><template #default="{ row }: { row: AuditedAgentAction }"><span>{{ analysis ? Math.round(analysis.confidence * 100) : '-' }}%</span></template></el-table-column>
          <el-table-column label="状态" width="82"><template #default="{ row }: { row: AuditedAgentAction }"><el-tag size="small" :type="actionStatusType(row.status)">{{ actionStatusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column v-if="can('agent:approve')" label="操作" width="142"><template #default="{ row }: { row: AuditedAgentAction }"><el-button v-if="row.status === 'PROPOSED'" text type="primary" size="small" @click="confirm(row)">批准</el-button><el-button v-if="row.status === 'PROPOSED'" text type="danger" size="small" @click="reject(row)">拒绝</el-button><span v-else class="muted">{{ row.policyCode }}</span></template></el-table-column>
        </el-table>
      </section>

      <section class="panel insight-panel">
        <div class="panel-title">本次研判</div>
        <el-empty v-if="!analysis" description="暂无研判结论" />
        <template v-else>
          <div class="level-card" :class="`level-${analysis.defectLevel.toLowerCase()}`"><span>缺陷等级</span><strong>{{ defectLabel(analysis.defectLevel) }}</strong><small>置信度 {{ Math.round(analysis.confidence * 100) }}%</small></div>
          <div class="insight-block"><h3>原因</h3><p>{{ analysis.cause }}</p></div>
          <div class="insight-block"><h3>研判建议</h3><ul><li v-for="item in analysis.recommendedActions" :key="item">{{ item }}</li></ul></div>
          <div class="insight-block"><h3>证据引用</h3><el-tag v-for="(item, idx) in analysis.evidenceReferences" :key="item.evidenceId" class="citation" size="small" type="primary" @click="scrollToEvidence(item.evidenceId)">[E{{ idx + 1 }}] {{ item.evidenceId }} · {{ item.role }}</el-tag></div>
        </template>
        <div class="panel-title evidence-title">本次运行证据</div>
        <div class="evidence-list">
          <article v-for="item in agentStore.activeRun?.evidence ?? []" :key="item.id" :id="`evidence-${item.id}`" class="evidence-item" :class="{ 'evidence-highlight': highlightEvidenceId === item.id }"><div class="evidence-meta"><el-tag size="small">{{ item.sourceType }}</el-tag><span>{{ fmt(item.collectedAt) }}</span></div><h4>{{ item.title }}</h4><p>{{ item.summary }}</p><img v-if="evidenceImageUrl(item)" :src="evidenceImageUrl(item)" alt="证据图像" /></article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CaretRight, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAlarmStore } from '@/stores/alarm'
import { useAgentStore } from '@/stores/agent'
import { useTaskStore } from '@/stores/task'
import { usePermission } from '@/composables/usePermission'
import { ALARM_SEVERITY_LABELS, TASK_STATUS_LABELS } from '@/types'
import type { Alarm, AlarmSeverity, InspectionTask, TaskStatus } from '@/types'
import type { AgentCaseStatus, AgentRiskLevel, AgentRunStatus, AgentStepType, AuditedAgentAction, AuditedAgentActionStatus, AuditedAgentConclusion, AuditedAgentEvidence } from '@/types/agent'

const pageRoute = useRoute()
const agentStore = useAgentStore()
const alarmStore = useAlarmStore()
const taskStore = useTaskStore()
const { can } = usePermission()
const form = reactive({ taskId: '', alarmId: '', goal: '', operatorNote: '' })
const caseFilter = ref('')
const highlightEvidenceId = ref('')

const analysis = computed<AuditedAgentConclusion | undefined>(() => agentStore.activeRun?.conclusion)
const selectedAlarm = computed(() => alarmStore.alarms.find((item) => item.id === form.alarmId))
const selectedTask = computed(() => taskStore.getTaskById(form.taskId))
const taskOptions = computed(() => taskStore.tasks)
const alarmOptions = computed(() => !form.taskId ? alarmStore.alarms : alarmStore.alarms.filter((item) => item.taskId === form.taskId || item.id === form.alarmId))
const isAlarmOnlyAnalysis = computed(() => Boolean(selectedAlarm.value && !selectedTask.value))
const activeSubtitle = computed(() => agentStore.activeCaseSummary ? [agentStore.activeCaseSummary.title, agentStore.activeRun ? `Run #${agentStore.activeRun.run.runNumber}` : '尚未运行'].join(' / ') : '选择或创建一个处置案件')
const filteredCases = computed(() => caseFilter.value ? agentStore.cases.filter((item) => item.status === caseFilter.value) : agentStore.cases)

onMounted(async () => {
  await Promise.allSettled([agentStore.loadCases(), taskStore.tasks.length ? Promise.resolve() : taskStore.loadDynamic(), alarmStore.alarms.length ? Promise.resolve() : alarmStore.load()])
  form.taskId = firstQuery(pageRoute.query.taskId)
  form.alarmId = firstQuery(pageRoute.query.alarmId)
  if (form.alarmId) onAlarmChange(form.alarmId)
  if (!form.taskId && !form.alarmId && agentStore.cases[0]) await agentStore.loadCase(agentStore.cases[0].id)
})

onUnmounted(() => agentStore.stopRealtime())

async function createCase() {
  if (!form.goal.trim()) {
    ElMessage.warning('请填写处置目标')
    return
  }
  if (form.taskId.trim() && !selectedTask.value) {
    ElMessage.warning('所选任务不存在，请重新选择')
    return
  }
  try {
    await agentStore.createCaseAndRun({ goal: form.goal.trim(), taskId: selectedTask.value ? form.taskId.trim() : undefined, alarmId: form.alarmId.trim() || undefined, operatorNote: form.operatorNote.trim() || undefined })
    ElMessage.success('研判运行已启动')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '研判启动失败')
  }
}

function onAlarmChange(value: string | number | boolean | undefined) {
  const alarm = alarmStore.alarms.find((item) => item.id === String(value ?? ''))
  if (alarm?.taskId && taskStore.getTaskById(alarm.taskId)) form.taskId = alarm.taskId
  if (!form.goal && alarm) form.goal = `判断“${alarm.message}”是否需要创建处置工单`
}

function onTaskChange(value: string | number | boolean | undefined) {
  const taskId = String(value ?? '')
  if (taskId && form.alarmId && selectedAlarm.value?.taskId !== taskId) form.alarmId = ''
  if (!form.goal && selectedTask.value) form.goal = `研判任务“${selectedTask.value.name}”的异常处置建议`
}

function firstQuery(value: unknown) { return Array.isArray(value) ? value[0] ?? '' : typeof value === 'string' ? value : '' }
function alarmOptionLabel(alarm: Alarm) { return `${alarmSeverityLabel(alarm.severity)} · ${alarm.message}` }
function taskOptionLabel(task: InspectionTask) { return `${task.name} · ${taskStatusLabel(task.status)}` }
function alarmSeverityLabel(severity: AlarmSeverity) { return ALARM_SEVERITY_LABELS[severity] }
function alarmSeverityType(severity: AlarmSeverity) { return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }[severity] as 'info' | 'warning' | 'danger' }
function taskStatusLabel(status: TaskStatus) { return TASK_STATUS_LABELS[status] }
async function confirm(action: AuditedAgentAction) { await agentStore.approveAction(action); ElMessage.success('动作已批准') }
async function reject(action: AuditedAgentAction) { await agentStore.rejectAction(action); ElMessage.success('动作已拒绝') }
function fmt(iso?: string) { return iso ? new Date(iso).toLocaleString('zh-CN') : '-' }
function caseStatusLabel(status: AgentCaseStatus) { return { OPEN: '待分析', ANALYZING: '分析中', WAITING_HUMAN: '待补充', WAITING_APPROVAL: '待审批', ACTION_EXECUTING: '执行中', RESOLVED: '已解决', FAILED: '失败', CLOSED: '已关闭' }[status] }
function caseStatusType(status: AgentCaseStatus) { return { OPEN: 'info', ANALYZING: 'warning', WAITING_HUMAN: 'warning', WAITING_APPROVAL: 'warning', ACTION_EXECUTING: 'warning', RESOLVED: 'success', FAILED: 'danger', CLOSED: 'info' }[status] as 'info' | 'warning' | 'success' | 'danger' }
function runStatusLabel(status: AgentRunStatus) { return { QUEUED: '排队中', RUNNING: '运行中', WAITING_TOOL: '等待工具', WAITING_HUMAN: '等待人工', WAITING_APPROVAL: '等待审批', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', TIMED_OUT: '超时', STEP_LIMIT_REACHED: '达到上限' }[status] }
function runStatusType(status: AgentRunStatus) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' || status === 'TIMED_OUT' ? 'danger' : 'warning' }
function stepType(type: AgentStepType) { return type.includes('FAILED') ? 'danger' : type.includes('SUCCEEDED') || type === 'RUN_FINISHED' ? 'success' : type.includes('ACTION') ? 'warning' : 'primary' }
function actionStatusLabel(status: AuditedAgentActionStatus) { return { PROPOSED: '待审批', APPROVED: '已批准', REJECTED: '已拒绝', EXECUTING: '执行中', SUCCEEDED: '已完成', FAILED: '失败', EXPIRED: '已过期', CANCELLED: '已取消' }[status] }
function actionStatusType(status: AuditedAgentActionStatus) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'REJECTED' || status === 'EXPIRED' || status === 'CANCELLED' ? 'info' : 'warning' }
function defectLabel(level: AgentRiskLevel) { return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }[level] }
function riskLevelLabel(level: AgentRiskLevel) { return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }[level] }
function riskLevelType(level: AgentRiskLevel) { return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }[level] as 'info' | 'warning' | 'danger' }
function evidenceImageUrl(item: AuditedAgentEvidence) {
  const value = item.payload.imageUrl
  if (typeof value !== 'string') return ''
  return /^https?:\/\//i.test(value) ? value : ''
}
function scrollToEvidence(evidenceId: string) {
  highlightEvidenceId.value = evidenceId
  const el = document.getElementById(`evidence-${evidenceId}`)
  if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.classList.add('evidence-highlight') }
  setTimeout(() => { highlightEvidenceId.value = '' }, 2000)
}
</script>

<style scoped>
.agent-page {
  --agent-border: #d8dee8;
  --agent-muted: #6b7280;
  --agent-blue: #2457a6;
  --agent-amber: #b7791f;
  --agent-red: #c2410c;
}

.agent-grid {
  display: grid;
  grid-template-columns: 280px minmax(360px, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.panel {
  min-height: 160px;
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  background: #fff;
  padding: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #172033;
}

.muted {
  margin-top: 3px;
  color: var(--agent-muted);
  font-size: 12px;
}

.run-button {
  width: 100%;
}

.degraded-alert {
  margin: 0 0 12px;
}

.option-main {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.option-main span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-sub {
  color: var(--agent-muted);
  font-size: 12px;
}

.context-box {
  display: grid;
  gap: 8px;
  margin: 2px 0 12px;
  border: 1px solid var(--agent-border);
  border-radius: 6px;
  background: #f8fafc;
  padding: 10px;
}

.context-row {
  display: grid;
  gap: 4px;
}

.context-row span {
  color: var(--agent-muted);
  font-size: 12px;
}

.context-row strong {
  color: #172033;
  font-size: 13px;
  line-height: 1.45;
}

.history-title,
.action-title,
.evidence-title {
  margin-top: 18px;
}

.session-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.session-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  width: 100%;
  border: 1px solid var(--agent-border);
  border-radius: 6px;
  background: #f8fafc;
  padding: 9px 10px;
  color: #172033;
  cursor: pointer;
  text-align: left;
}

.session-item.active {
  border-color: var(--agent-blue);
  background: #eef5ff;
}

.step-timeline {
  margin-top: 16px;
  min-height: 220px;
}

.step-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.step-row code {
  color: var(--agent-muted);
  font-size: 11px;
}

.action-name {
  font-weight: 600;
}

.level-card {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  align-items: center;
  margin-top: 12px;
  border-radius: 8px;
  padding: 14px;
  background: #eef5ff;
  color: var(--agent-blue);
}

.level-card strong {
  font-size: 28px;
}

.level-card small {
  grid-column: 1 / -1;
}

.level-high {
  background: #fff7e6;
  color: var(--agent-amber);
}

.level-critical {
  background: #fff1ed;
  color: var(--agent-red);
}

.level-low {
  background: #edf7f2;
  color: #247857;
}

.insight-block {
  margin-top: 16px;
}

.insight-block h3,
.evidence-item h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.insight-block p,
.evidence-item p {
  margin: 0;
  color: #374151;
  line-height: 1.6;
}

.insight-block ul {
  margin: 0;
  padding-left: 18px;
  color: #374151;
}

.citation {
  margin: 0 6px 6px 0;
}

.evidence-list {
  display: grid;
  gap: 10px;
  margin-top: 10px;
  max-height: 520px;
  overflow: auto;
}

.evidence-item {
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  padding: 12px;
  background: #fbfcfe;
}

.evidence-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  color: var(--agent-muted);
  font-size: 12px;
}

.evidence-item img {
  width: 100%;
  margin-top: 10px;
  border-radius: 6px;
  object-fit: cover;
}

.evidence-highlight {
  border-color: var(--agent-blue);
  box-shadow: 0 0 0 2px rgba(36, 87, 166, 0.25);
  transition: box-shadow 0.3s ease;
}

.case-filter {
  margin-top: 10px;
  width: 100%;
}

.case-filter :deep(.el-radio-button__inner) {
  font-size: 12px;
  padding: 4px 10px;
}

.citation {
  cursor: pointer;
}

@media (max-width: 1180px) {
  .agent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
