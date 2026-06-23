<template>
  <div>
    <PageHeader title="任务调度" description="创建、下发与控制巡检任务" :breadcrumbs="[{ label: '巡检业务' }, { label: '任务调度' }]">
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
          <TaskStatusTag :status="activeTask.status" />
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="路线">{{ routeName(activeTask.routeId) }}</el-descriptions-item>
            <el-descriptions-item label="机器人">{{ robotName(activeTask.robotId) }}</el-descriptions-item>
            <el-descriptions-item label="进度">
              <el-progress :percentage="activeTask.progress" style="width: 160px" />
            </el-descriptions-item>
            <el-descriptions-item label="当前检查点">第 {{ activeTask.currentCheckpointSeq }} 个</el-descriptions-item>
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
              :route="activeRoute"
              :robot-position="robotPos"
            />
          </div>
        </el-col>
      </el-row>
      <div class="action-bar">
        <el-button v-if="can('task:dispatch') && activeTask.status === 'CREATED'" type="primary" @click="taskStore.dispatch(activeTask.id)">下发任务</el-button>
        <el-button v-if="can('task:control') && activeTask.status === 'RUNNING'" @click="taskStore.pause(activeTask.id)">暂停</el-button>
        <el-button v-if="can('task:control') && activeTask.status === 'PAUSED'" type="primary" @click="taskStore.resume(activeTask.id)">恢复</el-button>
        <el-button v-if="can('task:control') && activeTask.status === 'RUNNING'" type="warning" @click="taskStore.takeover(activeTask.id)">人工接管</el-button>
        <el-button
          v-if="can('task:control') && ['RUNNING', 'PAUSED', 'MANUAL_TAKEOVER', 'DISPATCHED'].includes(activeTask.status)"
          type="danger"
          @click="cancelTask(activeTask.id)"
        >取消任务</el-button>
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
            <TaskStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('task:dispatch') && row.status === 'CREATED'" text type="primary" size="small" @click="taskStore.dispatch(row.id)">下发</el-button>
            <el-button v-if="can('task:control') && row.status === 'RUNNING'" text size="small" @click="taskStore.pause(row.id)">暂停</el-button>
            <el-button v-if="can('task:control') && row.status === 'PAUSED'" text type="primary" size="small" @click="taskStore.resume(row.id)">恢复</el-button>
            <el-button v-if="can('task:control') && row.status === 'RUNNING'" text type="warning" size="small" @click="taskStore.takeover(row.id)">接管</el-button>
            <el-button text size="small" @click="router.push(`/tasks/${row.id}`)">详情</el-button>
            <el-button
              v-if="can('task:control') && !['COMPLETED', 'CANCELLED'].includes(row.status)"
              text
              type="danger"
              size="small"
              @click="cancelTask(row.id)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="创建巡检任务" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.name" placeholder="例如：主变区例行巡检" />
        </el-form-item>
        <el-form-item label="巡检路线" required>
          <el-select v-model="form.routeId" style="width: 100%" placeholder="选择路线">
            <el-option
              v-for="r in routeStore.routes"
              :key="r.id"
              :label="`${siteName(r.siteId)} / ${r.name}`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行机器人" required>
          <el-select v-model="form.robotId" style="width: 100%">
            <el-option
              v-for="r in availableRobots"
              :key="r.id"
              :label="`${r.name} (${r.status})`"
              :value="r.id"
              :disabled="r.status === 'OFFLINE'"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { usePermission } from '@/composables/usePermission'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'

const router = useRouter()
const { can } = usePermission()
const taskStore = useTaskStore()
const routeStore = useRouteStore()
const robotStore = useRobotStore()
const siteStore = useSiteStore()

const dialogVisible = ref(false)
const form = reactive({ name: '', routeId: '', robotId: '' })

const activeTask = computed(() => taskStore.getActiveTask())
const activeRoute = computed(() =>
  activeTask.value ? routeStore.getRouteById(activeTask.value.routeId) ?? null : null,
)
const mapCenter = computed(() => {
  const route = activeRoute.value
  if (route?.path[0]) return route.path[0]
  if (route) return siteStore.getSiteById(route.siteId)?.center ?? { lat: 30.27, lng: 120.15 }
  return { lat: 30.27, lng: 120.15 }
})
const robotPos = computed(() => {
  if (!activeTask.value) return null
  return robotStore.getRobotById(activeTask.value.robotId)?.position ?? null
})

const availableRobots = computed(() => robotStore.robots)

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

function openCreateDialog() {
  form.name = `巡检任务 ${new Date().toLocaleDateString('zh-CN')}`
  form.routeId = routeStore.routes[0]?.id ?? ''
  form.robotId = robotStore.robots.find((r) => r.status === 'ONLINE')?.id ?? ''
  dialogVisible.value = true
}

function submitTask() {
  if (!form.name || !form.routeId || !form.robotId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  const route = routeStore.getRouteById(form.routeId)
  if (!route?.path.length) {
    ElMessage.warning('所选路线尚未绘制路径，请先在巡检规划中配置')
    return
  }
  taskStore.createTask(form.name, form.routeId, form.robotId)
  dialogVisible.value = false
  ElMessage.success('任务已创建，可点击下发开始执行')
}

function cancelTask(id: string) {
  ElMessageBox.confirm('确定取消该任务？', '确认', { type: 'warning' })
    .then(() => {
      taskStore.cancel(id)
      ElMessage.success('任务已取消')
    })
    .catch(() => {})
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
