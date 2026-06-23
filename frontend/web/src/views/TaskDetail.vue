<template>
  <div v-if="task">
    <PageHeader :title="task.name" :description="`任务 ID: ${task.id}`" :breadcrumbs="breadcrumbs">
      <template #actions>
        <TaskStatusTag :status="task.status" />
        <el-button v-if="can('task:dispatch') && task.status === 'CREATED'" type="primary" @click="taskStore.dispatch(task.id)">下发</el-button>
        <el-button v-if="can('task:control') && task.status === 'RUNNING'" @click="taskStore.pause(task.id)">暂停</el-button>
        <el-button v-if="can('task:control') && task.status === 'PAUSED'" type="primary" @click="taskStore.resume(task.id)">恢复</el-button>
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
              <el-progress :percentage="task.progress" style="width: 200px" />
            </el-descriptions-item>
            <el-descriptions-item label="当前检查点">第 {{ task.currentCheckpointSeq }} / {{ route?.checkpoints.length ?? 0 }} 个</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmt(task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ task.startedAt ? fmt(task.startedAt) : '-' }}</el-descriptions-item>
          </el-descriptions>
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
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>轨迹地图</template>
          <div style="height: 280px">
            <Map2D :center="mapCenter" :route="route ?? null" :robot-position="robotPos" />
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import type { TaskEvent } from '@/types'

const router = useRouter()
const routeParam = useRoute()
const taskStore = useTaskStore()
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
const taskAlarms = computed(() => alarmStore.alarms.filter((a) => a.taskId === taskId.value))

const mapCenter = computed(() => route.value?.path[0] ?? siteStore.sites[0]?.center ?? { lat: 30.27, lng: 120.15 })
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

function timelineType(type: TaskEvent['type']) {
  if (type === 'ALARM') return 'danger'
  if (type === 'COMPLETE') return 'success'
  return 'primary'
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
</style>
