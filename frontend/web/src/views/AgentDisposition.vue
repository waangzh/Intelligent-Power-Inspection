<template>
  <div class="agent-page">
    <PageHeader
      title="巡检处置 Agent"
      description="任务、告警、证据与待确认动作"
      :breadcrumbs="[{ label: '运维中心' }, { label: '巡检处置 Agent' }]"
    >
      <template #actions>
        <el-button :disabled="!agentStore.activeSession || agentStore.loading" @click="agentStore.rerunActive()">
          <el-icon><Refresh /></el-icon>
          重新分析
        </el-button>
      </template>
    </PageHeader>

    <div class="agent-grid">
      <section class="panel input-panel">
        <div class="panel-title">输入</div>
        <el-form label-position="top" size="small">
          <el-form-item label="任务 ID">
            <el-input v-model="form.taskId" clearable placeholder="task_demo" />
          </el-form-item>
          <el-form-item label="告警 ID">
            <el-input v-model="form.alarmId" clearable placeholder="alarm_seed_001" />
          </el-form-item>
          <el-form-item label="补充说明">
            <el-input v-model="form.prompt" type="textarea" :rows="4" resize="none" />
          </el-form-item>
          <el-button type="primary" :loading="agentStore.loading" class="run-button" @click="createSession">
            <el-icon><CaretRight /></el-icon>
            启动分析
          </el-button>
        </el-form>

        <div class="panel-title history-title">会话</div>
        <div class="session-list">
          <button
            v-for="session in agentStore.sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === agentStore.activeSession?.id }"
            @click="agentStore.loadSession(session.id)"
          >
            <span>{{ session.title }}</span>
            <el-tag size="small" :type="statusType(session.status)">{{ statusLabel(session.status) }}</el-tag>
          </button>
        </div>
      </section>

      <section class="panel run-panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">执行步骤</div>
            <div class="muted">{{ activeSubtitle }}</div>
          </div>
          <el-tag v-if="agentStore.activeSession" :type="statusType(agentStore.activeSession.status)">
            {{ statusLabel(agentStore.activeSession.status) }}
          </el-tag>
        </div>

        <el-empty v-if="!agentStore.activeSession" description="暂无会话" />
        <el-timeline v-else class="step-timeline">
          <el-timeline-item
            v-for="step in agentStore.latestSteps"
            :key="step.id"
            :timestamp="fmt(step.createdAt)"
            :type="stepType(step.type)"
          >
            <div class="step-row">
              <span>{{ step.message }}</span>
              <code>{{ step.type }}</code>
            </div>
          </el-timeline-item>
        </el-timeline>

        <div class="panel-title action-title">待确认动作</div>
        <el-table :data="agentStore.activeSession?.actions ?? []" size="small" empty-text="暂无动作">
          <el-table-column label="动作" min-width="170">
            <template #default="{ row }: { row: AgentAction }">
              <div class="action-name">{{ row.title }}</div>
              <div class="muted">{{ actionTypeLabel(row.type) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }: { row: AgentAction }">
              <el-tag size="small" :type="actionStatusType(row.status)">{{ actionStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="142">
            <template #default="{ row }: { row: AgentAction }">
              <el-button
                v-if="row.status === 'PENDING'"
                text
                type="primary"
                size="small"
                @click="confirm(row)"
              >确认</el-button>
              <el-button
                v-if="row.status === 'PENDING'"
                text
                type="danger"
                size="small"
                @click="reject(row)"
              >拒绝</el-button>
              <span v-else class="muted">已处理</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel insight-panel">
        <div class="panel-title">研判</div>
        <el-empty v-if="!analysis" description="暂无研判" />
        <template v-else>
          <div class="level-card" :class="`level-${analysis.defectLevel.toLowerCase()}`">
            <span>缺陷等级</span>
            <strong>{{ defectLabel(analysis.defectLevel) }}</strong>
            <small>置信度 {{ Math.round((analysis.confidence || 0) * 100) }}%</small>
          </div>
          <div class="insight-block">
            <h3>原因</h3>
            <p>{{ analysis.cause }}</p>
          </div>
          <div class="insight-block">
            <h3>建议动作</h3>
            <ul>
              <li v-for="item in analysis.recommendedActions" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="insight-block">
            <h3>引用依据</h3>
            <el-tag v-for="item in analysis.citations" :key="item" class="citation" size="small">{{ item }}</el-tag>
          </div>
        </template>

        <div class="panel-title evidence-title">证据</div>
        <div class="evidence-list">
          <article v-for="item in agentStore.activeSession?.evidence ?? []" :key="item.id" class="evidence-item">
            <div class="evidence-meta">
              <el-tag size="small">{{ item.type }}</el-tag>
              <span>{{ fmt(item.createdAt) }}</span>
            </div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.content }}</p>
            <img v-if="item.imageUrl" :src="item.imageUrl" alt="证据图像" />
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { CaretRight, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAgentStore } from '@/stores/agent'
import type { AgentAction, AgentAnalysis, AgentStatus } from '@/types/agent'

const agentStore = useAgentStore()
const form = reactive({
  taskId: '',
  alarmId: '',
  prompt: '',
})

const analysis = computed<AgentAnalysis | undefined>(() => agentStore.activeSession?.analysis)
const activeSubtitle = computed(() => {
  const session = agentStore.activeSession
  if (!session) return '选择或创建一个会话'
  return [session.taskId, session.alarmId].filter(Boolean).join(' / ') || session.inputType
})

onMounted(async () => {
  await agentStore.loadSessions()
  if (agentStore.sessions[0]) {
    await agentStore.loadSession(agentStore.sessions[0].id)
  }
})

onUnmounted(() => {
  agentStore.stopRealtime()
})

async function createSession() {
  if (!form.taskId.trim() && !form.alarmId.trim() && !form.prompt.trim()) {
    ElMessage.warning('请至少填写任务、告警或补充说明')
    return
  }
  await agentStore.createSession({
    taskId: form.taskId.trim() || undefined,
    alarmId: form.alarmId.trim() || undefined,
    prompt: form.prompt.trim() || undefined,
  })
  ElMessage.success('Agent 分析已启动')
}

async function confirm(action: AgentAction) {
  await agentStore.confirmAction(action)
  ElMessage.success('动作已确认')
}

async function reject(action: AgentAction) {
  await agentStore.rejectAction(action)
  ElMessage.success('动作已拒绝')
}

function fmt(iso?: string) {
  return iso ? new Date(iso).toLocaleString('zh-CN') : '-'
}

function statusLabel(status: AgentStatus) {
  return { RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '失败' }[status]
}

function statusType(status: AgentStatus) {
  return { RUNNING: 'warning', SUCCEEDED: 'success', FAILED: 'danger' }[status] as 'warning' | 'success' | 'danger'
}

function stepType(type: string) {
  if (type.includes('FAILED')) return 'danger'
  if (type.includes('SUCCEEDED') || type.includes('COMPLETED')) return 'success'
  if (type.includes('ACTION')) return 'warning'
  return 'primary'
}

function actionTypeLabel(type: string) {
  return {
    CREATE_WORK_ORDER_DRAFT: '创建工单草稿',
    PUSH_NOTIFICATION: '推送通知',
  }[type] || type
}

function actionStatusLabel(status: AgentAction['status']) {
  return { PENDING: '待确认', CONFIRMED: '已确认', REJECTED: '已拒绝' }[status]
}

function actionStatusType(status: AgentAction['status']) {
  return { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'info' }[status] as 'warning' | 'success' | 'info'
}

function defectLabel(level: AgentAnalysis['defectLevel']) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }[level]
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

@media (max-width: 1180px) {
  .agent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
