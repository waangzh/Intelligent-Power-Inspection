<template>
  <div class="dashboard">
    <el-card v-if="authStore.user" shadow="never" class="welcome-card">
      <div class="welcome-inner">
        <UserAvatar
          :display-name="authStore.user.displayName"
          :avatar-url="authStore.user.avatarUrl"
          :seed="authStore.user.id"
          :size="52"
        />
        <div class="welcome-text">
          <h3>{{ greeting }}，{{ authStore.user.displayName }}</h3>
          <p>{{ authStore.user.bio || '欢迎使用电力智能巡检平台，祝您工作顺利！' }}</p>
        </div>
        <div class="quick-actions">
          <el-button type="primary" plain size="small" @click="router.push('/tasks')">任务调度</el-button>
          <el-button type="warning" plain size="small" @click="router.push('/alarms')">
            告警中心
            <el-badge v-if="alarmStore.unacknowledgedCount" :value="alarmStore.unacknowledgedCount" style="margin-left: 4px" />
          </el-button>
          <el-button plain size="small" @click="router.push('/statistics')">统计分析</el-button>
          <el-button plain size="small" @click="router.push('/profile')">个人中心</el-button>
        </div>
      </div>
    </el-card>

    <PageHeader title="运行总览" description="电力巡检平台运行态势一屏掌握" :breadcrumbs="[{ label: '监控中心' }, { label: '运行总览' }]" />

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card class="stat-card" shadow="never">
          <div class="value">{{ stat.value }}</div>
          <div class="label">{{ stat.label }}</div>
          <div class="trend" :class="stat.up ? 'up' : ''">{{ stat.trend }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>告警趋势（7日）</template>
          <ChartCard :option="alarmChart" :height="220" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>任务完成率</template>
          <ChartCard :option="taskChart" :height="220" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>今日巡检日程</template>
          <el-timeline class="schedule">
            <el-timeline-item v-for="s in schedule" :key="s.time" :timestamp="s.time" placement="top">
              {{ s.text }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-head">
              <span>实时地图 · {{ activeSite?.name ?? '请选择站点' }}</span>
              <el-select v-model="selectedSiteId" size="small" style="width: 200px">
                <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </div>
          </template>
          <div style="height: 380px">
            <Map2D
              v-if="activeSite"
              :center="activeSite.center"
              :fallback-center="activeSite.center"
              :areas="siteStore.getAreasBySite(selectedSiteId)"
              :route="displayRoute"
              :robot-position="robotPosition"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>机器人状态</template>
          <div v-for="robot in robotStore.robots" :key="robot.id" class="robot-item">
            <div class="robot-head">
              <strong>{{ robot.name }}</strong>
              <el-tag :type="robotStatusType(robot.status)" size="small">{{ robotStatusLabel(robot.status) }}</el-tag>
            </div>
            <el-progress :percentage="robot.battery" :stroke-width="8" :color="batteryColor(robot.battery)" />
          </div>
        </el-card>
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>最新告警</span>
              <el-button text type="primary" size="small" @click="router.push('/alarms')">全部</el-button>
            </div>
          </template>
          <el-timeline v-if="recentAlarms.length">
            <el-timeline-item v-for="a in recentAlarms" :key="a.id" :type="a.severity === 'CRITICAL' ? 'danger' : 'warning'" :timestamp="formatTime(a.createdAt)" placement="top">
              {{ a.message }}
            </el-timeline-item>
          </el-timeline>
          <div v-else class="empty-hint">暂无告警</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>当前任务</template>
      <el-table :data="activeTasks" size="small" empty-text="暂无进行中的任务">
        <el-table-column prop="name" label="任务名称">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/tasks/${row.id}`)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><TaskStatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="进度" width="180">
          <template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="10" /></template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button v-if="can('task:control') && row.status === 'RUNNING'" size="small" @click="taskStore.pause(row.id)">暂停</el-button>
            <el-button v-if="can('task:control') && row.status === 'PAUSED'" size="small" type="primary" @click="taskStore.resume(row.id)">恢复</el-button>
            <el-button v-if="can('task:control') && row.status === 'RUNNING'" size="small" type="warning" @click="taskStore.takeover(row.id)">接管</el-button>
            <el-button size="small" @click="router.push(`/tasks/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ChartCard from '@/components/ChartCard.vue'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAnalytics } from '@/composables/useAnalytics'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import type { Robot } from '@/types'

const router = useRouter()
const { can } = usePermission()
const authStore = useAuthStore()
const { weeklyAlarmCounts, completionRate } = useAnalytics()
const siteStore = useSiteStore()
const routeStore = useRouteStore()
const taskStore = useTaskStore()
const robotStore = useRobotStore()
const alarmStore = useAlarmStore()

const selectedSiteId = ref(siteStore.sites[0]?.id ?? '')
watch(
  () => siteStore.sites.map((site) => site.id),
  (ids) => {
    if (ids.length > 0 && !ids.includes(selectedSiteId.value)) {
      selectedSiteId.value = ids[0]
    }
  },
  { immediate: true },
)
const activeSite = computed(() => siteStore.getSiteById(selectedSiteId.value))
const displayRoute = computed(() => {
  const active = taskStore.getActiveTask()
  if (active) return routeStore.getRouteById(active.routeId) ?? null
  return routeStore.getRoutesBySite(selectedSiteId.value)[0] ?? null
})
const robotPosition = computed(() => {
  const active = taskStore.getActiveTask()
  return active ? robotStore.getRobotById(active.robotId)?.position ?? null : null
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const stats = computed(() => [
  { label: '站点数量', value: siteStore.sites.length, trend: `覆盖 ${siteStore.sites.length} 座变电站`, up: true },
  { label: '巡检路线', value: routeStore.routes.length, trend: `共 ${routeStore.routes.length} 条路线`, up: true },
  { label: '进行中任务', value: taskStore.tasks.filter((t) => ['RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status)).length, trend: '实时更新', up: true },
  { label: '未确认告警', value: alarmStore.unacknowledgedCount, trend: alarmStore.unacknowledgedCount ? '需及时处理' : '暂无待处理', up: !alarmStore.unacknowledgedCount },
])

const schedule = computed(() => {
  const active = taskStore.tasks.filter((t) => ['DISPATCHED', 'RUNNING', 'CREATED'].includes(t.status))
  if (active.length) {
    return active.slice(0, 4).map((t) => ({
      time: new Date(t.startedAt ?? t.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      text: `${t.name}（${robotName(t.robotId)}）`,
    }))
  }
  return [
    { time: '08:00', text: '主变区例行巡检（机器人-A1）' },
    { time: '10:30', text: 'GIS 专项巡检（机器人-B2）' },
    { time: '14:00', text: '电容器组巡检（机器人-C3）' },
    { time: '16:00', text: '夜间预检任务待命' },
  ]
})

const alarmChart = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: ['一', '二', '三', '四', '五', '六', '日'], show: false },
  yAxis: { type: 'value', show: false },
  grid: { top: 10, bottom: 10, left: 10, right: 10 },
  series: [{ type: 'line', smooth: true, data: weeklyAlarmCounts.value, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#e6a23c' } }],
}))

const taskChart = computed(() => ({
  series: [{
    type: 'gauge',
    min: 0,
    max: 100,
    progress: { show: true, width: 10 },
    axisLine: { lineStyle: { width: 10 } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    detail: { fontSize: 18, formatter: '{value}%' },
    data: [{ value: completionRate.value }],
  }],
}))

function robotName(id: string) {
  return robotStore.getRobotById(id)?.name ?? id
}

const recentAlarms = computed(() => alarmStore.alarms.slice(0, 5))
const activeTasks = computed(() =>
  taskStore.tasks.filter((t) => ['DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status)),
)

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function robotStatusLabel(s: Robot['status']) {
  return { ONLINE: '在线', OFFLINE: '离线', BUSY: '任务中', CHARGING: '充电中' }[s]
}

function robotStatusType(s: Robot['status']) {
  return { ONLINE: 'success', OFFLINE: 'info', BUSY: 'warning', CHARGING: 'info' }[s] as 'success' | 'warning' | 'info'
}

function batteryColor(p: number) {
  if (p > 60) return '#67c23a'
  if (p > 30) return '#e6a23c'
  return '#f56c6c'
}
</script>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
  background: linear-gradient(135deg, #f0f6ff 0%, #fff 60%);
}

.welcome-inner {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.welcome-text {
  flex: 1;
  min-width: 200px;
}

.welcome-text h3 {
  margin: 0;
  font-size: 18px;
  color: #1a2b3c;
}

.welcome-text p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.quick-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.trend {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.trend.up {
  color: #67c23a;
}

.schedule {
  max-height: 200px;
  overflow: auto;
  padding-top: 8px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.robot-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
}

.robot-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
</style>
