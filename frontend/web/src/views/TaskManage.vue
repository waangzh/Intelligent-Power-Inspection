<template>
  <div>
    <PageHeader title="任务调度" :description="can('task:create') ? '创建、下发与控制巡检任务' : '查看巡检任务进度（管理员仅可应急急停）'" :breadcrumbs="[{ label: '巡检业务' }, { label: '任务调度' }]">
      <template #actions>
        <el-button v-if="can('task:create')" type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          创建任务
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" style="margin-bottom: 16px" v-if="activeTask">
      <template #header>
        <div class="card-head">
          <span>执行中 · {{ activeTask.name }}</span>
          <TaskStatusTag :status="taskStore.statusOf(activeTask)" />
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12">
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
        <el-col :span="12">
          <div style="height: 220px">
            <Map2D
              :center="mapCenter"
              :fallback-center="fallbackCenter"
              :route="activeRoute"
              :robot-position="robotPos"
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
        style="margin-top: 14px"
      />
      <div class="action-bar">
        <el-button
          v-if="can('task:dispatch') && taskStore.executionFor(activeTask.id) && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(activeTask))"
          type="primary"
          :disabled="!taskStore.eligibilityFor(activeTask.id)?.eligible"
          :title="startDisabledReason(activeTask.id)"
          @click="startInspection(activeTask.id)"
        >启动巡检</el-button>
        <el-button v-if="can('task:dispatch') && !activeTask.executionId && activeTask.status === 'CREATED'" type="primary" @click="taskStore.dispatch(activeTask.id)">下发任务</el-button>
        <el-button v-if="can('task:control') && !activeTask.executionId && activeTask.status === 'RUNNING'" @click="taskStore.pause(activeTask.id)">暂停</el-button>
        <el-button v-if="can('task:control') && !activeTask.executionId && activeTask.status === 'PAUSED'" type="primary" @click="taskStore.resume(activeTask.id)">恢复</el-button>
        <el-button v-if="can('task:control') && !activeTask.executionId && activeTask.status === 'RUNNING'" type="warning" @click="taskStore.takeover(activeTask.id)">人工接管</el-button>
        <el-button v-if="can('task:control') && activeTask.executionId && taskStore.statusOf(activeTask) === 'RUNNING'" @click="controlTask(activeTask.id, 'PAUSE')">暂停</el-button>
        <el-button v-if="can('task:control') && activeTask.executionId && taskStore.statusOf(activeTask) === 'PAUSED'" type="primary" @click="controlTask(activeTask.id, 'RESUME')">恢复</el-button>
        <el-button v-if="can('task:takeover') && activeTask.executionId && taskStore.statusOf(activeTask) === 'RUNNING'" type="warning" @click="controlTask(activeTask.id, 'TAKEOVER')">人工接管</el-button>
        <el-button
          v-if="canCancelTask(activeTask)"
          type="danger"
          @click="cancelTask(activeTask.id)"
        >{{ can('task:estop') && !can('task:control') ? '远程急停' : '取消任务' }}</el-button>
        <el-button v-if="can('task:dispatch')" type="success" @click="openAgentForTask(activeTask.id)">Agent 分析</el-button>
        <el-button @click="router.push(`/tasks/${activeTask.id}`)">任务详情</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>任务列表</template>
      <el-table :data="taskStore.tasks" size="small">
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
            <TaskStatusTag :status="taskStore.statusOf(row)" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-button
              v-if="can('task:dispatch') && taskStore.executionFor(row.id) && ['CREATED', 'START_FAILED'].includes(taskStore.statusOf(row))"
              text
              type="primary"
              size="small"
              :disabled="!taskStore.eligibilityFor(row.id)?.eligible"
              :title="startDisabledReason(row.id)"
              @click="startInspection(row.id)"
            >启动巡检</el-button>
            <el-progress :percentage="taskStore.executionFor(row.id)?.progress ?? row.progress" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('task:dispatch') && !row.executionId && row.status === 'CREATED'" text type="primary" size="small" @click="taskStore.dispatch(row.id)">下发</el-button>
            <el-button v-if="can('task:control') && !row.executionId && row.status === 'RUNNING'" text size="small" @click="taskStore.pause(row.id)">暂停</el-button>
            <el-button v-if="can('task:control') && !row.executionId && row.status === 'PAUSED'" text type="primary" size="small" @click="taskStore.resume(row.id)">恢复</el-button>
            <el-button v-if="can('task:control') && !row.executionId && row.status === 'RUNNING'" text type="warning" size="small" @click="taskStore.takeover(row.id)">接管</el-button>
            <el-button v-if="can('task:control') && row.executionId && taskStore.statusOf(row) === 'RUNNING'" text size="small" @click="controlTask(row.id, 'PAUSE')">暂停</el-button>
            <el-button v-if="can('task:control') && row.executionId && taskStore.statusOf(row) === 'PAUSED'" text type="primary" size="small" @click="controlTask(row.id, 'RESUME')">恢复</el-button>
            <el-button v-if="can('task:takeover') && row.executionId && taskStore.statusOf(row) === 'RUNNING'" text type="warning" size="small" @click="controlTask(row.id, 'TAKEOVER')">接管</el-button>
            <el-button v-if="can('task:dispatch')" text type="success" size="small" @click="openAgentForTask(row.id)">Agent</el-button>
            <el-button text size="small" @click="router.push(`/tasks/${row.id}`)">详情</el-button>
            <el-button
              v-if="canCancelTask(row)"
              text
              type="danger"
              size="small"
              @click="cancelTask(row.id)"
            >{{ can('task:estop') && !can('task:control') ? '急停' : '取消' }}</el-button>
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
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourcesApi } from '@/api/resources'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { usePermission } from '@/composables/usePermission'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { latestReadyRevision, useTaskStore } from '@/stores/task'
import type { InspectionTask, RouteRevision } from '@/types'
import { DEPLOYMENT_STATE_LABELS } from '@/utils/routeDeployment'

const readyDeploymentLabel = DEPLOYMENT_STATE_LABELS.READY_FOR_ROBOT

const router = useRouter()
const { can, canAny } = usePermission()
const taskPage = ref(0)

function loadTaskPage(page: number) {
  taskPage.value = page
  void taskStore.loadDynamic({ page, size: 20 })
}
const taskStore = useTaskStore()
const routeStore = useRouteStore()
const robotStore = useRobotStore()
const siteStore = useSiteStore()

const dialogVisible = ref(false)
const form = reactive({ name: '', routeId: '', robotId: 'robot_001' })
const readyRevision = ref<RouteRevision>()
const revisionLoading = ref(false)

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
const robotPos = computed(() => {
  if (!activeTask.value) return null
  return robotStore.getRobotById(activeTask.value.robotId)?.position ?? null
})

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
  if (!canAny('task:control', 'task:estop')) return false
  if (task.executionId) return can('task:control') && ['RUNNING', 'PAUSED'].includes(taskStore.statusOf(task))
  return !['COMPLETED', 'CANCELLED'].includes(task.status)
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
  if (!eligibility) return '正在核验启动条件'
  return eligibility.eligible ? '' : eligibility.ineligibleReason || '启动条件未满足'
}

async function startInspection(taskId: string) {
  try {
    await taskStore.startInspection(taskId)
    ElMessage.success('启动命令已受理，等待机器人 route_started 事件确认运行')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启动巡检失败')
  }
}

function cancelTask(id: string) {
  const emergency = can('task:estop') && !can('task:control')
  ElMessageBox.confirm(emergency ? '确定远程急停该任务？' : '确定取消该任务？', '确认', { type: 'warning' })
    .then(async () => {
      const task = taskStore.getTaskById(id)
      if (task?.executionId) {
        await taskStore.controlInspection(id, 'CANCEL')
        ElMessage.info('取消命令已接受，等待机器人确认')
      } else {
        taskStore.cancel(id)
        ElMessage.success(emergency ? '已发送远程急停' : '任务已取消')
      }
    })
    .catch(() => {})
}

function openAgentForTask(taskId: string) {
  router.push({ path: '/agents', query: { taskId } })
}
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.voice-hint {
  margin-top: 12px;
  padding: 10px 12px;
  background: #ecf5ff;
  border-radius: 6px;
  font-size: 13px;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-bar {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}
</style>
