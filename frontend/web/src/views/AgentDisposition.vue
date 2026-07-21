<template>
  <div class="agent-page">
    <PageHeader
      title="智能巡检研判助手"
      description="按处置案件保留独立分析版本、证据引用与受控动作"
      :breadcrumbs="[{ label: '运维中心' }, { label: '巡检研判助手' }]"
    >
      <template #actions>
        <el-tag effect="plain" type="primary" class="agent-badge">
          <el-icon><Connection /></el-icon>
          Agent · 受控动作
        </el-tag>
        <el-button
          v-if="can('agent:run')"
          plain
          :disabled="!agentStore.activeCase || agentStore.loading"
          @click="agentStore.startActiveRun()"
        >
          <el-icon><Refresh /></el-icon>
          重新分析
        </el-button>
      </template>
    </PageHeader>

    <div class="agent-grid">
      <el-card shadow="never" class="agent-card sidebar-card">
        <div class="section-head">
          <el-icon><Plus /></el-icon>
          <span>新建处置案件</span>
        </div>
        <el-form label-position="top" size="default" class="create-form">
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
            <el-input v-model="form.operatorNote" type="textarea" :rows="2" resize="none" />
          </el-form-item>
          <el-button v-if="can('agent:run')" type="primary" :loading="agentStore.loading" class="run-button" @click="createCase">
            <el-icon><CaretRight /></el-icon>
            启动研判
          </el-button>
        </el-form>

        <div class="section-divider" />

        <div class="section-head">
          <el-icon><FolderOpened /></el-icon>
          <span>处置案件</span>
        </div>
        <div class="case-chips">
          <button
            v-for="chip in caseFilterChips"
            :key="chip.key"
            type="button"
            class="case-chip"
            :class="{ active: caseFilter === chip.key }"
            @click="caseFilter = chip.key"
          >
            {{ chip.label }}
          </button>
        </div>
        <div class="agent-panel-scroll">
        <div class="session-list">
          <button
            v-for="item in filteredCases"
            :key="item.id"
            type="button"
            class="session-item"
            :class="{ active: item.id === agentStore.activeCaseSummary?.id }"
            @click="agentStore.loadCase(item.id)"
          >
            <span class="session-accent" />
            <div class="session-body">
              <div class="session-title">{{ item.title }}</div>
              <div class="session-meta">{{ fmtRelative(item.updatedAt) }}</div>
            </div>
            <el-tag size="small" effect="light" :type="caseStatusType(item.status)">{{ caseStatusLabel(item.status) }}</el-tag>
          </button>
          <div v-if="!filteredCases.length" class="session-empty">暂无案件</div>
        </div>
        </div>
      </el-card>

      <el-card shadow="never" class="agent-card run-card">
        <div class="panel-head">
          <div>
            <div class="panel-title">运行轨迹</div>
            <div class="muted">{{ activeSubtitle }}</div>
          </div>
          <el-tag v-if="agentStore.activeRun" effect="light" :type="runStatusType(agentStore.activeRun.run.status)">
            {{ runStatusLabel(agentStore.activeRun.run.status) }}
          </el-tag>
        </div>

        <div class="agent-panel-scroll">
        <div v-if="agentStore.activeRun" class="run-pipeline">
          <div
            v-for="(phase, index) in runPhases"
            :key="phase.key"
            class="pipeline-step"
            :class="{ done: index < runPhaseIndex, active: index === runPhaseIndex, pending: index > runPhaseIndex }"
          >
            <span class="pipeline-dot">{{ index + 1 }}</span>
            <span class="pipeline-label">{{ phase.label }}</span>
          </div>
        </div>

        <el-select
          v-if="agentStore.activeCase?.runs.length"
          class="run-select"
          :model-value="agentStore.activeRunId"
          @change="agentStore.selectRun(String($event))"
        >
          <el-option v-for="run in agentStore.activeCase.runs" :key="run.id" :label="`Run #${run.runNumber} · ${runStatusLabel(run.status)}`" :value="run.id" />
        </el-select>

        <el-empty v-if="!agentStore.activeRun" description="选择案件或启动研判以查看运行轨迹" :image-size="72" />

        <el-timeline v-else class="step-timeline">
          <el-timeline-item
            v-for="step in agentStore.activeRun.steps"
            :key="step.id"
            :timestamp="fmt(step.createdAt)"
            :type="stepType(step.type)"
            hollow
          >
            <div class="step-row">
              <div class="step-main">
                <el-icon class="step-icon" :class="`step-${stepType(step.type)}`"><component :is="stepIcon(step.type)" /></el-icon>
                <span>{{ step.summary }}</span>
              </div>
              <code>#{{ step.sequenceNo }} · {{ step.type }}</code>
            </div>
          </el-timeline-item>
        </el-timeline>

        <div v-if="agentStore.activeRun?.question?.question" class="human-question">
          <div class="question-head">
            <el-icon><QuestionFilled /></el-icon>
            <span class="question-title">需要人工补充</span>
          </div>
          <p>{{ agentStore.activeRun.question.question.prompt }}</p>
          <template v-if="can('agent:run') && agentStore.activeRun.run.status === 'WAITING_HUMAN'">
            <el-input v-model="humanAnswer" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="输入现场确认信息；内容将作为不可信业务证据供 Agent 读取" />
            <div class="question-actions">
              <el-button type="primary" size="small" @click="submitAnswer">提交回答</el-button>
              <el-button size="small" @click="continueWithEvidence">基于现有证据继续</el-button>
              <el-button type="danger" plain size="small" @click="cancelRun">取消 Run</el-button>
            </div>
          </template>
          <el-alert v-else type="info" :closable="false" title="当前账号没有回答该问题的权限，或 Run 已不再等待人工输入。" />
        </div>

        <template v-if="agentStore.activeRun?.toolCalls.length">
          <div class="sub-section-title">工具调用</div>
          <div class="tool-list">
            <div v-for="tool in agentStore.activeRun.toolCalls" :key="tool.id" class="tool-card">
              <div class="tool-name"><el-icon><Tools /></el-icon>{{ tool.toolName }}</div>
              <el-tag size="small" effect="light">{{ tool.status }}</el-tag>
              <span class="tool-duration">{{ tool.durationMs == null ? '-' : `${tool.durationMs}ms` }}</span>
            </div>
          </div>
        </template>

        <div class="sub-section-title">本次运行的动作</div>
        <el-table :data="agentStore.activeRun?.actions ?? []" size="small" empty-text="本次运行暂无动作" class="action-table">
          <el-table-column label="动作" min-width="140">
            <template #default="{ row }: { row: AuditedAgentAction }">
              <div class="action-name">{{ row.title }}</div>
              <div class="muted">{{ row.reason }}</div>
            </template>
          </el-table-column>
          <el-table-column label="风险" width="72">
            <template #default="{ row }: { row: AuditedAgentAction }">
              <el-tag size="small" effect="light" :type="riskLevelType(row.riskLevel)">{{ riskLevelLabel(row.riskLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="72">
            <template #default="{ row }: { row: AuditedAgentAction }"><span>{{ Math.round(row.confidence * 100) }}%</span></template>
          </el-table-column>
          <el-table-column label="状态" width="82">
            <template #default="{ row }: { row: AuditedAgentAction }">
              <el-tag size="small" effect="light" :type="actionStatusType(row.status)">{{ actionStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="策略" min-width="120">
            <template #default="{ row }: { row: AuditedAgentAction }">
              <div>{{ row.policyDecision }}</div>
              <div class="muted">{{ row.policyReason }}</div>
            </template>
          </el-table-column>
          <el-table-column v-if="can('agent:approve')" label="操作" width="150" class-name="actions-col">
            <template #default="{ row }: { row: AuditedAgentAction }">
              <div class="row-actions">
                <el-button v-if="row.status === 'PROPOSED'" plain size="small" class="action-btn action-submit" @click="confirm(row)">批准</el-button>
                <el-button v-if="row.status === 'PROPOSED'" plain size="small" class="action-btn action-danger" @click="reject(row)">拒绝</el-button>
                <el-button v-if="row.status === 'FAILED'" plain size="small" class="action-btn action-claim" @click="retry(row)">重试</el-button>
                <span v-if="row.status !== 'PROPOSED' && row.status !== 'FAILED'" class="muted">{{ row.policyCode }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
        </div>
      </el-card>

      <el-card shadow="never" class="agent-card conclusion-panel">
        <div class="panel-title">本次研判</div>
        <div class="agent-panel-scroll">
        <el-empty v-if="!analysis" description="研判完成后将在此展示结论" :image-size="72" />
        <template v-else>
          <div class="level-hero" :class="`level-${analysis.defectLevel.toLowerCase()}`">
            <div class="level-main">
              <span class="level-label">缺陷等级</span>
              <strong>{{ defectLabel(analysis.defectLevel) }}</strong>
            </div>
            <el-progress type="circle" :percentage="Math.round(analysis.confidence * 100)" :width="72" :stroke-width="6" />
            <small>研判置信度</small>
          </div>
          <div class="insight-block">
            <h3>原因</h3>
            <p>{{ analysis.cause }}</p>
          </div>
          <div class="insight-block">
            <h3>研判建议</h3>
            <ul><li v-for="item in analysis.recommendedActions" :key="item">{{ item }}</li></ul>
          </div>
          <div class="insight-block">
            <h3>证据引用</h3>
            <el-tag
              v-for="(item, idx) in analysis.evidenceReferences"
              :key="item.evidenceId"
              class="citation"
              size="small"
              effect="plain"
              type="primary"
              @click="scrollToEvidence(item.evidenceId)"
            >[E{{ idx + 1 }}] {{ item.evidenceId }} · {{ item.role }}</el-tag>
          </div>
        </template>

        <div class="sub-section-title evidence-title">本次运行证据</div>
        <div class="evidence-list">
          <article
            v-for="item in agentStore.activeRun?.evidence ?? []"
            :key="item.id"
            :id="`evidence-${item.id}`"
            class="evidence-item"
            :class="{ 'evidence-highlight': highlightEvidenceId === item.id }"
          >
            <div class="evidence-meta">
              <el-tag size="small" effect="light">{{ item.sourceType }}</el-tag>
              <span>{{ fmt(item.collectedAt) }}</span>
            </div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.summary }}</p>
            <img v-if="evidenceImageUrl(item)" :src="evidenceImageUrl(item)" alt="证据图像" />
          </article>
          <div v-if="!(agentStore.activeRun?.evidence?.length)" class="session-empty">暂无证据</div>
        </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CaretRight,
  CircleCheck,
  Connection,
  Cpu,
  FolderOpened,
  Plus,
  QuestionFilled,
  Refresh,
  Tools,
  User,
  Warning,
} from '@element-plus/icons-vue'
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
const humanAnswer = ref('')

const caseFilterChips = [
  { key: '', label: '全部' },
  { key: 'ANALYZING', label: '分析中' },
  { key: 'WAITING_HUMAN', label: '待补充' },
  { key: 'WAITING_APPROVAL', label: '待审批' },
  { key: 'RESOLVED', label: '已解决' },
  { key: 'FAILED', label: '失败' },
]

const runPhases = [
  { key: 'queue', label: '排队' },
  { key: 'run', label: '分析' },
  { key: 'human', label: '人工/审批' },
  { key: 'done', label: '完成' },
]

const analysis = computed<AuditedAgentConclusion | undefined>(() => agentStore.activeRun?.conclusion)
const selectedAlarm = computed(() => alarmStore.alarms.find((item) => item.id === form.alarmId))
const selectedTask = computed(() => taskStore.getTaskById(form.taskId))
const taskOptions = computed(() => taskStore.tasks)
const alarmOptions = computed(() => !form.taskId ? alarmStore.alarms : alarmStore.alarms.filter((item) => item.taskId === form.taskId || item.id === form.alarmId))
const isAlarmOnlyAnalysis = computed(() => Boolean(selectedAlarm.value && !selectedTask.value))
const activeSubtitle = computed(() => agentStore.activeCaseSummary ? [agentStore.activeCaseSummary.title, agentStore.activeRun ? `Run #${agentStore.activeRun.run.runNumber}` : '尚未运行'].join(' / ') : '选择或创建一个处置案件')
const filteredCases = computed(() => caseFilter.value ? agentStore.cases.filter((item) => item.status === caseFilter.value) : agentStore.cases)

const runPhaseIndex = computed(() => {
  const status = agentStore.activeRun?.run.status
  if (!status) return 0
  if (['QUEUED'].includes(status)) return 0
  if (['RUNNING', 'WAITING_TOOL', 'ACTION_EXECUTING'].includes(status)) return 1
  if (['WAITING_HUMAN', 'WAITING_APPROVAL'].includes(status)) return 2
  if (['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'STEP_LIMIT_REACHED'].includes(status)) return 3
  return 1
})

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
async function retry(action: AuditedAgentAction) { await agentStore.retryAction(action); ElMessage.success('已提交动作重试') }
async function submitAnswer() {
  if (!humanAnswer.value.trim()) { ElMessage.warning('请输入回答内容'); return }
  await agentStore.submitHumanInput('ANSWER', humanAnswer.value)
  humanAnswer.value = ''
  ElMessage.success('人工回答已保存，Agent 将继续执行')
}
async function continueWithEvidence() { await agentStore.submitHumanInput('CONTINUE_WITH_CURRENT_EVIDENCE'); ElMessage.success('已请求基于现有证据继续') }
async function cancelRun() { await agentStore.cancelActiveRun(); ElMessage.success('Run 已取消') }
function fmt(iso?: string) { return iso ? new Date(iso).toLocaleString('zh-CN') : '-' }
function fmtRelative(iso?: string) {
  if (!iso) return '-'
  const diffSec = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diffSec < 60) return '刚刚'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`
  return fmt(iso)
}
function caseStatusLabel(status: AgentCaseStatus) { return { OPEN: '待分析', ANALYZING: '分析中', WAITING_HUMAN: '待补充', WAITING_APPROVAL: '待审批', ACTION_EXECUTING: '执行中', RESOLVED: '已解决', FAILED: '失败', CLOSED: '已关闭' }[status] }
function caseStatusType(status: AgentCaseStatus) { return { OPEN: 'info', ANALYZING: 'warning', WAITING_HUMAN: 'warning', WAITING_APPROVAL: 'warning', ACTION_EXECUTING: 'warning', RESOLVED: 'success', FAILED: 'danger', CLOSED: 'info' }[status] as 'info' | 'warning' | 'success' | 'danger' }
function runStatusLabel(status: AgentRunStatus) { return { QUEUED: '排队中', RUNNING: '运行中', WAITING_TOOL: '等待工具', WAITING_HUMAN: '等待人工', WAITING_APPROVAL: '等待审批', ACTION_EXECUTING: '动作执行中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', TIMED_OUT: '超时', STEP_LIMIT_REACHED: '达到上限' }[status] }
function runStatusType(status: AgentRunStatus) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' || status === 'TIMED_OUT' ? 'danger' : 'warning' }
function stepType(type: AgentStepType) { return type.includes('FAILED') ? 'danger' : type.includes('SUCCEEDED') || type === 'RUN_FINISHED' ? 'success' : type.includes('ACTION') ? 'warning' : 'primary' }
function stepIcon(type: AgentStepType) {
  if (type.includes('HUMAN')) return User
  if (type.includes('TOOL')) return Tools
  if (type.includes('ACTION')) return Warning
  if (type.includes('FAILED')) return Warning
  if (type.includes('SUCCEEDED') || type === 'RUN_FINISHED') return CircleCheck
  return Cpu
}
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
  if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }) }
  setTimeout(() => { highlightEvidenceId.value = '' }, 2000)
}
</script>

<style scoped>
.agent-page {
  --agent-muted: var(--pi-muted);
}

.agent-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.agent-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--pi-border);
  border-radius: var(--pi-card-radius);
}

.agent-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 18px;
  background: #fff;
  min-height: 720px;
  overflow: hidden;
}

.agent-panel-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-top: 4px;
  padding-right: 4px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 700;
  color: var(--pi-text);
}

.section-divider {
  height: 1px;
  margin: 18px 0;
  background: var(--pi-border-soft);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pi-text);
}

.sub-section-title {
  margin: 18px 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: #526986;
}

.muted {
  margin-top: 3px;
  color: var(--agent-muted);
  font-size: 12px;
}

.run-button {
  width: 100%;
  margin-top: 4px;
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
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  background: #f7faff;
  padding: 10px 12px;
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
  color: var(--pi-text);
  font-size: 13px;
  line-height: 1.45;
}

.case-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.case-chip {
  padding: 4px 10px;
  border: 1px solid var(--pi-border);
  border-radius: 999px;
  background: #fff;
  color: #526986;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.case-chip:hover {
  background: #f7faff;
}

.case-chip.active {
  border-color: var(--pi-primary);
  background: #e6f4ff;
  color: var(--pi-primary);
}

.session-list {
  display: grid;
  gap: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  background: #f7faff;
  padding: 10px 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}

.session-item:hover {
  background: #f0f7ff;
}

.session-item.active {
  border-color: var(--pi-primary);
  background: #e6f4ff;
}

.session-item.active .session-accent {
  background: var(--pi-primary);
}

.session-accent {
  width: 3px;
  align-self: stretch;
  border-radius: 99px;
  background: transparent;
  flex-shrink: 0;
}

.session-body {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--pi-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  margin-top: 2px;
  font-size: 11px;
  color: var(--agent-muted);
}

.session-empty {
  padding: 20px;
  text-align: center;
  color: var(--agent-muted);
  font-size: 13px;
}

.run-pipeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 10px;
  background: #f7faff;
  border: 1px solid var(--pi-border-soft);
}

.pipeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.pipeline-dot {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  background: #e4ebf4;
  color: #6f8099;
}

.pipeline-label {
  font-size: 11px;
  color: var(--agent-muted);
  font-weight: 600;
}

.pipeline-step.done .pipeline-dot {
  background: #d9f7be;
  color: #389e0d;
}

.pipeline-step.done .pipeline-label {
  color: #389e0d;
}

.pipeline-step.active .pipeline-dot {
  background: var(--pi-primary);
  color: #fff;
  box-shadow: 0 0 0 3px rgba(23, 104, 242, 0.18);
}

.pipeline-step.active .pipeline-label {
  color: var(--pi-primary);
}

.run-select {
  width: 100%;
  margin-bottom: 8px;
}

.step-timeline {
  margin-top: 8px;
}

.run-card .agent-panel-scroll {
  margin-top: 0;
}

.step-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.step-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.step-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 16px;
}

.step-icon.step-primary { color: var(--pi-primary); }
.step-icon.step-success { color: #12b76a; }
.step-icon.step-warning { color: #ff8a00; }
.step-icon.step-danger { color: #f04438; }

.step-row code {
  flex-shrink: 0;
  color: var(--agent-muted);
  font-size: 11px;
}

.human-question {
  margin: 14px 0;
  border: 1px solid #ffd591;
  border-radius: 12px;
  background: linear-gradient(180deg, #fffbe6 0%, #fff7e6 100%);
  padding: 14px;
}

.question-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #d46b08;
}

.question-title {
  font-weight: 700;
}

.human-question p {
  margin: 10px 0 12px;
  color: #5e4a20;
  line-height: 1.55;
}

.question-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.tool-list {
  display: grid;
  gap: 8px;
}

.tool-card {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  background: #f7faff;
}

.tool-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--pi-text);
  min-width: 0;
}

.tool-duration {
  font-size: 11px;
  color: var(--agent-muted);
  font-family: Consolas, monospace;
}

.action-table {
  border-radius: 10px;
  overflow: hidden;
}

.action-name {
  font-weight: 600;
}

.level-hero {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 4px 16px;
  align-items: center;
  margin-top: 12px;
  border-radius: 12px;
  padding: 16px;
  background: #eef5ff;
  color: var(--pi-primary);
}

.level-main strong {
  display: block;
  font-size: 32px;
  line-height: 1.1;
}

.level-label {
  font-size: 12px;
  font-weight: 700;
  opacity: 0.85;
}

.level-hero small {
  grid-column: 1 / -1;
  font-size: 12px;
  color: var(--agent-muted);
}

.level-hero :deep(.el-progress) {
  grid-row: 1 / span 2;
}

.level-high {
  background: #fff7e6;
  color: #d46b08;
}

.level-critical {
  background: #fff1f0;
  color: #cf1322;
}

.level-medium {
  background: #eef5ff;
  color: var(--pi-primary);
}

.level-low {
  background: #f6ffed;
  color: #389e0d;
}

.insight-block {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  background: #f7faff;
}

.insight-block h3,
.evidence-item h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: #526986;
}

.insight-block p,
.evidence-item p {
  margin: 0;
  color: #314a6b;
  line-height: 1.6;
  font-size: 13px;
}

.insight-block ul {
  margin: 0;
  padding-left: 18px;
  color: #314a6b;
  font-size: 13px;
  line-height: 1.6;
}

.citation {
  margin: 0 6px 6px 0;
  cursor: pointer;
}

.evidence-list {
  display: grid;
  gap: 10px;
}

.evidence-item {
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  padding: 12px;
  background: #f7faff;
  transition: border-color 0.2s, box-shadow 0.2s;
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
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--pi-border-soft);
}

.evidence-highlight {
  border-color: var(--pi-primary);
  box-shadow: 0 0 0 2px rgba(23, 104, 242, 0.2);
}

.evidence-title {
  margin-top: 20px;
}

@media (max-width: 1280px) {
  .agent-grid {
    grid-template-columns: 1fr;
  }

  .agent-card :deep(.el-card__body) {
    min-height: auto;
  }

  .agent-panel-scroll {
    max-height: none;
  }

  .run-pipeline {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
