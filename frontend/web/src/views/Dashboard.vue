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
        <div class="welcome-online">
          <i />
          {{ onlineRobotSummary }}
        </div>
        <div class="quick-actions">
          <el-button type="primary" plain size="small" @click="router.push('/tasks')">任务调度</el-button>
          <el-button plain size="small" @click="router.push('/statistics')">统计分析</el-button>
          <el-button plain size="small" @click="router.push('/profile')">个人中心</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :lg="6" v-for="(stat, index) in stats" :key="stat.label">
        <el-card :class="['stat-card', `stat-card-${index}`]" shadow="never">
          <div class="overview-stat">
            <div class="stat-icon" :class="`tone-${index}`"><el-icon><component :is="statIcons[index]" /></el-icon></div>
            <div>
              <div class="label">{{ stat.label }}</div>
              <div class="value">{{ stat.value }}</div>
              <div class="trend" :class="stat.up ? 'up' : ''">{{ stat.trend }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="overview-live">
      <el-col :xs="24" :lg="11">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-head">
              <span>实时地图 · {{ activeSite?.name ?? '请选择站点' }}</span>
              <el-select v-model="selectedSiteId" size="small" style="width: 200px">
                <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </div>
          </template>
          <div class="overview-map">
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
      <el-col :xs="24" :lg="6">
        <el-card shadow="never" class="completion-card">
          <template #header>任务完成率</template>
          <ChartCard :option="taskChart" :height="260" />
          <div class="completion-legend">
            <span><i class="done" />已完成</span>
            <span><i class="ongoing" />进行中</span>
            <span><i class="pending" />待处理</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="7" class="overview-side">
        <el-card shadow="never" class="schedule-card">
          <template #header>今日巡检日程</template>
          <el-timeline class="schedule">
            <el-timeline-item v-for="s in schedule" :key="s.time" :timestamp="s.time" placement="top">
              {{ s.text }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
        <el-card shadow="never" class="robot-status-card">
          <template #header>机器人状态</template>
          <div v-for="robot in robotStore.robots" :key="robot.id" class="robot-item">
            <div class="robot-head">
              <strong>{{ robot.name }}</strong>
              <el-tag :type="robotStatusType(robot.status)" size="small">{{ robotStatusLabel(robot.status) }}</el-tag>
            </div>
            <div class="robot-meta">
              <span>巡逻：{{ patrolStateLabel(robot.telemetry?.patrolState) }}</span>
              <span>Nav2：{{ nav2StatusLabel(robot.telemetry?.nav2Status) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="overview-analytics">
      <el-col :xs="24" :lg="11">
        <el-card shadow="never" class="trend-card">
          <template #header>
            <div class="card-head"><span>近 7 日告警趋势</span><el-tag type="warning" effect="light">告警总计 {{ overview?.counts.alarms ?? 0 }} 条</el-tag></div>
          </template>
          <ChartCard :option="alarmChart" :height="210" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="13">
        <el-card shadow="never" class="health-card">
          <template #header>
            <span>告警分布与设备健康</span>
          </template>
          <div class="health-content">
            <ChartCard :option="healthChart" :height="210" />
            <div class="health-list">
              <div><span><el-icon color="#12b76a"><CircleCheckFilled /></el-icon>机器人在线率</span><el-progress :percentage="robotOnlineRate" :show-text="false" color="#12b76a" /><strong>{{ robotOnlineRate }}%</strong></div>
              <div><span><el-icon color="#1768f2"><Tickets /></el-icon>任务达成率</span><el-progress :percentage="completionRate" :show-text="false" color="#1768f2" /><strong>{{ completionRate }}%</strong></div>
              <div><span><el-icon color="#ff7a00"><WarningFilled /></el-icon>告警处理率</span><el-progress :percentage="alarmHandledRate" :show-text="false" color="#ff7a00" /><strong>{{ alarmHandledRate }}%</strong></div>
              <em><el-icon><CircleCheckFilled /></el-icon>数据状态稳定</em>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="overview-extensions">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="latest-alarm-card">
          <template #header><div class="card-head"><span>最新告警</span><el-button text type="primary" size="small" @click="router.push('/alarms')">全部</el-button></div></template>
          <el-timeline v-if="recentAlarms.length">
            <el-timeline-item v-for="a in recentAlarms" :key="a.id" :type="a.severity === 'CRITICAL' ? 'danger' : 'warning'" :timestamp="formatTime(a.createdAt)" placement="top">{{ a.message }}</el-timeline-item>
          </el-timeline>
          <div v-else class="empty-hint">暂无告警</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="current-tasks-card">
          <template #header>当前任务</template>
          <el-table :data="activeTasks" size="small" empty-text="暂无进行中的任务">
            <el-table-column prop="name" label="任务名称"><template #default="{ row }"><el-link type="primary" @click="router.push(`/tasks/${row.id}`)">{{ row.name }}</el-link></template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><TaskStatusTag :status="row.status" /></template></el-table-column>
            <el-table-column label="进度" width="180"><template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="10" /></template></el-table-column>
            <el-table-column label="操作" width="260"><template #default="{ row }"><el-button v-if="can('task:control') && row.status === 'RUNNING'" size="small" @click="taskStore.pause(row.id)">暂停</el-button><el-button v-if="can('task:control') && row.status === 'PAUSED'" size="small" type="primary" @click="taskStore.resume(row.id)">恢复</el-button><el-button v-if="can('task:control') && row.status === 'RUNNING'" size="small" type="warning" @click="taskStore.takeover(row.id)">接管</el-button><el-button size="small" @click="router.push(`/tasks/${row.id}`)">详情</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { resourcesApi } from '@/api/resources'
import ChartCard from '@/components/ChartCard.vue'
import Map2D from '@/components/Map2D.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useRobotStore } from '@/stores/robot'
import { useRobotHeartbeatStore } from '@/stores/robotHeartbeat'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { setPageRealtimeResources } from '@/stores/bootstrap'
import type { Robot } from '@/types'
import type { DashboardOverview } from '@/types/pagination'
import { isRobotOnline, nav2StatusLabel, patrolStateLabel } from '@/utils/robotStatus'

const router = useRouter()
const { can } = usePermission()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const routeStore = useRouteStore()
const taskStore = useTaskStore()
const robotStore = useRobotStore()
const heartbeatStore = useRobotHeartbeatStore()
const alarmStore = useAlarmStore()
const overview = ref<DashboardOverview | null>(null)
const onlineRobotSummary = computed(() => {
  const total = overview.value?.counts.robots
    ?? (heartbeatStore.loaded ? heartbeatStore.totalCount : robotStore.robots.length)
  const online = overview.value?.counts.onlineRobots
    ?? (heartbeatStore.loaded ? heartbeatStore.onlineCount : 0)
  return `${online} / ${total} 机器人在线`
})
let refreshTimer: ReturnType<typeof setTimeout> | undefined
const statIcons = ['OfficeBuilding', 'MapLocation', 'Tickets', 'Bell']
const completedTaskCount = computed(() => overview.value?.counts.completedTasks ?? 0)
const totalTaskCount = computed(() => overview.value?.counts.tasks ?? 0)
const completionRate = computed(() => overview.value?.rates.taskCompletion ?? 0)
const weeklyAlarmCounts = computed(() => overview.value?.weeklyAlarmCounts ?? Array(7).fill(0))

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
watch(selectedSiteId, (siteId) => {
  if (!siteId) return
  void resourcesApi.listAreas({ siteId, size: 100 }).then((page) => {
    siteStore.areas = page.items
  })
}, { immediate: true })
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
  { label: '站点数量', value: overview.value?.counts.sites ?? 0, trend: `覆盖 ${overview.value?.counts.sites ?? 0} 座变电站`, up: true },
  { label: '巡检路线', value: overview.value?.counts.routes ?? 0, trend: `共 ${overview.value?.counts.routes ?? 0} 条路线`, up: true },
  { label: '进行中任务', value: overview.value?.counts.activeTasks ?? 0, trend: '实时更新', up: true },
  { label: '未确认告警', value: overview.value?.counts.unacknowledgedAlarms ?? 0, trend: (overview.value?.counts.unacknowledgedAlarms ?? 0) ? '需及时处理' : '暂无待处理', up: !(overview.value?.counts.unacknowledgedAlarms ?? 0) },
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
    progress: { show: true, width: 18, roundCap: true, itemStyle: { color: '#1768f2' } },
    axisLine: { lineStyle: { width: 18, color: [[completionRate.value / 100, '#1768f2'], [1, '#e5ecf7']] } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    title: { show: true, color: '#64748b', fontSize: 12, fontWeight: 600, offsetCenter: [0, '29%'] },
    detail: { color: '#102a56', fontSize: 38, fontWeight: 700, offsetCenter: [0, '-2%'], formatter: '{value}%' },
    data: [{ value: completionRate.value, name: `已完成 ${completedTaskCount.value} 项 / 总计 ${totalTaskCount.value} 项` }],
  }],
}))

const healthChart = computed(() => ({
  grid: { top: 18, right: 16, bottom: 22, left: 42 },
  xAxis: { type: 'category', data: ['紧急', '高', '中'], axisTick: { show: false } },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#eaf0f7', type: 'dashed' } } },
  series: [{
    type: 'bar',
    barWidth: 34,
    data: ['CRITICAL', 'HIGH', 'MEDIUM'].map((severity, index) => ({
      value: overview.value?.alarmSeverity[severity as keyof DashboardOverview['alarmSeverity']] ?? 0,
      itemStyle: { color: ['#1768f2', '#12b76a', '#ff7a00'][index], borderRadius: [4, 4, 0, 0] },
    })),
    label: { show: true, position: 'top', color: '#315272', fontWeight: 700 },
  }],
}))

const robotOnlineRate = computed(() => overview.value?.rates.robotOnline ?? 0)
const alarmHandledRate = computed(() => overview.value?.rates.alarmHandled ?? 100)

function robotName(id: string) {
  return robotStore.getRobotById(id)?.name ?? id
}

const recentAlarms = computed(() => overview.value?.recentAlarms ?? [])
const activeTasks = computed(() => overview.value?.activeTaskItems ?? [])

async function loadOverview() {
  const [data] = await Promise.all([
    resourcesApi.getDashboardOverview(),
    heartbeatStore.refresh(),
  ])
  overview.value = data
  siteStore.sites = data.siteItems
  robotStore.robots = data.robotItems.map((robot) => ({
    ...robot,
    status: isRobotOnline(robot, heartbeatStore.isOnline(robot.id))
      ? (robot.status === 'BUSY' ? 'BUSY' : 'ONLINE')
      : 'OFFLINE',
  }))
  taskStore.tasks = data.activeTaskItems
  alarmStore.alarms = data.recentAlarms
  const routeIds = [...new Set(data.activeTaskItems.map((task) => task.routeId))]
  await Promise.allSettled(routeIds.map((id) => routeStore.loadOne(id)))
}

function scheduleOverviewRefresh() {
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => { void loadOverview() }, 300)
}

onMounted(async () => {
  await loadOverview()
  setPageRealtimeResources(['task', 'robot', 'alarm'], scheduleOverviewRefresh)
})

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function robotStatusLabel(s: Robot['status']) {
  return { ONLINE: '在线', OFFLINE: '离线', BUSY: '任务中' }[s]
}

function robotStatusType(s: Robot['status']) {
  return { ONLINE: 'success', OFFLINE: 'info', BUSY: 'warning' }[s] as 'success' | 'warning' | 'info'
}
</script>

<style scoped>
.welcome-card {
  order: -3;
  position: relative;
  isolation: isolate;
  margin-bottom: 12px;
  overflow: hidden;
  border: 0;
  background:
    radial-gradient(circle at 72% 18%, rgba(92, 181, 255, 0.42), transparent 18%),
    radial-gradient(circle at 84% 88%, rgba(32, 100, 255, 0.45), transparent 34%),
    linear-gradient(105deg, #006b78 0%, #005caf 55%, #0045ae 100%);
  box-shadow: 0 8px 18px rgba(16, 80, 157, 0.16);
}

.welcome-card::before {
  position: absolute;
  z-index: -1;
  top: -46px;
  right: 6%;
  width: min(46vw, 650px);
  height: 210px;
  content: '';
  opacity: 0.48;
  background:
    linear-gradient(112deg, transparent 48%, rgba(151, 222, 255, 0.54) 48.4%, transparent 48.8%) 0 0 / 110px 100%,
    linear-gradient(68deg, transparent 48%, rgba(151, 222, 255, 0.38) 48.4%, transparent 48.8%) 0 0 / 150px 100%,
    radial-gradient(circle at 36% 72%, rgba(111, 219, 255, 0.8) 0 5px, transparent 6px),
    radial-gradient(circle at 76% 27%, rgba(111, 219, 255, 0.75) 0 6px, transparent 7px);
  mask-image: linear-gradient(90deg, transparent, #000 20%, #000 92%, transparent);
}

.welcome-card::after {
  position: absolute;
  z-index: -1;
  right: 13%;
  bottom: -30px;
  width: 124px;
  height: 142px;
  content: '';
  opacity: 0.68;
  border: 2px solid rgba(178, 229, 255, 0.52);
  border-bottom-width: 8px;
  clip-path: polygon(44% 0, 56% 0, 100% 100%, 0 100%);
}

.welcome-inner {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px 16px;
  position: relative;
  z-index: 1;
}

.welcome-text {
  flex: 1;
  min-width: 200px;
}

.welcome-text h3 {
  margin: 0;
  color: #fff;
  font-size: clamp(22px, 2vw, 28px);
  line-height: 1.2;
}

.welcome-text p {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.76);
}

.quick-actions {
  display: flex;
  grid-column: 1 / -1;
  gap: 8px;
  margin-top: 2px;
  padding-left: 68px;
  flex-wrap: wrap;
}

.welcome-card :deep(.el-card__body) {
  min-height: 144px;
  padding: 22px 34px 18px;
}

.welcome-card :deep(.el-button) {
  min-width: 132px;
  height: 34px;
  background: rgba(255, 255, 255, 0.94);
  border-color: rgba(255, 255, 255, 0.72);
  border-radius: 9px;
  color: #164c86;
}

.welcome-online {
  display: inline-flex;
  align-items: center;
  align-self: center;
  gap: 9px;
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid rgba(139, 255, 187, 0.18);
  border-radius: 24px;
  color: #ecfff2;
  background: rgba(0, 185, 91, 0.82);
  box-shadow: 0 9px 20px rgba(0, 57, 65, 0.16);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.welcome-online i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #b9ffcf;
  box-shadow: 0 0 0 4px rgba(185, 255, 207, 0.17);
}

.dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stats-row :deep(.el-col) {
  display: flex;
}

.stats-row :deep(.el-card) {
  width: 100%;
  height: 120px;
  min-height: 120px;
  overflow: hidden;
}

.stats-row :deep(.el-card__body) {
  height: 100%;
  padding: 15px 18px;
  overflow: hidden;
}

.overview-stat {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.overview-stat > div:last-child {
  min-width: 0;
}

.stat-icon {
  display: grid;
  width: 62px;
  height: 62px;
  flex: 0 0 62px;
  place-items: center;
  border-radius: 19px;
  color: #fff;
  font-size: 29px;
}

.tone-0 { background: linear-gradient(135deg, #2878ff, #1455d9); }
.tone-1 { background: linear-gradient(135deg, #1dcc83, #08a75d); }
.tone-2 { background: linear-gradient(135deg, #8b58ff, #6431e6); }
.tone-3 { background: linear-gradient(135deg, #ff7d2d, #f25216); }

.stat-card .label {
  margin: 0;
  color: #526986;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-card .value {
  margin-top: 2px;
  font-size: 34px;
  line-height: 1.05;
}

.stat-card .trend {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
}

.stat-card-0 .value { color: #1768f2; }
.stat-card-1 .value { color: #0bad64; }
.stat-card-2 .value { color: #1768f2; }
.stat-card-3 .value,
.stat-card-3 .trend { color: #f25216; }

.trend {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.trend.up {
  color: #67c23a;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.robot-item {
  padding: 9px 0;
  border-bottom: 1px solid #f0f2f5;
}

.overview-live :deep(.panel-card),
.overview-live :deep(.completion-card),
.overview-analytics :deep(.el-card) {
  height: 100%;
}

.overview-live :deep(.el-card__header),
.overview-analytics :deep(.el-card__header) {
  padding-block: 11px;
}

.overview-map {
  height: 326px;
}

.overview-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.overview-side :deep(.el-card) {
  height: auto;
}

.schedule-card {
  flex: 0 0 auto;
}

.robot-status-card {
  flex: 1;
}

.schedule {
  margin: 0;
  padding-top: 2px;
}

.schedule :deep(.el-timeline-item) {
  padding-bottom: 12px;
}

.schedule :deep(.el-timeline-item:last-child) {
  padding-bottom: 0;
}

.completion-card :deep(.el-card__body) {
  padding-bottom: 10px;
}

.completion-legend {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding-bottom: 2px;
}

.completion-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border: 1px solid #e4ebf4;
  border-radius: 7px;
  color: #5c708d;
  font-size: 11px;
}

.completion-legend i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.completion-legend .done { background: #12b76a; }
.completion-legend .ongoing { background: #ff9700; }
.completion-legend .pending { background: #ff4d43; }

.health-content {
  display: grid;
  grid-template-columns: minmax(260px, 1.1fr) minmax(230px, 0.9fr);
  align-items: center;
  gap: 14px;
}

.health-list {
  display: grid;
  gap: 13px;
}

.health-list > div {
  display: grid;
  grid-template-columns: 104px 1fr 38px;
  align-items: center;
  gap: 8px;
  color: #435b78;
  font-size: 12px;
}

.health-list span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.health-list strong {
  color: #315272;
  text-align: right;
}

.health-list :deep(.el-progress-bar__outer) {
  background: #eaf0f7;
}

.health-list em {
  justify-self: center;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border-radius: 20px;
  color: #0c9f59;
  background: #e9f9f0;
  font-size: 11px;
  font-style: normal;
}

.latest-alarm-card,
.current-tasks-card {
  height: 100%;
}

@media (max-width: 991px) {
  .welcome-card :deep(.el-card__body) {
    padding: 18px 20px 16px;
  }

  .overview-live :deep(.el-col),
  .overview-analytics :deep(.el-col),
  .overview-extensions :deep(.el-col) {
    margin-bottom: 12px;
  }

  .health-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-row :deep(.el-card) {
    height: 112px;
    min-height: 112px;
  }

  .stats-row :deep(.el-card__body) {
    padding: 14px;
  }

  .stat-icon {
    width: 52px;
    height: 52px;
    flex-basis: 52px;
    border-radius: 16px;
    font-size: 24px;
  }

  .stat-card .label {
    font-size: 14px;
  }

  .stat-card .value {
    font-size: 30px;
  }

  .welcome-inner {
    grid-template-columns: 42px minmax(0, 1fr);
    align-items: start;
  }

  .welcome-card :deep(.el-avatar) {
    width: 42px !important;
    height: 42px !important;
  }

  .welcome-online {
    grid-column: 2;
    justify-self: start;
    min-height: 32px;
    padding-inline: 11px;
    font-size: 11px;
  }

  .quick-actions {
    width: 100%;
    padding-left: 0;
  }

  .quick-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .overview-map {
    height: 280px;
  }

  .health-list > div {
    grid-template-columns: 96px 1fr 34px;
  }
}

.robot-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.robot-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}
</style>
