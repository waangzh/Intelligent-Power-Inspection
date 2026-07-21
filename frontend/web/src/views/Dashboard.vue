<template>
  <div class="dashboard">
    <el-card v-if="authStore.user" shadow="never" class="welcome-card">
      <div class="welcome-inner">
        <div class="welcome-text">
          <h3>{{ greeting }}，{{ authStore.user.displayName }}</h3>
          <p>变电站智能巡检全景感知 · 站点、机器人、任务与告警一屏联动</p>
        </div>
        <el-button
          class="welcome-online"
          aria-label="查看机器人管理"
          title="查看机器人管理"
          @click="router.push('/robots')"
        >
          <el-icon><Cpu /></el-icon>
          {{ onlineRobotSummary }}
        </el-button>
        <div class="quick-actions">
          <el-button plain size="small" @click="router.push('/tasks')">
            <el-icon><CircleCheckFilled /></el-icon>
            任务调度
          </el-button>
          <el-button plain size="small" @click="router.push('/statistics')">
            <el-icon><DataAnalysis /></el-icon>
            统计分析
          </el-button>
          <el-button plain size="small" @click="router.push('/profile')">
            <el-icon><User /></el-icon>
            个人中心
          </el-button>
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
              :robot-location="activeRobotLocation"
              :robot-label="activeRobotName"
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
            <el-table-column label="状态" width="100"><template #default="{ row }"><TaskStatusTag :status="row.status" :manual-reconciliation-required="taskStore.executionFor(row.id)?.manualReconciliationRequired" /></template></el-table-column>
            <el-table-column label="进度" width="180"><template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="10" /></template></el-table-column>
            <el-table-column label="操作" width="260"><template #default="{ row }"><el-button v-if="can('task:control') && row.status === 'RUNNING'" size="small" @click="taskStore.pause(row.id)">暂停</el-button><el-button v-if="can('task:control') && row.status === 'PAUSED'" size="small" type="primary" @click="taskStore.resume(row.id)">恢复</el-button><el-button v-if="can('task:control') && row.status === 'RUNNING'" size="small" type="warning" @click="taskStore.takeover(row.id)">接管</el-button><el-button size="small" @click="router.push(`/tasks/${row.id}`)">详情</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { resourcesApi } from '@/api/resources'
import ChartCard from '@/components/ChartCard.vue'
import Map2D from '@/components/Map2D.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useRobotStore } from '@/stores/robot'
import { useRobotHeartbeatStore } from '@/stores/robotHeartbeat'
import { useRobotLocationStore } from '@/stores/robotLocation'
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
const locationStore = useRobotLocationStore()
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
  locationStore.updatePollingQuery({ siteId })
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
const activeRobotId = computed(() => taskStore.getActiveTask()?.robotId ?? '')
const activeRobotLocation = computed(() => {
  const robotId = activeRobotId.value
  if (!robotId) return null
  return locationStore.getLocation(robotId) ?? null
})
const activeRobotName = computed(() => {
  const robotId = activeRobotId.value
  return robotId ? robotStore.getRobotById(robotId)?.name ?? robotId : ''
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
  if (selectedSiteId.value) {
    locationStore.startPolling({ siteId: selectedSiteId.value })
  }
  setPageRealtimeResources(['task', 'robot', 'alarm'], scheduleOverviewRefresh)
})

onUnmounted(() => {
  locationStore.stopPolling()
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
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid rgba(70, 197, 255, 0.28);
  background-color: #056a94;
  background-image:
    linear-gradient(90deg, rgba(0, 83, 105, 0.56) 0%, rgba(0, 67, 107, 0.2) 48%, rgba(0, 47, 146, 0.08) 100%),
    url('/img/dashboard.png');
  background-position: center 72%;
  background-size: 100% auto;
  background-repeat: no-repeat;
  box-shadow: 0 8px 22px rgba(0, 73, 148, 0.18);
}

.welcome-card::before {
  position: absolute;
  inset: 0;
  content: '';
  background:
    linear-gradient(90deg, rgba(0, 74, 82, 0.18), transparent 52%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent 42%);
  pointer-events: none;
}

.welcome-inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px 24px;
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
  font-size: 21px;
  line-height: 1.25;
  font-weight: 750;
  text-shadow: 0 2px 10px rgba(0, 31, 74, 0.28);
}

.welcome-text p {
  margin: 3px 0 0;
  font-size: 12px;
  color: rgba(239, 252, 255, 0.82);
  line-height: 1.45;
}

.quick-actions {
  display: flex;
  grid-column: 1;
  gap: 10px;
  margin-top: -1px;
  flex-wrap: wrap;
}

.welcome-card :deep(.el-card__body) {
  min-height: 112px;
  padding: 16px 26px 14px;
}

.quick-actions :deep(.el-button) {
  min-width: 148px;
  height: 38px;
  margin-left: 0;
  padding-inline: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(213, 241, 255, 0.88);
  border-radius: 7px;
  color: #164f77;
  font-size: 14px;
  font-weight: 650;
  box-shadow: 0 4px 12px rgba(0, 38, 91, 0.12);
  transition: transform 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
}

.quick-actions :deep(.el-button > span) {
  gap: 10px;
}

.quick-actions :deep(.el-button:hover) {
  color: #075f9d;
  background: #fff;
  border-color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(0, 38, 91, 0.2);
}

.quick-actions :deep(.el-button:active) {
  transform: translateY(0);
  box-shadow: 0 3px 9px rgba(0, 38, 91, 0.16);
}

.quick-actions :deep(.el-button:focus-visible) {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.quick-actions :deep(.el-button:nth-child(1) .el-icon) { color: #0bae69; }
.quick-actions :deep(.el-button:nth-child(2) .el-icon) { color: #1677e8; }
.quick-actions :deep(.el-button:nth-child(3) .el-icon) { color: #f59b23; }

.quick-actions :deep(.el-icon) {
  font-size: 18px;
}

.welcome-online {
  display: inline-flex;
  grid-column: 2;
  grid-row: 1 / span 2;
  align-items: center;
  align-self: center;
  min-width: 0;
  height: 40px;
  margin: 0;
  padding: 0 17px;
  border: 1px solid rgba(179, 255, 210, 0.22);
  border-radius: 20px;
  color: #ecfff2;
  background: rgba(0, 183, 96, 0.9);
  box-shadow: 0 8px 18px rgba(0, 57, 65, 0.2);
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  transition: transform 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.welcome-online:hover,
.welcome-online:focus {
  color: #fff;
  border-color: rgba(214, 255, 231, 0.56);
  background: #08a861;
}

.welcome-online:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(0, 57, 65, 0.28);
}

.welcome-online:active {
  transform: translateY(0);
}

.welcome-online:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.welcome-online :deep(span) {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.welcome-online :deep(.el-icon) {
  font-size: 18px;
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
    padding: 16px 20px 14px;
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
    grid-template-columns: 1fr;
    align-items: center;
    gap: 10px;
  }

  .welcome-online {
    grid-column: 1;
    grid-row: auto;
    justify-self: start;
    height: 40px;
    padding-inline: 11px;
    font-size: 14px;
  }

  .quick-actions {
    grid-column: 1;
    width: 100%;
  }

  .quick-actions :deep(.el-button) {
    min-width: 112px;
    flex: 1 1 112px;
    margin-left: 0;
  }

  .welcome-card {
    background-position: 68% center;
    background-size: auto 100%;
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
