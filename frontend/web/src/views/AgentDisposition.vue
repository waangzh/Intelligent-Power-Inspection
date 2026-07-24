<template>
  <div class="agent-page">
    <PageHeader
      title="智能巡检研判工作台"
      description="AI 分析、策略判定与人工审批全程留痕"
      :breadcrumbs="[{ label: '运维中心' }, { label: '巡检研判助手' }]"
    >
      <template #actions>
        <el-tag effect="plain" type="primary" class="agent-badge">
          <el-icon><Connection /></el-icon>
          决策审计模式
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
          <span>案件工作台</span>
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
          <el-form-item label="操作员补充说明（不可信证据）">
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
            <div class="eyebrow">SYSTEM FACTS</div>
            <div class="panel-title">运行轨迹 / Agent 执行流</div>
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

        <div v-else class="step-timeline">
          <div
            v-for="step in agentStore.activeRun.steps"
            :key="step.id"
            class="step-entry"
          >
            <span class="step-sequence">{{ step.sequenceNo }}</span>
            <el-icon class="step-icon" :class="`step-${stepType(step.type)}`"><component :is="stepIcon(step.type)" /></el-icon>
            <div class="step-copy">
              <div class="step-summary">{{ step.summary }}</div>
              <code>{{ step.type }}</code>
            </div>
            <time>{{ fmt(step.createdAt) }}</time>
          </div>
        </div>

        <template v-if="agentStore.activeRun?.toolCalls.length">
          <div class="sub-section-title"><span>工具调用</span><small>{{ agentStore.activeRun.toolCalls.length }} 次</small></div>
          <el-table :data="agentStore.activeRun.toolCalls" size="small" class="tool-table">
            <el-table-column label="工具" min-width="132">
              <template #default="{ row }"><div class="tool-name"><el-icon><Tools /></el-icon>{{ row.toolName }}</div></template>
            </el-table-column>
            <el-table-column label="状态" width="82">
              <template #default="{ row }"><el-tag size="small" effect="light" :type="toolStatusType(row.status)">{{ toolStatusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="耗时" width="72">
              <template #default="{ row }"><span class="tool-duration">{{ row.durationMs == null ? '-' : `${row.durationMs}ms` }}</span></template>
            </el-table-column>
            <el-table-column label="结果摘要" min-width="180">
              <template #default="{ row }"><span class="tool-result">{{ row.resultSummary || row.errorMessage || row.reason || '-' }}</span></template>
            </el-table-column>
          </el-table>
        </template>
        </div>
      </el-card>

      <section class="decision-column">
        <el-card shadow="never" class="agent-card source-card ai-card">
          <div class="source-head">
            <span class="source-index">A</span>
            <div>
              <div class="source-kicker">AI ANALYSIS</div>
              <div class="panel-title">AI 分析</div>
            </div>
            <span class="state-label state-proposed">建议态</span>
          </div>
          <div class="agent-panel-scroll source-scroll">
            <el-empty v-if="!analysis" description="等待 AI 生成研判结论" :image-size="64" />
            <template v-else>
              <div class="ai-summary">
                <div class="metric-cell">
                  <span>缺陷等级</span>
                  <strong :class="`risk-${analysis.defectLevel.toLowerCase()}`">{{ defectLabel(analysis.defectLevel) }}</strong>
                </div>
                <div class="metric-cell">
                  <span>置信度</span>
                  <strong>{{ Math.round(analysis.confidence * 100) }}%</strong>
                </div>
              </div>
              <div class="analysis-block">
                <h3>推理摘要</h3>
                <p>{{ analysis.cause }}</p>
              </div>
              <div class="analysis-block recommendation-block">
                <h3>推荐动作</h3>
                <ol><li v-for="item in analysis.recommendedActions" :key="item">{{ item }}</li></ol>
              </div>
              <div class="analysis-block citations-block">
                <h3>引用证据</h3>
                <button
                  v-for="(item, idx) in analysis.evidenceReferences"
                  :key="item.evidenceId"
                  type="button"
                  class="citation-link"
                  @click="scrollToEvidence(item.evidenceId)"
                >
                  <span>[E{{ idx + 1 }}]</span>
                  <strong>{{ item.role }}</strong>
                  <small>{{ item.statement }}</small>
                </button>
              </div>
            </template>
          </div>
        </el-card>

        <el-card shadow="never" class="agent-card source-card policy-card">
          <div class="source-head">
            <span class="source-index">B</span>
            <div>
              <div class="source-kicker">SYSTEM POLICY</div>
              <div class="panel-title">系统策略</div>
            </div>
          </div>
          <div class="policy-counts">
            <div><strong>{{ policyCount('AUTO_EXECUTE') }}</strong><span>AUTO</span></div>
            <div><strong>{{ policyCount('REQUIRE_APPROVAL') }}</strong><span>APPROVAL</span></div>
            <div><strong>{{ policyCount('DENY') }}</strong><span>DENY</span></div>
          </div>
          <div class="agent-panel-scroll source-scroll">
            <div v-if="!policyActions.length" class="session-empty">暂无策略判定</div>
            <article v-for="action in policyActions" :key="action.id" class="policy-item">
              <div class="policy-line">
                <span class="policy-decision" :class="`decision-${action.policyDecision.toLowerCase()}`">{{ action.policyDecision }}</span>
                <el-tag size="small" effect="plain" :type="riskLevelType(action.riskLevel)">{{ riskLevelLabel(action.riskLevel) }}风险</el-tag>
              </div>
              <h3>{{ action.title }}</h3>
              <p>{{ action.policyReason }}</p>
              <div class="policy-meta"><code>{{ action.policyCode }}</code><span>置信度 {{ Math.round(action.confidence * 100) }}%</span></div>
            </article>

            <template v-if="executionActions.length">
              <div class="sub-section-title execution-title"><span>执行记录</span><small>区别于 AI 建议</small></div>
              <article v-for="action in executionActions" :key="`execution-${action.id}`" class="execution-item">
                <div>
                  <strong>{{ action.title }}</strong>
                  <span>{{ actionResultSummary(action) }}</span>
                </div>
                <div class="execution-state">
                  <el-tag size="small" effect="dark" :type="actionStatusType(action.status)">{{ actionStatusLabel(action.status) }}</el-tag>
                  <el-button v-if="action.status === 'FAILED' && can('agent:approve')" plain size="small" @click="retry(action)">重试</el-button>
                </div>
              </article>
            </template>
          </div>
        </el-card>

        <el-card shadow="never" class="agent-card source-card human-card">
          <div class="source-head">
            <span class="source-index">C</span>
            <div>
              <div class="source-kicker">HUMAN DECISION</div>
              <div class="panel-title">人工决策</div>
            </div>
            <span v-if="pendingActions.length" class="pending-count">{{ pendingActions.length }} 待审批</span>
          </div>
          <div class="agent-panel-scroll source-scroll">
            <div v-if="agentStore.activeRun?.question?.question" class="human-question">
              <div class="question-head"><el-icon><QuestionFilled /></el-icon><span class="question-title">补充证据</span></div>
              <p>{{ agentStore.activeRun.question.question.prompt }}</p>
              <template v-if="can('agent:run') && agentStore.activeRun.run.status === 'WAITING_HUMAN'">
                <el-input v-model="humanAnswer" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="输入现场确认信息" />
                <div class="question-actions">
                  <el-button type="warning" size="small" @click="submitAnswer">提交证据</el-button>
                  <el-button size="small" @click="continueWithEvidence">按现有证据继续</el-button>
                  <el-button type="danger" plain size="small" @click="cancelRun">取消 Run</el-button>
                </div>
              </template>
            </div>

            <article v-for="action in pendingActions" :key="`approval-${action.id}`" class="approval-item">
              <div class="approval-head">
                <div><strong>{{ action.title }}</strong><span>{{ action.reason }}</span></div>
                <el-tag size="small" :type="riskLevelType(action.riskLevel)">{{ riskLevelLabel(action.riskLevel) }}风险</el-tag>
              </div>
              <el-input v-if="can('agent:approve')" v-model="decisionComments[action.id]" size="small" maxlength="500" placeholder="审批意见（选填）" />
              <div v-if="can('agent:approve')" class="approval-actions">
                <el-button type="success" size="small" @click="confirm(action)"><el-icon><CircleCheck /></el-icon>批准并执行</el-button>
                <el-button type="danger" plain size="small" @click="reject(action)"><el-icon><Close /></el-icon>拒绝</el-button>
              </div>
            </article>

            <template v-if="reviewedActions.length">
              <div class="sub-section-title review-title"><span>人工处理记录</span><small>{{ reviewedActions.length }} 条</small></div>
              <article v-for="action in reviewedActions" :key="`review-${action.id}`" class="review-item">
                <el-tag size="small" effect="plain" :type="actionStatusType(action.status)">{{ actionStatusLabel(action.status) }}</el-tag>
                <div><strong>{{ action.title }}</strong><span>{{ reviewerName(action) }} · {{ fmt(reviewedAt(action)) }}</span></div>
                <p v-if="reviewComment(action)">{{ reviewComment(action) }}</p>
              </article>
            </template>

            <div v-if="!pendingActions.length && !reviewedActions.length && !agentStore.activeRun?.question?.question" class="human-empty">
              <el-icon><CircleCheck /></el-icon>
              <span>当前无待人工决策事项</span>
            </div>
          </div>
        </el-card>
      </section>

      <el-card shadow="never" class="agent-card evidence-panel">
        <div class="source-head evidence-head">
          <span class="source-index"><Tools /></span>
          <div>
            <div class="source-kicker">VERIFIED CONTEXT</div>
            <div class="panel-title">证据详情</div>
          </div>
          <span class="evidence-count">{{ agentStore.activeRun?.evidence.length ?? 0 }}</span>
        </div>
        <div class="agent-panel-scroll source-scroll">
        <div class="evidence-list">
          <article
            v-for="item in agentStore.activeRun?.evidence ?? []"
            :key="item.id"
            :id="`evidence-${item.id}`"
            class="evidence-item"
            :class="{ 'evidence-highlight': highlightEvidenceId === item.id }"
          >
            <div class="evidence-meta">
              <div>
                <span v-if="evidenceReferenceIndex(item.id)" class="evidence-seq">E{{ evidenceReferenceIndex(item.id) }}</span>
                <el-tag size="small" effect="plain">{{ item.sourceType }}</el-tag>
              </div>
              <span>{{ fmt(item.collectedAt) }}</span>
            </div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.summary }}</p>
            <div class="evidence-foot"><code>{{ item.sourceId || item.id }}</code><span>{{ item.contentHash.slice(0, 10) }}</span></div>
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
  Close,
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
import type { AgentCaseStatus, AgentPolicyDecision, AgentRiskLevel, AgentRunStatus, AgentStepType, AuditedAgentAction, AuditedAgentActionStatus, AuditedAgentConclusion, AuditedAgentEvidence, AuditedAgentToolCall } from '@/types/agent'

const pageRoute = useRoute()
const agentStore = useAgentStore()
const alarmStore = useAlarmStore()
const taskStore = useTaskStore()
const { can } = usePermission()
const form = reactive({ taskId: '', alarmId: '', goal: '', operatorNote: '' })
const caseFilter = ref('')
const highlightEvidenceId = ref('')
const humanAnswer = ref('')
const decisionComments = reactive<Record<string, string>>({})

const caseFilterChips = [
  { key: '', label: '全部' },
  { key: 'ANALYZING', label: '分析中' },
  { key: 'WAITING_HUMAN', label: '待补充' },
  { key: 'WAITING_APPROVAL', label: '待审批' },
  { key: 'RESOLVED', label: '已解决' },
  { key: 'FAILED', label: '失败' },
]

const runPhases = [
  { key: 'case', label: '案件创建' },
  { key: 'evidence', label: '证据采集' },
  { key: 'analysis', label: 'AI 研判' },
  { key: 'human', label: '人工介入' },
  { key: 'action', label: '动作执行' },
  { key: 'done', label: '闭环完成' },
]

const analysis = computed<AuditedAgentConclusion | undefined>(() => agentStore.activeRun?.conclusion)
const selectedAlarm = computed(() => alarmStore.alarms.find((item) => item.id === form.alarmId))
const selectedTask = computed(() => taskStore.getTaskById(form.taskId))
const taskOptions = computed(() => taskStore.tasks)
const alarmOptions = computed(() => !form.taskId ? alarmStore.alarms : alarmStore.alarms.filter((item) => item.taskId === form.taskId || item.id === form.alarmId))
const isAlarmOnlyAnalysis = computed(() => Boolean(selectedAlarm.value && !selectedTask.value))
const activeSubtitle = computed(() => agentStore.activeCaseSummary ? [agentStore.activeCaseSummary.title, agentStore.activeRun ? `Run #${agentStore.activeRun.run.runNumber}` : '尚未运行'].join(' / ') : '选择或创建一个处置案件')
const filteredCases = computed(() => caseFilter.value ? agentStore.cases.filter((item) => item.status === caseFilter.value) : agentStore.cases)
const policyActions = computed(() => agentStore.activeRun?.actions ?? [])
const pendingActions = computed(() => policyActions.value.filter((item) => item.status === 'PROPOSED' && item.policyDecision === 'REQUIRE_APPROVAL'))
const reviewedActions = computed(() => policyActions.value.filter((item) => Boolean(item.approvedAt || item.rejectedAt)))
const executionActions = computed(() => policyActions.value.filter((item) => !['PROPOSED', 'APPROVED', 'REJECTED'].includes(item.status)))

const runPhaseIndex = computed(() => {
  const status = agentStore.activeRun?.run.status
  if (!status) return 0
  if (['QUEUED'].includes(status)) return 0
  if (['RUNNING', 'WAITING_TOOL'].includes(status)) return agentStore.activeRun?.steps.some((step) => step.type === 'LLM_ANALYZED') ? 2 : 1
  if (['WAITING_HUMAN', 'WAITING_APPROVAL'].includes(status)) return 3
  if (status === 'ACTION_EXECUTING') return 4
  if (['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'STEP_LIMIT_REACHED'].includes(status)) return 5
  return 2
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
async function confirm(action: AuditedAgentAction) { await agentStore.approveAction(action, decisionComments[action.id]?.trim() || '批准执行'); delete decisionComments[action.id]; ElMessage.success('动作已批准') }
async function reject(action: AuditedAgentAction) { await agentStore.rejectAction(action, decisionComments[action.id]?.trim() || '拒绝执行'); delete decisionComments[action.id]; ElMessage.success('动作已拒绝') }
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
function toolStatusLabel(status: AuditedAgentToolCall['status']) { return { REQUESTED: '待调用', RUNNING: '调用中', SUCCEEDED: '成功', FAILED: '失败', TIMED_OUT: '超时', CANCELLED: '取消' }[status] }
function toolStatusType(status: AuditedAgentToolCall['status']) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' || status === 'TIMED_OUT' ? 'danger' : status === 'CANCELLED' ? 'info' : 'warning' }
function defectLabel(level: AgentRiskLevel) { return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }[level] }
function riskLevelLabel(level: AgentRiskLevel) { return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }[level] }
function riskLevelType(level: AgentRiskLevel) { return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }[level] as 'info' | 'warning' | 'danger' }
function policyCount(decision: AgentPolicyDecision) { return policyActions.value.filter((item) => item.policyDecision === decision).length }
function reviewerName(action: AuditedAgentAction) { return action.rejectedById || action.approvedById || '系统记录' }
function reviewedAt(action: AuditedAgentAction) { return action.rejectedAt || action.approvedAt }
function reviewComment(action: AuditedAgentAction) { return action.rejectionComment || action.approvalComment || '' }
function actionResultSummary(action: AuditedAgentAction) {
  if (action.errorMessage) return action.errorMessage
  if (action.status === 'EXECUTING') return `开始于 ${fmt(action.executionStartedAt)}`
  if (action.result) return `结果已回写 · ${fmt(action.executionCompletedAt)}`
  return actionStatusLabel(action.status)
}
function evidenceReferenceIndex(evidenceId: string) {
  const index = analysis.value?.evidenceReferences.findIndex((item) => item.evidenceId === evidenceId) ?? -1
  return index < 0 ? 0 : index + 1
}
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
  --agent-muted: #708197;
  --agent-ink: #19324f;
  --agent-blue: #1768f2;
  --agent-blue-soft: #f4f8ff;
  --agent-gray-soft: #f7f9fc;
  --agent-amber: #b85c00;
  --agent-amber-soft: #fff9ee;
}

.agent-badge,
.section-head,
.source-head,
.question-head,
.tool-name {
  display: flex;
  align-items: center;
}

.agent-badge { gap: 5px; }

.agent-grid {
  display: grid;
  grid-template-columns: 270px minmax(480px, 1.55fr) minmax(380px, 1.15fr) minmax(280px, 0.8fr);
  grid-template-areas: "sidebar run decision evidence";
  gap: 12px;
  min-height: calc(100vh - 154px);
  align-items: stretch;
}

.sidebar-card { grid-area: sidebar; }
.run-card { grid-area: run; }
.decision-column { grid-area: decision; }
.evidence-panel { grid-area: evidence; }

.agent-card {
  min-width: 0;
  height: 100%;
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(34, 62, 94, 0.05);
}

.agent-card :deep(.el-card__body) {
  display: flex;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  padding: 15px;
  overflow: hidden;
}

.sidebar-card,
.run-card,
.evidence-panel { min-height: 760px; }

.decision-column {
  display: grid;
  min-width: 0;
  grid-template-rows: minmax(250px, 1.05fr) minmax(250px, 1.05fr) minmax(250px, 1fr);
  gap: 12px;
}

.agent-panel-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
  scrollbar-width: thin;
  scrollbar-color: #c9d5e3 transparent;
}

.section-head {
  gap: 8px;
  margin-bottom: 13px;
  color: var(--agent-ink);
  font-size: 15px;
  font-weight: 700;
}

.section-divider {
  height: 1px;
  margin: 16px 0;
  background: #e9eef4;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 13px;
}

.panel-title {
  color: var(--agent-ink);
  font-size: 15px;
  font-weight: 750;
  line-height: 1.35;
}

.eyebrow,
.source-kicker {
  margin-bottom: 2px;
  color: #7a8da4;
  font-family: Consolas, 'Microsoft YaHei', monospace;
  font-size: 10px;
  font-weight: 700;
}

.muted {
  margin-top: 3px;
  color: var(--agent-muted);
  font-size: 11px;
  line-height: 1.45;
}

.sub-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 16px 0 8px;
  color: #435c78;
  font-size: 13px;
  font-weight: 700;
}

.sub-section-title small {
  color: #8998aa;
  font-size: 10px;
  font-weight: 600;
}

.run-button { width: 100%; margin-top: 3px; }
.degraded-alert { margin: 0 0 10px; }

.create-form :deep(.el-form-item) { margin-bottom: 13px; }
.create-form :deep(.el-form-item__label) { color: #526983; font-size: 12px; font-weight: 650; }

.option-main {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.option-main span,
.session-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-sub { color: var(--agent-muted); font-size: 11px; }

.context-box {
  display: grid;
  gap: 7px;
  margin: 1px 0 11px;
  border: 1px solid #dfe7f0;
  border-radius: 6px;
  background: var(--agent-gray-soft);
  padding: 9px 11px;
}

.context-row { display: grid; gap: 3px; }
.context-row span { color: var(--agent-muted); font-size: 11px; }
.context-row strong { color: var(--agent-ink); font-size: 12px; line-height: 1.45; }

.case-chips { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 9px; }

.case-chip {
  min-height: 26px;
  padding: 3px 8px;
  border: 1px solid #dce5ef;
  border-radius: 5px;
  background: #fff;
  color: #526983;
  font-size: 10px;
  font-weight: 650;
  cursor: pointer;
}

.case-chip:hover { background: #f5f8fc; }
.case-chip.active { border-color: #79aaf6; background: #edf5ff; color: var(--agent-blue); }

.session-list { display: grid; gap: 7px; }

.session-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 54px;
  border: 1px solid #e3e9f1;
  border-radius: 6px;
  background: #fff;
  padding: 9px 10px;
  cursor: pointer;
  text-align: left;
}

.session-item:hover { background: #f7faff; }
.session-item.active { border-color: #77a8f5; background: #f1f6ff; box-shadow: inset 3px 0 var(--agent-blue); }
.session-accent { display: none; }
.session-body { flex: 1; min-width: 0; }
.session-title { color: var(--agent-ink); font-size: 12px; font-weight: 650; }
.session-meta { margin-top: 3px; color: var(--agent-muted); font-size: 10px; }
.session-empty { padding: 18px 10px; text-align: center; color: var(--agent-muted); font-size: 12px; }

.run-pipeline {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 12px;
  padding: 12px 8px 10px;
  border: 1px solid #e0e7ef;
  border-radius: 6px;
  background: #f9fbfd;
}

.pipeline-step {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.pipeline-step:not(:last-child)::after {
  position: absolute;
  top: 13px;
  left: calc(50% + 16px);
  width: calc(100% - 32px);
  height: 1px;
  background: #cad5e2;
  content: '';
}

.pipeline-step.done:not(:last-child)::after { background: #7db0fa; }

.pipeline-dot {
  z-index: 1;
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  border: 1px solid #ccd7e3;
  border-radius: 50%;
  background: #fff;
  color: #77889d;
  font-size: 11px;
  font-weight: 700;
}

.pipeline-label { color: var(--agent-muted); font-size: 10px; font-weight: 650; white-space: nowrap; }
.pipeline-step.done .pipeline-dot { border-color: #78aaf8; background: #edf5ff; color: var(--agent-blue); }
.pipeline-step.done .pipeline-label { color: #3f6fae; }
.pipeline-step.active .pipeline-dot { border-color: var(--agent-blue); background: var(--agent-blue); color: #fff; box-shadow: 0 0 0 3px rgba(23, 104, 242, 0.13); }
.pipeline-step.active .pipeline-label { color: var(--agent-blue); }

.run-select { width: 100%; margin-bottom: 9px; }
.step-timeline { position: relative; margin: 2px 0 4px; }
.step-timeline::before { position: absolute; top: 18px; bottom: 18px; left: 17px; width: 1px; background: #d9e2ec; content: ''; }

.step-entry {
  position: relative;
  display: grid;
  grid-template-columns: 34px 20px minmax(0, 1fr) auto;
  gap: 7px;
  align-items: start;
  min-height: 48px;
  padding: 7px 0;
}

.step-sequence {
  z-index: 1;
  display: grid;
  width: 24px;
  height: 24px;
  margin-left: 5px;
  place-items: center;
  border: 1px solid #b9c9dc;
  border-radius: 50%;
  background: #fff;
  color: #55708e;
  font-family: Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
}

.step-icon { margin-top: 4px; font-size: 15px; }
.step-icon.step-primary { color: var(--agent-blue); }
.step-icon.step-success { color: #159455; }
.step-icon.step-warning { color: #d66b00; }
.step-icon.step-danger { color: #d7352f; }
.step-copy { min-width: 0; }
.step-summary { color: #29445f; font-size: 12px; font-weight: 600; line-height: 1.4; }
.step-copy code { color: #8594a7; font-size: 9px; overflow-wrap: anywhere; }
.step-entry time { padding-top: 3px; color: #8797aa; font-size: 10px; white-space: nowrap; }

.tool-table { border: 1px solid #e3eaf2; border-radius: 6px; overflow: hidden; }
.tool-name { gap: 6px; min-width: 0; color: #29445f; font-size: 12px; font-weight: 650; }
.tool-duration { color: #72849a; font-family: Consolas, monospace; font-size: 10px; }
.tool-result { display: block; color: #5d7087; font-size: 11px; line-height: 1.4; }

.source-card :deep(.el-card__body) { padding: 13px; }
.source-head { min-height: 38px; gap: 9px; margin-bottom: 10px; }
.source-head > div { min-width: 0; flex: 1; }

.source-index {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 5px;
  background: #36516f;
  color: #fff;
  font-family: Consolas, monospace;
  font-size: 14px;
  font-weight: 800;
}

.state-label,
.pending-count,
.evidence-count {
  flex-shrink: 0;
  border-radius: 4px;
  padding: 3px 6px;
  font-size: 10px;
  font-weight: 700;
}

.state-proposed { border: 1px solid #a8cafa; background: #eef5ff; color: #1768f2; }
.pending-count { border: 1px solid #f1bd72; background: #fff4df; color: #a84d00; }
.source-scroll { padding-right: 3px; }

.ai-card { border-color: #a9caf7; background: var(--agent-blue-soft); }
.ai-card :deep(.el-card__body) { background: var(--agent-blue-soft); }
.ai-card .source-index { background: var(--agent-blue); }
.ai-card .source-kicker,
.ai-card .panel-title { color: #1458bd; }

.ai-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid #cbdcf5;
  border-radius: 6px;
  background: #fff;
}

.metric-cell { padding: 10px 11px; }
.metric-cell + .metric-cell { border-left: 1px solid #dce7f6; }
.metric-cell span { display: block; margin-bottom: 4px; color: #68809e; font-size: 10px; font-weight: 650; }
.metric-cell strong { color: #174ea6; font-size: 20px; line-height: 1.1; }
.metric-cell .risk-high,
.metric-cell .risk-critical { color: #d7352f; }
.metric-cell .risk-medium { color: #b85c00; }
.metric-cell .risk-low { color: #16844b; }

.analysis-block { padding: 11px 2px 10px; border-bottom: 1px solid #dbe7f7; }
.analysis-block:last-child { border-bottom: 0; }
.analysis-block h3,
.policy-item h3,
.evidence-item h4 { margin: 0 0 6px; color: #315272; font-size: 11px; font-weight: 750; }
.analysis-block p,
.policy-item p,
.evidence-item p { margin: 0; color: #3e5875; font-size: 11px; line-height: 1.55; }
.analysis-block ol { margin: 0; padding-left: 20px; color: #294e7e; font-size: 11px; line-height: 1.65; }

.citation-link {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 1px 7px;
  width: 100%;
  margin-top: 5px;
  border: 0;
  border-left: 2px solid #79aaf3;
  background: #fff;
  padding: 7px 8px;
  color: #295da4;
  text-align: left;
  cursor: pointer;
}

.citation-link:hover { background: #edf5ff; }
.citation-link span { grid-row: 1 / 3; font-family: Consolas, monospace; font-size: 10px; font-weight: 700; }
.citation-link strong { font-size: 10px; }
.citation-link small { color: #667d98; font-size: 10px; line-height: 1.35; }

.policy-card { background: var(--agent-gray-soft); }
.policy-card :deep(.el-card__body) { background: var(--agent-gray-soft); }
.policy-counts { display: grid; grid-template-columns: repeat(3, 1fr); margin-bottom: 8px; border: 1px solid #dfe6ee; border-radius: 6px; background: #fff; }
.policy-counts div { padding: 7px 5px; text-align: center; }
.policy-counts div + div { border-left: 1px solid #e3e9f0; }
.policy-counts strong { display: block; color: #29445f; font-size: 16px; }
.policy-counts span { color: #7c8ea2; font-family: Consolas, monospace; font-size: 8px; }

.policy-item { padding: 9px 1px 10px; border-bottom: 1px solid #dfe6ee; }
.policy-line { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 7px; }
.policy-decision { max-width: 100%; overflow: hidden; border-radius: 4px; padding: 3px 6px; font-family: Consolas, monospace; font-size: 9px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.decision-auto_execute { background: #e8f7ef; color: #137a45; }
.decision-require_approval { background: #fff0d8; color: #a64b00; }
.decision-deny { background: #ffe8e6; color: #bd2e27; }
.policy-meta { display: flex; justify-content: space-between; gap: 8px; margin-top: 6px; color: #78899d; font-size: 9px; }
.policy-meta code { color: #5d7087; font-size: 9px; }
.execution-title { margin-top: 12px; }

.execution-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  border-left: 3px solid #4a617a;
  background: #fff;
  padding: 8px 9px;
}

.execution-item > div { min-width: 0; }
.execution-item strong,
.execution-item span { display: block; }
.execution-item strong { color: #29445f; font-size: 10px; }
.execution-item span { margin-top: 2px; color: #77899d; font-size: 9px; line-height: 1.35; }
.execution-state { display: flex; flex-shrink: 0; align-items: center; gap: 5px; }
.execution-state :deep(.el-button) { margin: 0; }

.human-card { border-color: #efb964; background: var(--agent-amber-soft); }
.human-card :deep(.el-card__body) { background: var(--agent-amber-soft); }
.human-card .source-index { background: #d66b00; }
.human-card .source-kicker,
.human-card .panel-title { color: #9d4900; }

.human-question,
.approval-item {
  border: 1px solid #efc783;
  border-radius: 6px;
  background: #fff;
  padding: 10px;
}

.human-question { margin-bottom: 8px; }
.question-head { gap: 6px; color: #a84f00; }
.question-title { font-size: 11px; font-weight: 750; }
.human-question p { margin: 7px 0 9px; color: #674c2e; font-size: 11px; line-height: 1.5; }
.question-actions,
.approval-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.question-actions :deep(.el-button),
.approval-actions :deep(.el-button) { margin: 0; }

.approval-item + .approval-item { margin-top: 8px; }
.approval-head { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 9px; }
.approval-head > div { min-width: 0; }
.approval-head strong,
.approval-head span { display: block; }
.approval-head strong { color: #563819; font-size: 11px; }
.approval-head span { margin-top: 3px; color: #7d654d; font-size: 10px; line-height: 1.4; }
.review-title { margin-top: 12px; }

.review-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px 8px;
  align-items: start;
  padding: 8px 2px;
  border-bottom: 1px solid #efdfc7;
}

.review-item div strong,
.review-item div span { display: block; }
.review-item div strong { color: #5a4229; font-size: 10px; }
.review-item div span { margin-top: 2px; color: #8a755f; font-size: 9px; }
.review-item p { grid-column: 2; margin: 0; color: #725d47; font-size: 10px; line-height: 1.4; }
.human-empty { display: flex; min-height: 88px; align-items: center; justify-content: center; gap: 7px; color: #8c765c; font-size: 11px; }

.evidence-panel { background: #fbfcfe; }
.evidence-panel :deep(.el-card__body) { background: #fbfcfe; }
.evidence-head { margin-bottom: 12px; }
.evidence-head .source-index { background: #526b85; }
.evidence-head .source-index :deep(svg) { width: 15px; }
.evidence-count { min-width: 24px; background: #eaf0f6; color: #526b85; text-align: center; }
.evidence-list { display: grid; gap: 8px; }

.evidence-item {
  border: 1px solid #dfe6ee;
  border-radius: 6px;
  background: #fff;
  padding: 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.evidence-meta { display: flex; justify-content: space-between; gap: 7px; align-items: center; margin-bottom: 7px; color: var(--agent-muted); font-size: 9px; }
.evidence-meta > div { display: flex; align-items: center; gap: 5px; min-width: 0; }
.evidence-seq { color: #1768f2; font-family: Consolas, monospace; font-size: 10px; font-weight: 800; }
.evidence-foot { display: flex; justify-content: space-between; gap: 8px; margin-top: 7px; color: #8b99a9; font-size: 8px; }
.evidence-foot code { max-width: 70%; overflow: hidden; color: #718397; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.evidence-item img { width: 100%; max-height: 210px; margin-top: 8px; border: 1px solid #dfe6ee; border-radius: 4px; object-fit: cover; }
.evidence-highlight { border-color: var(--agent-blue); box-shadow: 0 0 0 2px rgba(23, 104, 242, 0.16); }

@media (max-width: 1700px) {
  .agent-grid {
    grid-template-columns: 270px minmax(420px, 1.25fr) minmax(360px, 1fr);
    grid-template-areas:
      "sidebar run decision"
      "sidebar evidence evidence";
  }

  .evidence-panel { min-height: 360px; }
  .evidence-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 1320px) {
  .agent-grid {
    grid-template-columns: 270px minmax(0, 1fr);
    grid-template-areas:
      "sidebar run"
      "decision decision"
      "evidence evidence";
  }

  .decision-column { grid-template-columns: repeat(3, minmax(0, 1fr)); grid-template-rows: minmax(380px, auto); }
}

@media (max-width: 1000px) {
  .agent-grid {
    grid-template-columns: 1fr;
    grid-template-areas: "sidebar" "run" "decision" "evidence";
    min-height: 0;
  }

  .sidebar-card,
  .run-card,
  .evidence-panel { min-height: auto; }
  .agent-card :deep(.el-card__body) { height: auto; min-height: 0; overflow: visible; }
  .decision-column { grid-template-columns: 1fr; grid-template-rows: auto; }
  .source-card { min-height: 340px; }
  .agent-panel-scroll { overflow: visible; }
  .evidence-list { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .run-pipeline { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px 0; }
  .pipeline-step:nth-child(3)::after { display: none; }
  .step-entry { grid-template-columns: 30px 18px minmax(0, 1fr); }
  .step-entry time { grid-column: 3; padding-top: 0; }
  .tool-table :deep(.el-table__body-wrapper),
  .tool-table :deep(.el-table__header-wrapper) { overflow-x: auto; }
}
</style>
