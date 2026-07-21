<template>
  <div v-if="task">
    <PageHeader :title="task.name" description="巡检任务详情" :breadcrumbs="breadcrumbs">
      <template #actions>
        <TaskStatusTag
          :status="taskStore.statusOf(task)"
          :manual-reconciliation-required="execution?.manualReconciliationRequired"
        />
        <el-button
          v-if="can('task:start-local') && canShowLocalStart"
          type="primary"
          :disabled="!!startDisabledReason('LOCAL_CONFIRM')"
          :title="startDisabledReason('LOCAL_CONFIRM')"
          @click="startInspection('LOCAL_CONFIRM')"
        >下发并等待本地启动</el-button>
        <el-button
          v-if="can('task:start-remote') && canShowRemoteStart"
          type="warning"
          :disabled="!!startDisabledReason('REMOTE_IMMEDIATE')"
          :title="startDisabledReason('REMOTE_IMMEDIATE')"
          @click="confirmRemoteStart"
        >远程立即启动</el-button>
        <el-button v-if="can('task:dispatch') && !task.executionId && task.status === 'CREATED'" type="primary" @click="taskStore.dispatch(task.id)">下发</el-button>
        <el-button v-if="canPause" :loading="controlBusy" :disabled="controlBusy" @click="sendControl('PAUSE')">暂停</el-button>
        <el-button v-if="canResume" type="primary" :loading="controlBusy" :disabled="controlBusy" @click="sendControl('RESUME')">恢复</el-button>
        <el-button v-if="canTakeover" type="warning" :loading="controlBusy" :disabled="controlBusy" @click="requestTakeover">人工接管</el-button>
        <el-button v-if="can('task:dispatch')" type="success" @click="openAgent">Agent 分析</el-button>
        <el-button
          v-if="canCancelTask"
          type="danger"
          plain
          :loading="controlBusy"
          :disabled="controlBusy"
          @click="cancelTask"
        >取消任务</el-button>
        <el-button
          v-if="canEstopTask"
          type="danger"
          :loading="controlBusy"
          :disabled="controlBusy"
          @click="emergencyStopTask"
        >远程急停</el-button>
        <el-button @click="router.push('/tasks')">返回列表</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>基本信息</template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="路线">{{ route?.name }}</el-descriptions-item>
            <el-descriptions-item label="机器人">{{ robot?.name }}</el-descriptions-item>
            <el-descriptions-item label="进度">
              <el-progress :percentage="execution?.progress ?? task.progress" style="width: 200px" />
            </el-descriptions-item>
            <el-descriptions-item label="当前检查点">第 {{ task.currentCheckpointSeq }} / {{ route?.checkpoints.length ?? 0 }} 个</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmt(task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ execution?.startedAt ? fmt(execution.startedAt) : '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions v-if="execution" :column="2" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="执行 ID">{{ execution.executionId }}</el-descriptions-item>
            <el-descriptions-item label="路线修订">{{ execution.routeRevisionId }}</el-descriptions-item>
            <el-descriptions-item label="部署 ID">{{ execution.deploymentId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="启动方式">{{ execution.startMode === 'LOCAL_CONFIRM' ? '机器人本地确认' : '远程立即启动' }}</el-descriptions-item>
            <el-descriptions-item label="任务下发时间">{{ execution.startRequestedAt ? fmt(execution.startRequestedAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="机器人准备完成">{{ execution.robotReadyAt ? fmt(execution.robotReadyAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="本地确认时间">{{ execution.localConfirmedAt ? fmt(execution.localConfirmedAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前目标">{{ execution.currentTargetId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最后事件">{{ execution.lastEventAt ? fmt(execution.lastEventAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="路线哈希">{{ shortHash(execution.routeContentSha256) }}</el-descriptions-item>
            <el-descriptions-item label="地图哈希">{{ shortHash(execution.mapImageSha256) }}</el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="execution && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(task))"
            :title="eligibilityReason || `部署为${DEPLOYMENT_STATE_LABELS.READY_FOR_ROBOT}且身份、路线哈希、地图哈希一致后才允许启动`"
            :type="eligibility?.eligible ? 'success' : 'warning'"
            :closable="false"
            show-icon
            style="margin-top: 12px"
          />
          <el-alert
            v-if="execution && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(task)) && canShowLocalStart"
            title="本地确认启动"
            description="任务将下发到机器人。机器人完成路线和地图校验后，需要在机器人触摸屏上确认，机器人不会立即移动。"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 12px"
          />
          <el-alert
            v-if="executionStatus === 'WAITING_LOCAL_CONFIRM'"
            title="等待机器人本地确认"
            :description="`${robot?.name || execution?.robotId || '机器人'}已准备完成，已等待 ${waitingDuration}。请前往机器人触摸屏确认启动。`"
            type="warning"
            :closable="false"
            show-icon
            style="margin-top: 12px"
          />
          <el-alert
            v-if="execution?.lastErrorMessage"
            :title="`${execution.lastErrorCode || 'EXECUTION_ERROR'}：${execution.lastErrorMessage}`"
            type="error"
            :closable="false"
            show-icon
            style="margin-top: 12px"
          />
          <el-alert
            v-if="execution?.latestControl"
            :title="commandFeedback.title"
            :description="commandFeedback.description"
            :type="commandFeedback.type"
            :closable="false"
            show-icon
            style="margin-top: 12px"
          />
        </el-card>

        <el-card shadow="never">
          <template #header>执行时间线</template>
          <el-timeline v-if="events.length">
            <el-timeline-item
              v-for="ev in events"
              :key="ev.id"
              :timestamp="fmt(ev.createdAt)"
              placement="top"
              :type="timelineType(ev.type)"
            >
              <p><strong>{{ eventLabel(ev.type) }}</strong> — {{ ev.message }}</p>
              <p v-if="ev.checkpointName" class="muted">检查点: {{ ev.checkpointName }}</p>
              <img v-if="ev.imageUrl" :src="ev.imageUrl" class="ev-img" alt="截图" />
            </el-timeline-item>
          </el-timeline>
          <div v-else class="empty-hint">暂无执行记录，下发任务后将自动生成</div>
          <ListPagination :total="taskStore.eventTotals[taskId] ?? 0" :page="eventPage" @change="loadEventPage" />
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header>模型检测结果</template>
          <div v-if="taskRuns.length" class="detection-run-list">
            <article v-for="run in taskRuns" :key="run.runId" class="detection-run-item">
              <div class="run-meta">
                <strong>{{ run.checkpointId || '未关联检查点' }}</strong>
                <el-tag size="small" :type="run.status === 'SUCCEEDED' ? 'success' : run.status === 'FAILED' ? 'danger' : 'warning'">
                  {{ run.status === 'SUCCEEDED' ? '成功' : run.status === 'FAILED' ? '失败' : '检测中' }}
                </el-tag>
                <span>{{ run.createdAt ? fmt(run.createdAt) : '-' }}</span>
              </div>
              <p v-if="run.errorMessage" class="muted">{{ run.errorMessage }}</p>
              <p v-if="run.originalAvailable === false" class="muted">原始图片已按保留策略清理</p>
              <div class="run-images">
                <img v-if="run.inputImageUrl" :src="run.inputImageUrl" alt="机器人原始图片" />
                <img v-if="run.resultImageUrl" :src="run.resultImageUrl" alt="LocateAnything 标注结果" />
              </div>
              <p class="muted">定位结果 {{ run.findings.length }} 项；定位结果不等于异常结论</p>
            </article>
          </div>
          <div v-else class="empty-hint">暂无模型检测结果</div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>轨迹地图</template>
          <div style="height: 280px">
            <Map2D
              :center="mapCenter"
              :fallback-center="fallbackCenter"
              :route="route ?? null"
              :robot-position="robotPos"
            />
          </div>
        </el-card>
        <el-card shadow="never">
          <template #header>关联告警 ({{ taskAlarms.length }})</template>
          <el-table :data="taskAlarms" size="small" max-height="240">
            <el-table-column prop="message" label="内容" show-overflow-tooltip />
            <el-table-column label="级别" width="70">
              <template #default="{ row }">
                <el-tag size="small" :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
  <el-empty v-else description="任务不存在" />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { useDetectionStore } from '@/stores/detection'
import type { DetectionRun, TaskEvent } from '@/types'
import { DEPLOYMENT_STATE_LABELS } from '@/utils/routeDeployment'

const router = useRouter()
const routeParam = useRoute()
const taskStore = useTaskStore()
const detectionStore = useDetectionStore()
const eventPage = ref(0)
const routeStore = useRouteStore()
const robotStore = useRobotStore()
const alarmStore = useAlarmStore()
const siteStore = useSiteStore()
const { can } = usePermission()

const taskId = computed(() => routeParam.params.id as string)
const task = computed(() => taskStore.getTaskById(taskId.value))
const route = computed(() => (task.value ? routeStore.getRouteById(task.value.routeId) : undefined))
const robot = computed(() => (task.value ? robotStore.getRobotById(task.value.robotId) : undefined))
const events = computed(() => taskStore.getEventsByTask(taskId.value))
const taskRuns = computed<DetectionRun[]>(() => detectionStore.runs.filter((run) => run.taskId === taskId.value))

function loadEventPage(page: number) {
  eventPage.value = page
  void taskStore.refreshEvents(taskId.value, { page, size: 20 })
}
const taskAlarms = computed(() => alarmStore.alarms.filter((a) => a.taskId === taskId.value))
const execution = computed(() => taskStore.executionFor(taskId.value))
const eligibility = computed(() => taskStore.eligibilityFor(taskId.value))
const controlBusy = computed(() => !!taskStore.controlInFlight[taskId.value])
const executionStatus = computed(() => execution.value?.status)
const startable = computed(() => !!execution.value && !!task.value
  && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(task.value)))
const canShowLocalStart = computed(() => startable.value
  && (eligibility.value?.supportsLocalConfirmStart ?? robot.value?.supportsLocalConfirmStart ?? false))
const canShowRemoteStart = computed(() => startable.value
  && (eligibility.value?.supportsRemoteImmediateStart ?? robot.value?.supportsRemoteImmediateStart ?? true))
const canPause = computed(() => !!execution.value && can('task:control') && executionStatus.value === 'RUNNING')
const canResume = computed(() => !!execution.value && can('task:control') && executionStatus.value === 'PAUSED')
const canTakeover = computed(() => !!execution.value && can('task:takeover') && executionStatus.value === 'RUNNING')
const canCancelTask = computed(() => {
  if (!task.value || !can('task:control')) return false
  const status = taskStore.statusOf(task.value)
  if (task.value.executionId) return ['STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSED'].includes(status)
  return !['COMPLETED', 'CANCELLED', 'ESTOPPED'].includes(task.value.status)
})
const canEstopTask = computed(() => {
  if (!task.value || !can('task:estop')) return false
  const status = taskStore.statusOf(task.value)
  if (['COMPLETED', 'CANCELLED', 'ESTOPPED', 'ESTOPPING', 'CANCELLING'].includes(status)) return false
  if (task.value.executionId) return ['STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSED'].includes(status)
  return true
})
const commandFeedback = computed(() => {
  const control = execution.value?.latestControl
  const status = control?.status
  const title = ({
    PENDING_SEND: '命令发送中', SENDING: '命令发送中', QUEUED: '命令已受理，等待机器人 ACK',
    ACKED: '已被机器人 ACK，等待真实事件确认', RECONCILING: '对账中，尚未假定控制成功或失败',
    CONFIRMED: '已由真实事件确认', FAILED: '控制命令失败',
  } as Record<string, string>)[status ?? ''] ?? '控制命令状态未知'
  return { title, description: control?.resultMessage || '请等待轮询更新', type: status === 'FAILED' ? 'error' : status === 'CONFIRMED' ? 'success' : 'warning' as 'error' | 'success' | 'warning' }
})

const fallbackCenter = computed(() =>
  route.value ? siteStore.getSiteById(route.value.siteId)?.center ?? siteStore.sites[0]?.center : siteStore.sites[0]?.center,
)
const mapCenter = computed(() => route.value?.path[0] ?? fallbackCenter.value ?? { lat: 30.27, lng: 120.15 })
const robotPos = computed(() => robot.value?.position ?? null)

const breadcrumbs = [
  { label: '巡检业务' },
  { label: '任务调度', to: '/tasks' },
  { label: '任务详情' },
]

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function eventLabel(type: TaskEvent['type']) {
  return {
    DISPATCH: '下发', ARRIVE: '到点', INSPECT: '采集', DETECT: '检测',
    ALARM: '告警', PAUSE: '暂停', RESUME: '执行', COMPLETE: '完成', VOICE: '语音',
  }[type]
}

const eligibilityReason = computed(() => {
  if (!execution.value) return '任务未绑定不可变路线修订执行快照'
  if (!eligibility.value) return '正在核验启动条件'
  return eligibility.value.eligible ? '' : eligibility.value.ineligibleReason || '启动条件未满足'
})

const waitingDuration = computed(() => {
  if (!execution.value?.robotReadyAt) return '0 秒'
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(execution.value.robotReadyAt).getTime()) / 1000))
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  return minutes < 60 ? `${minutes} 分钟` : `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
})

function startDisabledReason(mode: 'REMOTE_IMMEDIATE' | 'LOCAL_CONFIRM') {
  if (eligibilityReason.value) return eligibilityReason.value
  if (robot.value?.status === 'OFFLINE') return '机器人离线'
  if (robot.value?.status === 'BUSY') return '机器人正忙'
  if (mode === 'LOCAL_CONFIRM' && !canShowLocalStart.value) return '机器人不支持本地确认启动'
  if (mode === 'REMOTE_IMMEDIATE' && !canShowRemoteStart.value) return '机器人不支持远程立即启动'
  return ''
}

function shortHash(value: string) {
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value
}

async function startInspection(startMode: 'REMOTE_IMMEDIATE' | 'LOCAL_CONFIRM') {
  if (!task.value) return
  try {
    await taskStore.startInspection(task.value.id, startMode)
    ElMessage.success(startMode === 'LOCAL_CONFIRM'
      ? '任务已下发，等待机器人准备完成并在触摸屏确认'
      : '远程启动命令已受理，等待机器人 route_started 事件确认运行')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启动巡检失败')
  }
}

function confirmRemoteStart() {
  ElMessageBox.confirm(
    '机器人将在任务校验成功后立即开始移动。请确认现场无人、通道无障碍且急停状态正常。',
    '确认远程立即启动',
    { type: 'warning', confirmButtonText: '确认启动', cancelButtonText: '取消' },
  ).then(() => startInspection('REMOTE_IMMEDIATE')).catch(() => {})
}

function timelineType(type: TaskEvent['type']) {
  if (type === 'ALARM') return 'danger'
  if (type === 'COMPLETE') return 'success'
  return 'primary'
}

async function sendControl(action: 'PAUSE' | 'RESUME') {
  if (!task.value) return
  try {
    await taskStore.controlInspection(task.value.id, action)
    ElMessage.info('控制请求已受理，等待机器人 ACK 与真实事件确认')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '控制请求发送失败')
  }
}

function requestTakeover() {
  if (!task.value) return
  ElMessageBox.prompt('请说明人工接管原因', '确认人工接管', {
    confirmButtonText: '确认接管', cancelButtonText: '取消', inputPattern: /\S+/, inputErrorMessage: '必须填写接管原因', type: 'warning',
  }).then(async ({ value }) => {
    try {
      await taskStore.controlInspection(task.value!.id, 'TAKEOVER', value)
      ElMessage.info('人工接管请求已受理，等待机器人真实事件确认')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '人工接管请求发送失败')
    }
  }).catch(() => {})
}

function cancelTask() {
  if (!task.value) return
  ElMessageBox.confirm('确定取消该任务？这是业务取消，不是设备急停。', '确认取消', { type: 'warning' })
    .then(async () => {
      try {
        await taskStore.controlInspection(task.value!.id, 'CANCEL')
        ElMessage.info('取消请求已受理，等待机器人真实事件确认')
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '取消请求发送失败')
      }
    })
    .catch(() => {})
}

function emergencyStopTask() {
  if (!task.value) return
  ElMessageBox.prompt('请填写远程急停原因（必填）', '远程急停确认', {
    type: 'error',
    confirmButtonText: '确认急停',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '必须填写急停原因',
    inputPlaceholder: '例如：现场出现人员闯入，立即停机',
  }).then(async ({ value }) => {
    try {
      await taskStore.emergencyStop(task.value!.id, value.trim())
      ElMessage.warning('远程急停已受理，等待机器人 ACK 与确认事件')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '远程急停失败')
    }
  }).catch(() => {})
}

function openAgent() {
  router.push({ path: '/agents', query: { taskId: taskId.value } })
}
</script>

<style scoped>
.muted {
  font-size: 12px;
  color: #909399;
}

.ev-img {
  margin-top: 8px;
  max-width: 200px;
  border-radius: 6px;
}

.detection-run-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detection-run-item {
  padding: 10px;
  border: 1px solid #d7dde8;
  background: #f8fafc;
}

.run-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.run-meta span {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}

.run-images {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.run-images img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: contain;
  background: #0f172a;
}
</style>
