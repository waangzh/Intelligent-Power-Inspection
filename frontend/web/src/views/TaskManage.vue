<template>
  <div class="task-page">
    <PageHeader
      title="任务调度"
      :description="can('task:create') ? '创建、下发与控制巡检任务' : '查看巡检任务进度（管理员仅可应急急停）'"
      :breadcrumbs="[{ label: '巡检业务' }, { label: '任务调度' }]"
    >
      <template #actions>
        <el-button v-if="can('task:create')" type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          创建任务
        </el-button>
      </template>
    </PageHeader>

    <div class="stats-grid">
      <el-card
        v-for="(stat, index) in overviewStats"
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

    <el-card v-if="activeTask" shadow="never" class="active-task-card">
      <template #header>
        <div class="card-head">
          <div class="active-task-title">
            <el-icon class="pulse-icon"><VideoPlay /></el-icon>
            <span>执行中 · {{ activeTask.name }}</span>
          </div>
          <TaskStatusTag
            :status="taskStore.statusOf(activeTask)"
            :manual-reconciliation-required="taskStore.executionFor(activeTask.id)?.manualReconciliationRequired"
          />
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="路线">{{ routeName(activeTask.routeId) }}</el-descriptions-item>
            <el-descriptions-item label="机器人">{{ robotName(activeTask.robotId) }}</el-descriptions-item>
            <el-descriptions-item label="进度">
              <el-progress :percentage="taskStore.executionFor(activeTask.id)?.progress ?? activeTask.progress" style="width: 160px" />
            </el-descriptions-item>
            <el-descriptions-item label="当前检查点">第 {{ activeTask.currentCheckpointSeq }} 个</el-descriptions-item>
            <el-descriptions-item v-if="taskStore.executionFor(activeTask.id)" label="路线修订">{{ taskStore.executionFor(activeTask.id)?.routeRevisionId }}</el-descriptions-item>
            <el-descriptions-item v-if="taskStore.executionFor(activeTask.id)" label="执行 ID">{{ taskStore.executionFor(activeTask.id)?.executionId }}</el-descriptions-item>
            <el-descriptions-item v-if="taskStore.executionFor(activeTask.id)" label="部署 ID">{{ taskStore.executionFor(activeTask.id)?.deploymentId ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="voice-hint" v-if="activeTask.status === 'RUNNING'">
            <el-icon><Microphone /></el-icon>
            机器人语音：行进中持续检测人员 / 安全帽 / 障碍物 / 火源；到达检查点后播报「已到达指定位置，开始检查」
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="active-map">
            <Map2D
              :center="mapCenter"
              :fallback-center="fallbackCenter"
              :route="activeRoute"
              :robot-location="activeRobotLocation"
              :robot-label="activeRobotName"
            />
          </div>
        </el-col>
      </el-row>
      <el-alert
        v-if="taskStore.executionFor(activeTask.id) && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(activeTask))"
        :title="startDisabledReason(activeTask.id) || '路线修订、部署、机器人在线状态与哈希校验均满足后，才可启动巡检'"
        :type="taskStore.eligibilityFor(activeTask.id)?.eligible ? 'success' : 'warning'"
        :closable="false"
        show-icon
        class="start-alert"
      />
      <div class="action-bar">
        <el-button
          v-if="taskStore.executionFor(activeTask.id) && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(activeTask))"
          type="primary"
          :disabled="!!startDisabledReason(activeTask.id)"
          :title="startDisabledReason(activeTask.id)"
          @click="openStartOptions(activeTask.id)"
        >选择启动方式</el-button>
        <el-button v-if="can('task:dispatch') && !activeTask.executionId && activeTask.status === 'CREATED'" type="primary" @click="taskStore.dispatch(activeTask.id)">下发任务</el-button>
        <el-button v-if="can('task:control') && !activeTask.executionId && activeTask.status === 'RUNNING'" @click="taskStore.pause(activeTask.id)">暂停</el-button>
        <el-button v-if="can('task:control') && !activeTask.executionId && activeTask.status === 'PAUSED'" type="primary" @click="taskStore.resume(activeTask.id)">恢复</el-button>
        <el-button v-if="can('task:control') && !activeTask.executionId && activeTask.status === 'RUNNING'" type="warning" plain @click="taskStore.takeover(activeTask.id)">人工接管</el-button>
        <el-button v-if="can('task:control') && activeTask.executionId && taskStore.statusOf(activeTask) === 'RUNNING'" @click="controlTask(activeTask.id, 'PAUSE')">暂停</el-button>
        <el-button v-if="can('task:control') && activeTask.executionId && taskStore.statusOf(activeTask) === 'PAUSED'" type="primary" @click="controlTask(activeTask.id, 'RESUME')">恢复</el-button>
        <el-button v-if="can('task:takeover') && activeTask.executionId && taskStore.statusOf(activeTask) === 'RUNNING'" type="warning" plain @click="controlTask(activeTask.id, 'TAKEOVER')">人工接管</el-button>
        <el-button v-if="canCancelTask(activeTask)" type="danger" plain @click="cancelTask(activeTask.id)">取消任务</el-button>
        <el-button v-if="canEstopTask(activeTask)" type="danger" @click="emergencyStopTask(activeTask.id)">远程急停</el-button>
        <el-button v-if="can('task:dispatch')" type="success" plain @click="openAgentForTask(activeTask.id)">Agent 分析</el-button>
        <el-button plain @click="router.push(`/tasks/${activeTask.id}`)">任务详情</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索任务名称" clearable class="filter-search" @input="taskPage = 0">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="filter-item">
          <span class="filter-label">状态</span>
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 130px" @change="onStatusFilterChange">
            <el-option label="执行中" value="RUNNING" />
            <el-option label="待启动" value="PENDING" />
            <el-option label="已完成" value="DONE" />
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
          <span class="table-title">任务列表</span>
          <span class="record-count">共 {{ filteredTasks.length }} 条记录</span>
        </div>
      </template>
      <el-table :data="filteredTasks" size="small">
        <el-table-column prop="name" label="任务名称" min-width="140">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/tasks/${row.id}`)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="路线" min-width="120">
          <template #default="{ row }">{{ routeName(row.routeId) }}</template>
        </el-table-column>
        <el-table-column label="机器人" width="130">
          <template #default="{ row }">{{ robotName(row.robotId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <TaskStatusTag
              :status="taskStore.statusOf(row)"
              :manual-reconciliation-required="taskStore.executionFor(row.id)?.manualReconciliationRequired"
            />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-button
                v-if="taskStore.executionFor(row.id) && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(row))"
                plain
                size="small"
                class="action-btn action-detail"
                :disabled="!!startDisabledReason(row.id)"
                :title="startDisabledReason(row.id)"
                @click="openStartOptions(row.id)"
              >启动</el-button>
              <el-progress :percentage="taskStore.executionFor(row.id)?.progress ?? row.progress" :stroke-width="8" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right" class-name="actions-col">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-if="can('task:dispatch') && !row.executionId && row.status === 'CREATED'" plain size="small" class="action-btn action-claim" @click="taskStore.dispatch(row.id)">下发</el-button>
              <el-button v-if="can('task:control') && !row.executionId && row.status === 'RUNNING'" plain size="small" class="action-btn action-detail" @click="taskStore.pause(row.id)">暂停</el-button>
              <el-button v-if="can('task:control') && !row.executionId && row.status === 'PAUSED'" plain size="small" class="action-btn action-detail" @click="taskStore.resume(row.id)">恢复</el-button>
              <el-button v-if="can('task:control') && !row.executionId && row.status === 'RUNNING'" plain size="small" class="action-btn action-claim" @click="taskStore.takeover(row.id)">接管</el-button>
              <el-button v-if="can('task:control') && row.executionId && taskStore.statusOf(row) === 'RUNNING'" plain size="small" class="action-btn action-detail" @click="controlTask(row.id, 'PAUSE')">暂停</el-button>
              <el-button v-if="can('task:control') && row.executionId && taskStore.statusOf(row) === 'PAUSED'" plain size="small" class="action-btn action-detail" @click="controlTask(row.id, 'RESUME')">恢复</el-button>
              <el-button v-if="can('task:takeover') && row.executionId && taskStore.statusOf(row) === 'RUNNING'" plain size="small" class="action-btn action-claim" @click="controlTask(row.id, 'TAKEOVER')">接管</el-button>
              <el-button v-if="can('task:dispatch')" plain size="small" class="action-btn action-submit" @click="openAgentForTask(row.id)">Agent</el-button>
              <el-button plain size="small" class="action-btn action-detail" @click="router.push(`/tasks/${row.id}`)">详情</el-button>
              <el-button v-if="canCancelTask(row)" plain size="small" class="action-btn action-danger" @click="cancelTask(row.id)">取消</el-button>
              <el-button v-if="canEstopTask(row)" plain size="small" class="action-btn action-danger" @click="emergencyStopTask(row.id)">急停</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <ListPagination :total="taskStore.total" :page="taskPage" @change="loadTaskPage" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="创建巡检任务" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.name" placeholder="例如：主变区例行巡检" />
        </el-form-item>
        <el-form-item label="巡检路线" required>
          <el-select v-model="form.routeId" style="width: 100%" placeholder="选择路线" @change="loadReadyRevision">
            <el-option
              v-for="r in routeStore.routes"
              :key="r.id"
              :label="`${siteName(r.siteId)} / ${r.name}`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="可部署版本" required>
          <el-input :model-value="readyRevision ? `第 ${readyRevision.revisionNo} 版（${readyRevision.id}）` : ''" :placeholder="`暂无${readyDeploymentLabel}版本`" disabled />
        </el-form-item>
        <el-alert v-if="!revisionLoading && form.routeId && !readyRevision" :title="`该路线没有已发布且${readyDeploymentLabel}的版本，请先发布路线并同步部署`" type="warning" :closable="false" show-icon />
        <el-form-item label="执行机器人">
          <el-input :model-value="defaultRobotLabel" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="revisionLoading" :disabled="!readyRevision" @click="submitTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  CircleCheck,
  Clock,
  List,
  Microphone,
  Plus,
  RefreshRight,
  Search,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourcesApi } from '@/api/resources'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { usePermission } from '@/composables/usePermission'
import { useRobotStore } from '@/stores/robot'
import { useRobotLocationStore } from '@/stores/robotLocation'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { latestReadyRevision, useTaskStore } from '@/stores/task'
import type { InspectionTask, RouteRevision, TaskStatus } from '@/types'
import { DEPLOYMENT_STATE_LABELS } from '@/utils/routeDeployment'

const readyDeploymentLabel = DEPLOYMENT_STATE_LABELS.READY_FOR_ROBOT

const RUNNING_STATUSES: TaskStatus[] = ['STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING', 'MANUAL_TAKEOVER', 'TAKEOVER_PENDING']
const PENDING_STATUSES: TaskStatus[] = ['CREATED', 'DISPATCHED', 'START_FAILED']
const DONE_STATUSES: TaskStatus[] = ['COMPLETED', 'CANCELLED', 'ESTOPPED', 'FAILED']

const router = useRouter()
const { can, canAny } = usePermission()
const taskPage = ref(0)
const keyword = ref('')
const statusFilter = ref('')
const activeStatKey = ref('ALL')
const statIcons = [List, VideoPlay, Clock, CircleCheck]

const taskStore = useTaskStore()
const routeStore = useRouteStore()
const robotStore = useRobotStore()
const locationStore = useRobotLocationStore()
const siteStore = useSiteStore()

const dialogVisible = ref(false)
const form = reactive({ name: '', routeId: '', robotId: 'robot_001' })
const readyRevision = ref<RouteRevision>()
const revisionLoading = ref(false)

function loadTaskPage(page: number) {
  taskPage.value = page
  void taskStore.loadDynamic({ page, size: 20 })
}

const activeTask = computed(() => taskStore.getActiveTask())
const activeRoute = computed(() =>
  activeTask.value ? routeStore.getRouteById(activeTask.value.routeId) ?? null : null,
)
const mapCenter = computed(() => {
  const route = activeRoute.value
  if (route?.path[0]) return route.path[0]
  return fallbackCenter.value ?? { lat: 30.27, lng: 120.15 }
})
const fallbackCenter = computed(() => {
  const route = activeRoute.value
  return route ? siteStore.getSiteById(route.siteId)?.center ?? siteStore.sites[0]?.center : siteStore.sites[0]?.center
})
const activeRobotLocation = computed(() => {
  const robotId = activeTask.value?.robotId
  return robotId ? locationStore.getLocation(robotId) ?? null : null
})
const activeRobotName = computed(() => {
  const robotId = activeTask.value?.robotId
  return robotId ? robotStore.getRobotById(robotId)?.name ?? robotId : ''
})

watch(
  () => activeTask.value?.robotId,
  (robotId) => {
    locationStore.stopPolling()
    if (robotId) locationStore.startRobotPolling(robotId)
  },
  { immediate: true },
)

onUnmounted(() => {
  locationStore.stopPolling()
})

const overviewStats = computed(() => {
  const tasks = taskStore.tasks
  const statusOf = (task: InspectionTask) => taskStore.statusOf(task)
  return [
    {
      key: 'ALL',
      label: '全部任务',
      value: taskStore.total,
      footer: '服务端总数',
    },
    {
      key: 'RUNNING',
      label: '执行中',
      value: tasks.filter((t) => RUNNING_STATUSES.includes(statusOf(t))).length,
      footer: '本页统计',
    },
    {
      key: 'PENDING',
      label: '待启动',
      value: tasks.filter((t) => PENDING_STATUSES.includes(statusOf(t))).length,
      footer: '本页统计',
    },
    {
      key: 'DONE',
      label: '已结束',
      value: tasks.filter((t) => DONE_STATUSES.includes(statusOf(t))).length,
      footer: '本页统计',
    },
  ]
})

const filteredTasks = computed(() => {
  let list = taskStore.tasks
  if (keyword.value.trim()) {
    const q = keyword.value.trim()
    list = list.filter((t) => t.name.includes(q))
  }
  if (statusFilter.value === 'RUNNING') {
    list = list.filter((t) => RUNNING_STATUSES.includes(taskStore.statusOf(t)))
  } else if (statusFilter.value === 'PENDING') {
    list = list.filter((t) => PENDING_STATUSES.includes(taskStore.statusOf(t)))
  } else if (statusFilter.value === 'DONE') {
    list = list.filter((t) => DONE_STATUSES.includes(taskStore.statusOf(t)))
  }
  return list
})

function selectStatCard(key: string) {
  if (activeStatKey.value === key) {
    activeStatKey.value = 'ALL'
    statusFilter.value = ''
  } else {
    activeStatKey.value = key
    statusFilter.value = key === 'ALL' ? '' : key
  }
}

function onStatusFilterChange() {
  activeStatKey.value = statusFilter.value || 'ALL'
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = ''
  activeStatKey.value = 'ALL'
}

const defaultRobotLabel = computed(() => {
  const robot = robotStore.robots[0]
  return robot ? `${robot.name}（${robot.status}）` : 'robot_001'
})

function routeName(id: string) {
  return routeStore.getRouteById(id)?.name ?? '-'
}

function robotName(id: string) {
  return robotStore.getRobotById(id)?.name ?? '-'
}

function siteName(siteId: string) {
  return siteStore.getSiteById(siteId)?.name ?? '-'
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

async function openCreateDialog() {
  form.name = `巡检任务 ${new Date().toLocaleDateString('zh-CN')}`
  form.routeId = routeStore.routes[0]?.id ?? ''
  form.robotId = robotStore.robots[0]?.id ?? 'robot_001'
  dialogVisible.value = true
  await loadReadyRevision()
}

async function loadReadyRevision() {
  readyRevision.value = undefined
  if (!form.routeId || !form.robotId) return
  revisionLoading.value = true
  try {
    const revisions = await resourcesApi.listRouteRevisions(form.routeId)
    const deployments = (await Promise.all(revisions.map((revision) =>
      resourcesApi.listRouteDeployments(revision.id).catch(() => []),
    ))).flat()
    readyRevision.value = latestReadyRevision(revisions, deployments, form.robotId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '可部署路线版本加载失败')
  } finally {
    revisionLoading.value = false
  }
}

async function submitTask() {
  if (!form.name || !form.routeId || !form.robotId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (!readyRevision.value) {
    ElMessage.warning(`请先发布路线并同步到${readyDeploymentLabel}`)
    return
  }
  try {
    const task = await taskStore.createTask(form.name, form.routeId, form.robotId, readyRevision.value.id)
    dialogVisible.value = false
    ElMessage.success(`任务已创建，执行 ID：${task.executionId}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务创建失败')
  }
}

function canCancelTask(task: InspectionTask) {
  if (!can('task:control')) return false
  const status = taskStore.statusOf(task)
  if (task.executionId) return ['STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSED'].includes(status)
  return !['COMPLETED', 'CANCELLED', 'ESTOPPED'].includes(task.status)
}

function canEstopTask(task: InspectionTask) {
  if (!can('task:estop')) return false
  const status = taskStore.statusOf(task)
  if (['COMPLETED', 'CANCELLED', 'ESTOPPED', 'ESTOPPING', 'CANCELLING'].includes(status)) return false
  if (task.executionId) return ['STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSED'].includes(status)
  return ['CREATED', 'DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER', 'STARTING'].includes(task.status)
}

async function controlTask(id: string, action: 'PAUSE' | 'RESUME' | 'TAKEOVER') {
  try {
    await taskStore.controlInspection(id, action, action === 'TAKEOVER' ? '平台任务页人工接管' : undefined)
    ElMessage.info('命令已接受，等待机器人 ACK 与真实事件确认')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '控制请求失败')
  }
}

function startDisabledReason(taskId: string) {
  const eligibility = taskStore.eligibilityFor(taskId)
  if (!canAny('task:start-local', 'task:start-remote')) {
    return '当前账号没有任务启动权限；如权限刚更新，请重新登录'
  }
  if (!eligibility) return '正在核验启动条件'
  if (!eligibility.supportsRemoteImmediateStart && !eligibility.supportsLocalConfirmStart) {
    return '机器人不支持远程立即启动或本地确认启动'
  }
  return eligibility.eligible ? '' : eligibility.ineligibleReason || '启动条件未满足'
}

function openStartOptions(taskId: string) {
  router.push(`/tasks/${taskId}`)
}

function cancelTask(id: string) {
  ElMessageBox.confirm('确定取消该任务？这是业务取消，不是设备急停。', '确认取消', { type: 'warning' })
    .then(async () => {
      const task = taskStore.getTaskById(id)
      if (task?.executionId) {
        await taskStore.controlInspection(id, 'CANCEL')
        ElMessage.info('取消命令已接受，等待机器人确认')
      } else {
        taskStore.cancel(id)
        ElMessage.success('任务已取消')
      }
    })
    .catch(() => {})
}

function emergencyStopTask(id: string) {
  ElMessageBox.prompt('请填写远程急停原因（必填）', '远程急停确认', {
    type: 'error',
    confirmButtonText: '确认急停',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '必须填写急停原因',
    inputPlaceholder: '例如：现场出现人员闯入，立即停机',
  }).then(async ({ value }) => {
    try {
      await taskStore.emergencyStop(id, value.trim())
      ElMessage.warning('远程急停已受理，等待机器人 ACK 与确认事件')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '远程急停失败')
    }
  }).catch(() => {})
}

function openAgentForTask(taskId: string) {
  router.push({ path: '/agents', query: { taskId } })
}
</script>

<style scoped>
.active-task-card {
  margin-bottom: 16px;
  border-color: #91caff;
  box-shadow: 0 0 0 1px rgba(23, 104, 242, 0.08), var(--pi-card-shadow);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.active-task-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--pi-text);
}

.pulse-icon {
  color: var(--pi-primary);
  font-size: 18px;
}

.active-map {
  height: 220px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--pi-border-soft);
}

.voice-hint {
  margin-top: 12px;
  padding: 10px 12px;
  background: #ecf5ff;
  border-radius: 8px;
  font-size: 13px;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.start-alert {
  margin-top: 14px;
}

.action-bar {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-cell :deep(.el-progress) {
  flex: 1;
  min-width: 60px;
}

@media (max-width: 768px) {
  .active-map {
    margin-top: 12px;
    height: 200px;
  }
}
</style>
