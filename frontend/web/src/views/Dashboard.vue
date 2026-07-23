<template>
  <div class="dashboard">
    <header class="dashboard-header">
      <div>
        <p class="eyebrow">运行总览 / 实时调度</p>
        <h1>运行总览</h1>
        <p class="header-summary">
          {{ overview?.counts.sites ?? 0 }} 个站点 · {{ overview?.counts.routes ?? 0 }} 条巡检路线 · 数据每 3 秒刷新
        </p>
      </div>
      <div class="header-state">
        <span class="live-dot" />
        <span>实时连接正常</span>
        <span class="header-time">{{ currentTime }}</span>
      </div>
    </header>

    <el-card v-if="authStore.user" shadow="never" class="welcome-card">
      <div class="welcome-inner">
        <div class="welcome-text">
          <h3>{{ greeting }}，{{ authStore.user.displayName }}</h3>
          <p>变电站智能巡检全景感知 · 站点、机器人、任务与告警一屏联动</p>
        </div>
        <el-button class="welcome-online" aria-label="查看机器人管理" title="查看机器人管理" @click="go('/robots')">
          <el-icon><Cpu /></el-icon>
          {{ onlineRobotSummary }}
        </el-button>
        <div class="quick-actions">
          <el-button plain size="small" @click="go('/tasks')"><el-icon><CircleCheckFilled /></el-icon>任务调度</el-button>
          <el-button plain size="small" @click="go('/statistics')"><el-icon><DataAnalysis /></el-icon>统计分析</el-button>
          <el-button plain size="small" @click="go('/profile')"><el-icon><User /></el-icon>个人中心</el-button>
        </div>
      </div>
    </el-card>

    <section class="kpi-grid" aria-label="待办指标">
      <button v-for="(stat, index) in kpiStats" :key="stat.label" type="button" class="kpi-card" :class="`kpi-${index}`" @click="stat.action">
        <span class="kpi-icon"><el-icon><component :is="stat.icon" /></el-icon></span>
        <span class="kpi-content">
          <span class="kpi-label">{{ stat.label }}</span>
          <strong class="kpi-value">{{ stat.value }}</strong>
          <span class="kpi-note">{{ stat.note }}</span>
        </span>
        <span class="kpi-link">查看 <el-icon><ArrowRight /></el-icon></span>
      </button>
    </section>

    <section class="command-grid">
      <article class="map-panel" :class="{ fullscreen: mapFullscreen }">
        <div class="panel-toolbar">
          <div class="panel-title">
            <span class="title-mark" />
            <div>
              <h2>实时站点地图</h2>
              <p>{{ activeSite?.name ?? '请选择站点' }}</p>
            </div>
          </div>
          <div class="map-tools">
            <el-select v-model="selectedSiteId" size="small" aria-label="选择站点" style="width: 182px">
              <el-option v-for="site in siteStore.sites" :key="site.id" :label="site.name" :value="site.id" />
            </el-select>
            <el-select v-model="selectedRobotFilter" size="small" aria-label="筛选机器人" style="width: 112px">
              <el-option label="全部机器人" value="ALL" />
              <el-option v-for="robot in robotStore.robots" :key="robot.id" :label="robot.name" :value="robot.id" />
            </el-select>
            <el-select v-model="selectedAlarmFilter" size="small" aria-label="筛选告警" style="width: 100px">
              <el-option label="全部告警" value="ALL" />
              <el-option label="严重告警" value="CRITICAL" />
            </el-select>
            <el-button text class="toolbar-icon" :title="showLegend ? '隐藏地图图例' : '显示地图图例'" @click="showLegend = !showLegend">
              <el-icon><MapLocation /></el-icon>
            </el-button>
            <el-button text class="toolbar-icon" :title="mapFullscreen ? '退出全屏' : '全屏地图'" @click="mapFullscreen = !mapFullscreen">
              <el-icon><Close v-if="mapFullscreen" /><FullScreen v-else /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="map-stage">
          <Map2D
            v-if="activeSite"
            :center="activeSite.center"
            :fallback-center="activeSite.center"
            :areas="siteStore.getAreasBySite(selectedSiteId)"
            area-style="restricted"
            :route="displayRoute"
            :robot-location="mapRobotLocation"
            :robot-label="mapRobotName"
            :alarms="mapAlarms"
          />
          <div v-else class="map-empty">暂无可用站点</div>
          <div v-if="showLegend" class="map-legend">
            <strong>地图图例</strong>
            <span><i class="legend-dot robot" />机器人位置</span>
            <span><i class="legend-line" />巡检路线</span>
            <span><i class="legend-dot checkpoint" />巡检点</span>
            <span><i class="legend-triangle" />告警点</span>
            <span><i class="legend-area" />禁行区</span>
          </div>
          <div class="map-status">
            <span>最后更新 {{ lastUpdatedAt }}</span>
            <span><i class="live-dot" />实时连接正常</span>
          </div>
          <div class="map-task-progress" v-if="activeTask">
            <span class="map-task-label"><el-icon><VideoPlay /></el-icon>{{ activeTask.name }}</span>
            <el-progress :percentage="activeTask.progress" :show-text="false" :stroke-width="6" />
            <strong>{{ activeTask.progress }}%</strong>
          </div>
        </div>
      </article>

      <article class="risk-panel">
        <div class="panel-toolbar">
          <div class="panel-title">
            <span class="title-mark danger" />
            <div>
              <h2>风险队列</h2>
              <p>按严重度排序 · {{ riskAlarms.length }} 条未确认</p>
            </div>
          </div>
          <el-button text type="primary" class="view-all" @click="go('/alarms', { acknowledged: 'false' })">查看全部告警 <el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <div class="risk-list">
          <div v-for="alarm in visibleRiskAlarms" :key="alarm.id" class="risk-item">
            <div class="risk-main">
              <el-tag size="small" effect="dark" :type="severityTagType(alarm.severity)">{{ severityLabel(alarm.severity) }}</el-tag>
              <div class="risk-copy">
                <strong>{{ alarm.message }}</strong>
                <span>{{ alarm.itemName || '巡检设备' }} · {{ alarm.routeName }}{{ alarm.checkpointName ? ` · ${alarm.checkpointName}` : '' }}</span>
                <small>持续 {{ elapsed(alarm.createdAt) }} · {{ formatClock(alarm.createdAt) }} 发生</small>
              </div>
            </div>
            <div class="risk-actions">
              <el-button v-if="!alarm.acknowledged" size="small" plain type="primary" @click="confirmAlarm(alarm)">确认</el-button>
              <el-button size="small" type="primary" plain @click="go('/alarms', { severity: alarm.severity, q: alarm.message })">处置</el-button>
            </div>
          </div>
          <div v-if="!visibleRiskAlarms.length" class="risk-empty"><el-icon><CircleCheckFilled /></el-icon><span>当前没有待确认风险</span></div>
        </div>
        <div class="risk-footer"><span>风险处置 SLA 监测中</span><el-button text type="primary" @click="go('/alarms', { acknowledged: 'false' })">进入告警中心 <el-icon><ArrowRight /></el-icon></el-button></div>
      </article>
    </section>

    <section class="execution-grid">
      <article class="execution-panel compact-panel">
        <div class="panel-toolbar">
          <div class="panel-title"><span class="title-mark blue" /><div><h2>今日巡检执行</h2><p>{{ completedTaskCount }} / {{ totalTaskCount }} 已完成</p></div></div>
          <strong class="completion-rate">{{ completionRate }}%</strong>
        </div>
        <div class="execution-progress"><el-progress :percentage="completionRate" :show-text="false" :stroke-width="9" /><span>{{ completedTaskCount }} / {{ totalTaskCount }}</span></div>
        <div class="execution-breakdown">
          <span><i class="dot blue" />进行中 <strong>{{ activeTaskCount }}</strong></span>
          <span><i class="dot gray" />待执行 <strong>{{ pendingTaskCount }}</strong></span>
          <span><i class="dot red" />异常 <strong>{{ abnormalTaskCount }}</strong></span>
        </div>
        <div class="asset-summary"><span>站点资产 <strong>{{ overview?.counts.sites ?? 0 }}</strong></span><span>巡检路线 <strong>{{ overview?.counts.routes ?? 0 }}</strong></span><el-button text type="primary" @click="go('/sites')">查看资产 <el-icon><ArrowRight /></el-icon></el-button></div>
      </article>

      <article class="timeline-panel compact-panel">
        <div class="panel-toolbar"><div class="panel-title"><span class="title-mark blue" /><div><h2>今日巡检执行时间线</h2><p>当前时段任务优先显示</p></div></div><el-button text type="primary" @click="go('/tasks')">任务调度 <el-icon><ArrowRight /></el-icon></el-button></div>
        <div class="schedule-list">
          <div v-for="item in schedule" :key="`${item.time}-${item.name}`" class="schedule-item" :class="`schedule-${item.state}`">
            <div class="schedule-time">{{ item.time }}</div><span class="schedule-line" /><div class="schedule-body"><strong>{{ item.name }}</strong><span>{{ item.robot }}</span></div><div class="schedule-status"><el-tag size="small" :type="scheduleTagType(item.state)">{{ scheduleLabel(item.state) }}<template v-if="item.state === 'RUNNING'"> · {{ item.progress }}%</template></el-tag></div>
          </div>
        </div>
      </article>
      <article class="robot-panel compact-panel">
        <div class="panel-toolbar"><div class="panel-title"><span class="title-mark green" /><div><h2>机器人运行状态</h2><p>异常优先 · 心跳与任务状态实时同步</p></div></div><el-button text type="primary" @click="go('/robots')">机器人管理 <el-icon><ArrowRight /></el-icon></el-button></div>
        <div class="robot-grid">
          <div v-for="robot in orderedRobots" :key="robot.id" class="robot-card" :class="{ 'is-abnormal': isRobotAbnormal(robot) }">
            <div class="robot-card-head"><div><strong>{{ robot.name }}</strong><span>{{ robot.model || '巡检机器人' }}</span></div><el-tag size="small" :type="robotStatusType(robot.status)">{{ robotStatusLabel(robot.status) }}</el-tag></div>
            <div class="robot-task"><span>任务</span><strong>{{ robotTask(robot)?.name || (robot.status === 'OFFLINE' ? '通信中断' : '空闲待命') }}</strong></div>
            <div v-if="robotTask(robot)" class="robot-progress"><el-progress :percentage="robotTask(robot)?.progress ?? 0" :show-text="false" :stroke-width="5" /><span>{{ robotTask(robot)?.progress ?? 0 }}%</span></div>
            <div class="robot-metrics"><span><b>电量</b>{{ batteryLabel(robot) }}</span><span><b>通信</b>{{ robotCommunication(robot) }}</span><span><b>Nav2</b>{{ nav2StatusLabel(robot.telemetry?.nav2Status) }}</span></div>
            <div class="robot-heartbeat"><span><i :class="['status-dot', isRobotAbnormal(robot) ? 'bad' : 'ok']" />{{ isRobotAbnormal(robot) ? abnormalReason(robot) : '运行稳定' }}</span><span>最后心跳 {{ heartbeatAge(robot) }}</span></div>
          </div>
          <div v-if="!orderedRobots.length" class="robot-empty">暂无机器人数据</div>
        </div>
      </article>
    </section>

    <section class="analytics-grid">
      <article class="analytics-panel compact-panel alarm-trend-panel">
        <div class="panel-toolbar">
          <div class="panel-title"><span class="title-mark amber" /><div><h2>近 7 日告警趋势</h2><p>累计 {{ overview?.counts.alarms ?? 0 }} 条</p></div></div>
          <div class="chart-actions"><el-select v-model="alarmChartMode" size="small" aria-label="告警趋势分组"><el-option label="按等级" value="severity" /><el-option label="按总量" value="total" /></el-select><el-button text class="toolbar-icon" title="告警趋势更多操作"><el-icon><MoreFilled /></el-icon></el-button></div>
        </div>
        <ChartCard :option="alarmChart" :height="190" />
      </article>
      <article class="analytics-panel compact-panel">
        <div class="panel-toolbar">
          <div class="panel-title"><span class="title-mark blue" /><div><h2>近期任务统计</h2><p>任务状态分布 · 共 {{ totalTaskCount }} 项</p></div></div>
          <el-button text type="primary" @click="go('/tasks')">查看任务 <el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <div class="recent-task-list">
          <div v-for="item in recentTaskStats" :key="item.label" class="recent-task-item">
            <div class="recent-task-head"><span><i :style="{ backgroundColor: item.color }" />{{ item.label }}</span><strong>{{ item.value }}</strong></div>
            <el-progress :percentage="item.percentage" :show-text="false" :stroke-width="6" :color="item.color" />
            <small>占近期任务 {{ item.percentage }}%</small>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCheckFilled, Close, Cpu, DataAnalysis, Document, FullScreen, MapLocation, MoreFilled, Tickets, User, VideoPlay, WarningFilled } from '@element-plus/icons-vue'
import ChartCard from '@/components/ChartCard.vue'
import Map2D from '@/components/Map2D.vue'
import { resourcesApi } from '@/api/resources'
import { useRobotStore } from '@/stores/robot'
import { useRobotHeartbeatStore } from '@/stores/robotHeartbeat'
import { useRobotLocationStore } from '@/stores/robotLocation'
import { useAuthStore } from '@/stores/auth'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { setPageRealtimeResources } from '@/stores/bootstrap'
import type { Alarm, InspectionTask, Robot } from '@/types'
import type { DashboardOverview } from '@/types/pagination'
import type { WorkOrder } from '@/types/workOrder'
import { isRobotOnline, nav2StatusLabel } from '@/utils/robotStatus'

const router = useRouter()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const routeStore = useRouteStore()
const taskStore = useTaskStore()
const robotStore = useRobotStore()
const heartbeatStore = useRobotHeartbeatStore()
const locationStore = useRobotLocationStore()
const overview = ref<DashboardOverview | null>(null)
const riskAlarms = ref<Alarm[]>([])
const pendingOrders = ref<WorkOrder[]>([])
const selectedSiteId = ref(siteStore.sites[0]?.id ?? '')
const selectedRobotFilter = ref('ALL')
const selectedAlarmFilter = ref('ALL')
const showLegend = ref(true)
const mapFullscreen = ref(false)
const alarmChartMode = ref<'severity' | 'total'>('severity')
const currentTime = ref(formatClock(new Date().toISOString()))
const lastUpdatedAt = ref('--:--:--')
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined

const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
})
const onlineRobotSummary = computed(() => {
  const total = overview.value?.counts.robots ?? robotStore.robots.length
  const online = overview.value?.counts.onlineRobots ?? robotStore.robots.filter((robot) => robot.status !== 'OFFLINE').length
  return `${online} / ${total} 机器人在线`
})

watch(() => siteStore.sites.map((site) => site.id), (ids) => {
  if (ids.length && !ids.includes(selectedSiteId.value)) selectedSiteId.value = ids[0]!
}, { immediate: true })
watch(selectedSiteId, (siteId) => {
  if (!siteId) return
  locationStore.updatePollingQuery({ siteId })
  void resourcesApi.listAreas({ siteId, size: 100 }).then((page) => { siteStore.areas = page.items })
}, { immediate: true })

const activeSite = computed(() => siteStore.getSiteById(selectedSiteId.value))
const activeTask = computed(() => taskStore.getActiveTask())
const displayRoute = computed(() => {
  if (activeTask.value) return routeStore.getRouteById(activeTask.value.routeId) ?? null
  return routeStore.getRoutesBySite(selectedSiteId.value)[0] ?? null
})
const mapRobotId = computed(() => selectedRobotFilter.value === 'ALL' ? activeTask.value?.robotId ?? robotStore.robots.find((r) => r.status === 'BUSY')?.id ?? '' : selectedRobotFilter.value)
const mapRobotLocation = computed(() => mapRobotId.value ? locationStore.getLocation(mapRobotId.value) ?? null : null)
const mapRobotName = computed(() => mapRobotId.value ? robotStore.getRobotById(mapRobotId.value)?.name ?? mapRobotId.value : '')
const mapAlarms = computed(() => selectedAlarmFilter.value === 'CRITICAL' ? riskAlarms.value.filter((alarm) => alarm.severity === 'CRITICAL') : riskAlarms.value)

const completedTaskCount = computed(() => overview.value?.counts.completedTasks ?? 0)
const totalTaskCount = computed(() => overview.value?.counts.tasks ?? 0)
const completionRate = computed(() => overview.value?.rates.taskCompletion ?? 0)
const activeTaskCount = computed(() => overview.value?.counts.activeTasks ?? 0)
const pendingTaskCount = computed(() => Math.max(totalTaskCount.value - completedTaskCount.value - activeTaskCount.value, 0))
const abnormalTaskCount = computed(() => taskStore.tasks.filter((task) => ['FAILED', 'ESTOPPED', 'START_FAILED'].includes(task.status) || taskStore.executionFor(task.id)?.manualReconciliationRequired || (task.status === 'RUNNING' && task.progress === 0)).length)
const criticalUnacknowledged = computed(() => riskAlarms.value.filter((alarm) => alarm.severity === 'CRITICAL' && !alarm.acknowledged).length)
const abnormalRobots = computed(() => robotStore.robots.filter(isRobotAbnormal).length)
const highPriorityOrders = computed(() => pendingOrders.value.filter((order) => order.priority === 'HIGH' || order.priority === 'URGENT').length)
const visibleRiskAlarms = computed(() => [...riskAlarms.value].sort((a, b) => {
  const severityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  return severityRank[b.severity] - severityRank[a.severity] || Number(a.acknowledged) - Number(b.acknowledged) || a.createdAt.localeCompare(b.createdAt)
}).slice(0, 5))
const recentTaskStats = computed(() => {
  const share = (value: number) => totalTaskCount.value ? Math.round(value / totalTaskCount.value * 100) : 0
  return [
    { label: '进行中', value: activeTaskCount.value, percentage: share(activeTaskCount.value), color: '#1768f2' },
    { label: '待执行', value: pendingTaskCount.value, percentage: share(pendingTaskCount.value), color: '#94a3b8' },
    { label: '已完成', value: completedTaskCount.value, percentage: share(completedTaskCount.value), color: '#12b76a' },
    { label: '异常', value: abnormalTaskCount.value, percentage: share(abnormalTaskCount.value), color: '#f04438' },
  ]
})

const kpiStats = computed(() => [
  { label: '严重未确认告警', value: criticalUnacknowledged.value, note: criticalUnacknowledged.value ? `最早已持续 ${elapsed(oldestAlarm.value?.createdAt)}` : '当前无严重未确认', icon: WarningFilled, action: () => go('/alarms', { severity: 'CRITICAL', acknowledged: 'false' }) },
  { label: '进行中任务', value: activeTaskCount.value, note: abnormalTaskCount.value ? `${abnormalTaskCount.value} 个任务进度异常` : '执行状态正常', icon: Tickets, action: () => go('/tasks', { status: 'RUNNING' }) },
  { label: '异常机器人', value: abnormalRobots.value, note: abnormalRobot.value ? `${abnormalRobot.value.name} · ${abnormalReason(abnormalRobot.value)}` : '机器人运行正常', icon: Cpu, action: () => go('/robots', { status: 'ABNORMAL' }) },
  { label: '待处理工单', value: pendingOrders.value.length, note: highPriorityOrders.value ? `其中 ${highPriorityOrders.value} 个高优先级` : '当前无高优先级工单', icon: Document, action: () => go('/workorders', { status: 'PENDING' }) },
])

const oldestAlarm = computed(() => [...riskAlarms.value].sort((a, b) => a.createdAt.localeCompare(b.createdAt))[0])
const abnormalRobot = computed(() => orderedRobots.value.find(isRobotAbnormal))
const orderedRobots = computed(() => [...robotStore.robots].sort((a, b) => robotPriority(a) - robotPriority(b)))

const schedule = computed(() => {
  const tasks = taskStore.tasks.filter((task) => ['CREATED', 'DISPATCHED', 'STARTING', 'WAITING_LOCAL_CONFIRM', 'RUNNING', 'PAUSED'].includes(task.status)).slice(0, 4)
  if (!tasks.length) return [
    { time: '08:00', name: '主变区域例行巡检', robot: '机器人 A-01', state: 'DONE', progress: 100 },
    { time: '10:30', name: 'GIS 专项巡检', robot: '机器人 B-02', state: 'RUNNING', progress: 42 },
    { time: '14:00', name: '电容器组巡检', robot: '机器人 C-03', state: 'PENDING', progress: 0 },
    { time: '16:00', name: '夜间预检任务待命', robot: '待分配', state: 'PENDING', progress: 0 },
  ] as Array<{ time: string; name: string; robot: string; state: 'DONE' | 'RUNNING' | 'PENDING' | 'ERROR' | 'PAUSED'; progress: number }>
  return tasks.map((task) => ({ time: formatClock(task.startedAt ?? task.createdAt), name: task.name, robot: robotName(task.robotId), state: scheduleState(task), progress: task.progress }))
})

const alarmTrendSeries = computed(() => {
  const definitions = [
    { key: 'CRITICAL', name: '严重', color: '#f04438' },
    { key: 'HIGH', name: '较重', color: '#f59e0b' },
    { key: 'MEDIUM', name: '一般', color: '#94a3b8' },
    { key: 'LOW', name: '提示', color: '#1768f2' },
  ] as const
  const totals = overview.value?.weeklyAlarmCounts ?? Array(7).fill(0)
  const labels: string[] = []
  const keys: string[] = []
  const recentByDay: Record<string, Record<string, number>> = {}
  ;(overview.value?.recentAlarms ?? []).forEach((alarm) => {
    const date = new Date(alarm.createdAt)
    const dayKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    recentByDay[dayKey] ??= {}
    recentByDay[dayKey][alarm.severity] = (recentByDay[dayKey][alarm.severity] ?? 0) + 1
  })
  for (let index = 6; index >= 0; index -= 1) {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() - index)
    keys.push(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`)
    labels.push(`${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`)
  }
  const severityTotal = definitions.reduce((sum, item) => sum + (overview.value?.alarmSeverity[item.key] ?? 0), 0)
  const weights = definitions.map((item) => severityTotal ? (overview.value?.alarmSeverity[item.key] ?? 0) / severityTotal : 0.25)
  if (alarmChartMode.value === 'total') {
    return [{ name: '全部告警', color: '#1768f2', data: totals.map((value) => Number(value) || 0) }]
  }
  return definitions.map((item, definitionIndex) => ({
    name: item.name,
    color: item.color,
    data: keys.map((dayKey, dayIndex) => recentByDay[dayKey]?.[item.key] ?? Math.round((Number(totals[dayIndex]) || 0) * weights[definitionIndex]!)),
  }))
})

const alarmChart = computed(() => ({
  color: alarmTrendSeries.value.map((item) => item.color),
  tooltip: { trigger: 'axis', axisPointer: { type: 'line' } },
  legend: { show: alarmChartMode.value === 'severity', bottom: 0, itemWidth: 8, itemHeight: 8, itemGap: 16, icon: 'circle', textStyle: { color: '#60758f', fontSize: 11 } },
  grid: { top: 12, right: 16, bottom: alarmChartMode.value === 'severity' ? 34 : 22, left: 38, containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: alarmTrendSeries.value[0]?.data.map((_, index) => index) ?? [], axisLine: { lineStyle: { color: '#b8c6d6' } }, axisTick: { show: false }, axisLabel: { color: '#6f8099', fontSize: 10, formatter: (value: number) => { const date = new Date(); date.setDate(date.getDate() - (6 - value)); return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` } } },
  yAxis: { type: 'value', min: 0, minInterval: 1, splitNumber: 4, axisLine: { show: true, lineStyle: { color: '#b8c6d6' } }, axisTick: { show: false }, axisLabel: { color: '#6f8099', fontSize: 10 }, splitLine: { lineStyle: { color: '#e8eef5', type: 'dashed' } } },
  series: alarmTrendSeries.value.map((item) => ({ name: item.name, type: 'line', smooth: 0.18, symbol: 'circle', symbolSize: 6, data: item.data, lineStyle: { width: 2, color: item.color }, itemStyle: { color: item.color, borderColor: '#fff', borderWidth: 1.5 }, areaStyle: { opacity: 0.025, color: item.color } })),
}))

async function loadOverview() {
  const [overviewResult, alarmsResult, ordersResult] = await Promise.allSettled([
    resourcesApi.getDashboardOverview(),
    resourcesApi.listAlarms({ size: 100, acknowledged: false }),
    resourcesApi.listWorkOrders({ size: 500, status: 'PENDING' }),
    heartbeatStore.refresh(),
  ])
  if (overviewResult.status === 'rejected') throw overviewResult.reason
  const data = overviewResult.value
  overview.value = data
  riskAlarms.value = alarmsResult.status === 'fulfilled' ? alarmsResult.value.items : []
  pendingOrders.value = ordersResult.status === 'fulfilled' ? ordersResult.value.items : []
  siteStore.sites = data.siteItems
  robotStore.robots = data.robotItems.map((robot) => ({ ...robot, status: isRobotOnline(robot, heartbeatStore.isOnline(robot.id)) ? (robot.status === 'BUSY' ? 'BUSY' : 'ONLINE') : 'OFFLINE' }))
  taskStore.tasks = data.activeTaskItems
  const routeIds = [...new Set(data.activeTaskItems.map((task) => task.routeId))]
  await Promise.allSettled(routeIds.map((id) => routeStore.loadOne(id)))
  lastUpdatedAt.value = formatClock(new Date().toISOString())
}

function scheduleOverviewRefresh() { if (refreshTimer) clearTimeout(refreshTimer); refreshTimer = setTimeout(() => { void loadOverview() }, 300) }
function go(path: string, query: Record<string, string> = {}) { void router.push({ path, query }) }
function robotName(id: string) { return robotStore.getRobotById(id)?.name ?? id }
function robotTask(robot: Robot) { return taskStore.tasks.find((task) => task.id === robot.currentTaskId || task.robotId === robot.id && ['RUNNING', 'PAUSED', 'STARTING'].includes(task.status)) }
function isRobotAbnormal(robot: Robot) { return robot.status === 'OFFLINE' || robot.telemetry?.bridgeReachable === false || (robot.telemetry?.lastScanAgeSec ?? 0) > 15 || robot.telemetry?.patrolState === 'failed' || robot.telemetry?.nav2Status === 'not_running' && robot.status === 'BUSY' }
function robotPriority(robot: Robot) { if (isRobotAbnormal(robot) && robot.status !== 'OFFLINE') return 0; if (robot.status === 'BUSY') return 1; if (robot.status === 'ONLINE') return 2; return 3 }
function abnormalReason(robot: Robot) { if (robot.status === 'OFFLINE' || robot.telemetry?.bridgeReachable === false) return '通信中断'; if ((robot.telemetry?.lastScanAgeSec ?? 0) > 15) return `激光雷达 ${Math.round(robot.telemetry?.lastScanAgeSec ?? 0)} 秒未更新`; if (robot.telemetry?.nav2Status === 'not_running') return 'Nav2 未运行'; return '巡检状态异常' }
function robotCommunication(robot: Robot) { return robot.status === 'OFFLINE' || robot.telemetry?.bridgeReachable === false ? '异常' : '正常' }
function batteryLabel(robot: Robot) { const value = (robot as Robot & { battery?: number }).battery; return value == null ? '--' : `${Math.round(value)}%` }
function heartbeatAge(robot: Robot) { if (!robot.lastOnlineAt) return '未知'; const sec = Math.max(0, Math.floor((Date.now() - new Date(robot.lastOnlineAt).getTime()) / 1000)); return sec < 60 ? `${sec} 秒前` : `${Math.floor(sec / 60)} 分钟前` }
function scheduleState(task: InspectionTask): 'DONE' | 'RUNNING' | 'PENDING' | 'ERROR' | 'PAUSED' { if (['FAILED', 'ESTOPPED', 'START_FAILED'].includes(task.status)) return 'ERROR'; if (task.status === 'PAUSED') return 'PAUSED'; if (['RUNNING', 'STARTING', 'WAITING_LOCAL_CONFIRM'].includes(task.status)) return 'RUNNING'; return 'PENDING' }
function scheduleLabel(state: string) { return { DONE: '已完成', RUNNING: '执行中', PENDING: '待执行', ERROR: '异常', PAUSED: '暂停' }[state] ?? state }
function scheduleTagType(state: string) { return ({ DONE: 'success', RUNNING: '', PENDING: 'info', ERROR: 'danger', PAUSED: 'warning' }[state] ?? 'info') as 'success' | 'primary' | 'info' | 'danger' | 'warning' }
function severityLabel(severity: Alarm['severity']) { return { CRITICAL: '严重', HIGH: '较重', MEDIUM: '一般', LOW: '提示' }[severity] }
function severityTagType(severity: Alarm['severity']) { return ({ CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'warning', LOW: 'info' }[severity]) as 'danger' | 'warning' | 'info' }
function robotStatusLabel(status: Robot['status']) { return { ONLINE: '在线 · 空闲', BUSY: '在线 · 执行中', OFFLINE: '离线' }[status] }
function robotStatusType(status: Robot['status']) { return ({ ONLINE: 'success', BUSY: 'primary', OFFLINE: 'danger' }[status]) as 'success' | 'primary' | 'danger' }
function formatClock(iso: string) { return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) }
function elapsed(iso?: string) { if (!iso) return '未知'; const sec = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000)); if (sec < 60) return `${sec} 秒`; return `${Math.floor(sec / 60)} 分 ${String(sec % 60).padStart(2, '0')} 秒` }
async function confirmAlarm(alarm: Alarm) { try { await resourcesApi.acknowledgeAlarm(alarm.id); riskAlarms.value = riskAlarms.value.filter((item) => item.id !== alarm.id); ElMessage.success('告警已确认') } catch { ElMessage.error('告警确认失败，请稍后重试') } }

onMounted(async () => { clockTimer = setInterval(() => { currentTime.value = formatClock(new Date().toISOString()) }, 1000); await loadOverview(); if (selectedSiteId.value) locationStore.startPolling({ siteId: selectedSiteId.value }); setPageRealtimeResources(['task', 'robot', 'alarm'], scheduleOverviewRefresh) })
onUnmounted(() => { locationStore.stopPolling(); if (refreshTimer) clearTimeout(refreshTimer); if (clockTimer) clearInterval(clockTimer) })
</script>

<style scoped>
.welcome-card { position: relative; overflow: hidden; border: 1px solid rgba(70, 197, 255, 0.28); background-color: #056a94; background-image: linear-gradient(90deg, rgba(0, 83, 105, 0.56) 0%, rgba(0, 67, 107, 0.2) 48%, rgba(0, 47, 146, 0.08) 100%), url('/img/dashboard.png'); background-position: center 72%; background-size: 100% auto; background-repeat: no-repeat; box-shadow: 0 8px 22px rgba(0, 73, 148, 0.18); }
.welcome-card::before { position: absolute; inset: 0; content: ''; background: linear-gradient(90deg, rgba(0, 74, 82, 0.18), transparent 52%), linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent 42%); pointer-events: none; }
.welcome-inner { display: grid; position: relative; z-index: 1; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px 24px; }
.welcome-text { min-width: 200px; }
.welcome-text h3 { margin: 0; color: #fff; font-size: 21px; line-height: 1.25; font-weight: 750; text-shadow: 0 2px 10px rgba(0, 31, 74, 0.28); }
.welcome-text p { margin: 3px 0 0; color: rgba(239, 252, 255, 0.82); font-size: 12px; line-height: 1.45; }
.welcome-card :deep(.el-card__body) { min-height: 112px; padding: 16px 26px 14px; }
.welcome-online { display: inline-flex; grid-column: 2; grid-row: 1 / span 2; align-items: center; align-self: center; height: 40px; margin: 0; padding: 0 17px; border: 1px solid rgba(179, 255, 210, 0.22); border-radius: 20px; color: #ecfff2; background: rgba(0, 183, 96, 0.9); box-shadow: 0 8px 18px rgba(0, 57, 65, 0.2); font-size: 14px; font-weight: 700; white-space: nowrap; }
.welcome-online:hover { color: #fff; border-color: rgba(214, 255, 231, 0.56); background: #08a861; }
.welcome-online :deep(span) { display: inline-flex; align-items: center; gap: 10px; }
.welcome-online :deep(.el-icon) { font-size: 18px; }
.quick-actions { display: flex; grid-column: 1; gap: 10px; margin-top: -1px; flex-wrap: wrap; }
.quick-actions :deep(.el-button) { min-width: 148px; height: 38px; margin-left: 0; padding-inline: 20px; border-color: rgba(213, 241, 255, 0.88); border-radius: 7px; color: #164f77; background: rgba(255, 255, 255, 0.95); font-size: 14px; font-weight: 650; box-shadow: 0 4px 12px rgba(0, 38, 91, 0.12); }
.quick-actions :deep(.el-button:hover) { color: #075f9d; background: #fff; border-color: #fff; }
.quick-actions :deep(.el-button:nth-child(1) .el-icon) { color: #0bae69; }
.quick-actions :deep(.el-button:nth-child(2) .el-icon) { color: #1677e8; }
.quick-actions :deep(.el-button:nth-child(3) .el-icon) { color: #f59b23; }

.dashboard { --ink: #122b4a; --muted: #6c809a; --line: #e4ebf4; --blue: #1768f2; --red: #f04438; --green: #12b76a; --amber: #ff8a00; display: flex; flex-direction: column; gap: 14px; padding-bottom: 20px; color: var(--ink); }
.dashboard-header, .panel-toolbar, .panel-title, .header-state, .map-tools, .risk-main, .risk-actions, .risk-footer, .execution-breakdown, .asset-summary, .robot-card-head, .robot-metrics, .robot-heartbeat, .schedule-item, .execution-progress { display: flex; align-items: center; }
.dashboard-header { justify-content: space-between; gap: 20px; padding: 2px 2px 0; }
.eyebrow { margin: 0 0 4px; color: var(--blue); font-size: 11px; font-weight: 800; letter-spacing: 1.2px; text-transform: uppercase; }
.dashboard h1 { margin: 0; color: var(--ink); font-size: 26px; line-height: 1.2; letter-spacing: 0; }
.header-summary { margin: 7px 0 0; color: var(--muted); font-size: 12px; }
.header-state { gap: 8px; padding: 8px 12px; border: 1px solid #d8e8e2; border-radius: 8px; color: #15734d; background: #f3fbf7; font-size: 12px; font-weight: 700; white-space: nowrap; }
.header-time { padding-left: 8px; border-left: 1px solid #cfe3d9; color: #628177; font-variant-numeric: tabular-nums; font-weight: 600; }
.live-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: var(--green); box-shadow: 0 0 0 3px rgba(18,183,106,.13); }
.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.kpi-card { position: relative; display: flex; min-width: 0; min-height: 118px; align-items: center; gap: 13px; padding: 18px 17px; border: 1px solid var(--line); border-radius: 12px; color: var(--ink); background: #fff; box-shadow: 0 3px 12px rgba(27,62,109,.06); text-align: left; cursor: pointer; transition: transform .16s ease, box-shadow .16s ease, border-color .16s ease; }
.kpi-card:hover, .kpi-card:focus-visible { border-color: #b6ccec; box-shadow: 0 7px 18px rgba(28,73,137,.12); outline: none; transform: translateY(-2px); }
.kpi-icon { display: grid; width: 45px; height: 45px; flex: 0 0 45px; place-items: center; border-radius: 11px; color: #fff; font-size: 21px; }
.kpi-0 .kpi-icon { background: var(--red); } .kpi-1 .kpi-icon { background: var(--blue); } .kpi-2 .kpi-icon { background: var(--amber); } .kpi-3 .kpi-icon { background: var(--green); }
.kpi-content { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.kpi-label { color: #5d7290; font-size: 13px; font-weight: 700; }
.kpi-value { margin-top: 2px; color: var(--ink); font-size: 30px; line-height: 1.1; font-variant-numeric: tabular-nums; }
.kpi-note { overflow: hidden; margin-top: 5px; color: #8798ad; font-size: 11px; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.kpi-0 .kpi-value { color: var(--red); } .kpi-1 .kpi-value { color: var(--blue); } .kpi-2 .kpi-value { color: #d87900; } .kpi-3 .kpi-value { color: var(--green); }
.kpi-link { display: inline-flex; align-items: center; align-self: flex-end; gap: 2px; color: #7590b1; font-size: 11px; white-space: nowrap; }
.kpi-link .el-icon { font-size: 13px; }
.command-grid { display: grid; grid-template-columns: minmax(0, 1.94fr) minmax(360px, 1fr); gap: 14px; }
.map-panel, .risk-panel, .compact-panel { min-width: 0; border: 1px solid var(--line); border-radius: 12px; background: #fff; box-shadow: 0 3px 12px rgba(27,62,109,.06); overflow: hidden; }
.map-panel { min-height: 620px; } .map-panel.fullscreen { position: fixed; inset: 16px; z-index: 2000; min-height: 0; box-shadow: 0 20px 60px rgba(13,38,78,.26); }
.panel-toolbar { justify-content: space-between; gap: 12px; min-height: 64px; padding: 14px 16px 10px; }
.panel-title { min-width: 0; gap: 10px; } .panel-title > div { min-width: 0; } .panel-title h2 { margin: 0; color: var(--ink); font-size: 15px; font-weight: 800; line-height: 1.2; } .panel-title p { margin: 4px 0 0; overflow: hidden; color: #8798ad; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.title-mark { width: 4px; height: 30px; flex: 0 0 4px; border-radius: 2px; background: var(--red); } .title-mark.blue { background: var(--blue); } .title-mark.green { background: var(--green); } .title-mark.amber { background: var(--amber); } .title-mark.danger { background: var(--red); }
.map-tools { justify-content: flex-end; gap: 6px; } .map-tools :deep(.el-select) { flex: 0 0 auto; } .toolbar-icon { width: 30px; height: 30px; padding: 0; color: #5f7794; }
.map-stage { position: relative; height: 554px; margin: 0 10px 10px; border: 1px solid #dce6f0; border-radius: 9px; overflow: hidden; background: #f2f6fb; } .map-panel.fullscreen .map-stage { height: calc(100vh - 92px); }
.map-stage :deep(.map-container) { min-height: 100%; border: 0; border-radius: 0; } .map-empty, .risk-empty, .robot-empty { display: grid; height: 100%; place-items: center; color: #8b9bb0; font-size: 12px; }
.map-legend, .map-status, .map-task-progress { position: absolute; z-index: 500; border: 1px solid rgba(215,226,239,.9); background: rgba(255,255,255,.94); box-shadow: 0 3px 12px rgba(27,62,109,.1); }
.map-legend { left: 12px; bottom: 12px; display: grid; gap: 7px; min-width: 136px; padding: 10px 11px; border-radius: 8px; } .map-legend strong { margin-bottom: 1px; color: var(--ink); font-size: 11px; } .map-legend span { display: inline-flex; align-items: center; gap: 7px; color: #5d7290; font-size: 10px; }
.legend-dot { width: 9px; height: 9px; border-radius: 50%; } .legend-dot.robot { background: #0f9f68; box-shadow: 0 0 0 2px rgba(15,159,104,.2); } .legend-dot.checkpoint { border: 2px solid var(--blue); background: #fff; } .legend-line { width: 17px; height: 3px; border-radius: 2px; background: var(--blue); } .legend-triangle { width: 0; height: 0; border-right: 5px solid transparent; border-bottom: 9px solid var(--red); border-left: 5px solid transparent; } .legend-area { width: 12px; height: 9px; border: 1px dashed #e28a35; background: rgba(245,158,11,.18); }
.map-status { right: 12px; bottom: 12px; display: grid; gap: 4px; padding: 8px 10px; border-radius: 7px; color: #72859e; font-size: 10px; } .map-status span:last-child { display: inline-flex; align-items: center; gap: 6px; color: #13875b; font-weight: 700; }
.map-task-progress { right: 12px; top: 12px; display: grid; grid-template-columns: auto minmax(80px, 130px) auto; align-items: center; gap: 8px; max-width: 390px; padding: 8px 10px; border-radius: 7px; } .map-task-label { display: inline-flex; max-width: 170px; align-items: center; gap: 5px; overflow: hidden; color: #496681; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; } .map-task-progress strong { color: var(--blue); font-size: 11px; }
.risk-panel { display: flex; min-height: 620px; flex-direction: column; } .view-all { padding: 0; font-size: 11px; } .risk-list { flex: 1; padding: 0 12px; } .risk-item { display: flex; justify-content: space-between; gap: 9px; padding: 14px 0; border-top: 1px solid #edf2f7; } .risk-item:first-child { border-top: 0; } .risk-main { min-width: 0; align-items: flex-start; gap: 9px; } .risk-copy { display: grid; min-width: 0; gap: 4px; } .risk-copy strong { overflow: hidden; color: #29435f; font-size: 12px; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; } .risk-copy span, .risk-copy small { overflow: hidden; color: #7890a9; font-size: 10px; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; } .risk-copy small { color: #9aa9b9; } .risk-actions { flex: 0 0 auto; align-self: center; gap: 4px; } .risk-actions :deep(.el-button) { padding: 5px 9px; font-size: 10px; } .risk-empty { min-height: 340px; gap: 8px; color: #149362; } .risk-empty .el-icon { font-size: 22px; } .risk-footer { justify-content: space-between; min-height: 46px; padding: 8px 14px; border-top: 1px solid #edf2f7; color: #91a0b2; font-size: 10px; } .risk-footer .el-button { padding: 0; font-size: 11px; }
.execution-grid { display: grid; grid-template-columns: minmax(250px, .72fr) minmax(360px, 1.28fr) minmax(300px, 1fr); gap: 14px; } .compact-panel { min-height: 190px; } .compact-panel > .panel-toolbar { padding-bottom: 8px; } .completion-rate { color: var(--blue); font-size: 26px; font-variant-numeric: tabular-nums; }
.execution-panel { padding-bottom: 0; } .execution-progress { gap: 12px; padding: 2px 16px 0; } .execution-progress :deep(.el-progress) { flex: 1; } .execution-progress span { color: #657c98; font-size: 11px; font-weight: 700; white-space: nowrap; } .execution-breakdown { gap: 20px; padding: 16px 16px 14px; } .execution-breakdown span { display: inline-flex; align-items: center; gap: 5px; color: #71849b; font-size: 11px; } .execution-breakdown strong { color: var(--ink); } .dot { width: 7px; height: 7px; border-radius: 50%; } .dot.blue { background: var(--blue); } .dot.gray { background: #b7c3d1; } .dot.red { background: var(--red); }
.asset-summary { justify-content: space-between; gap: 10px; padding: 11px 16px; border-top: 1px solid #edf2f7; color: #7890a9; font-size: 11px; } .asset-summary span { white-space: nowrap; } .asset-summary strong { margin-left: 3px; color: var(--ink); font-size: 15px; } .asset-summary .el-button { padding: 0; font-size: 11px; }
.schedule-list { padding: 0 16px 13px; } .schedule-item { min-height: 34px; gap: 8px; } .schedule-time { width: 42px; flex: 0 0 42px; color: #7890a9; font-size: 11px; font-variant-numeric: tabular-nums; } .schedule-line { position: relative; width: 9px; height: 34px; flex: 0 0 9px; border-left: 1px solid #dce5ef; } .schedule-line::after { position: absolute; top: 12px; left: -4px; width: 7px; height: 7px; border: 2px solid #b9c8d8; border-radius: 50%; background: #fff; content: ''; } .schedule-RUNNING .schedule-line::after { border-color: var(--blue); background: var(--blue); box-shadow: 0 0 0 3px rgba(23,104,242,.13); } .schedule-DONE .schedule-line::after { border-color: var(--green); background: var(--green); } .schedule-ERROR .schedule-line::after { border-color: var(--red); background: var(--red); } .schedule-body { display: grid; min-width: 0; flex: 1; gap: 2px; } .schedule-body strong { overflow: hidden; color: #3c5773; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; } .schedule-body span { overflow: hidden; color: #94a3b5; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; } .schedule-status :deep(.el-tag) { border: 0; font-size: 10px; }
.robot-panel { padding-bottom: 13px; } .execution-grid .robot-grid { display: grid; grid-template-columns: 1fr; max-height: 314px; overflow-y: auto; } .robot-grid { gap: 10px; padding: 0 14px; } .robot-card { min-width: 0; padding: 12px; border: 1px solid #e4ebf4; border-radius: 8px; background: #fbfdff; } .robot-card.is-abnormal { border-color: #f3c4be; background: #fffafa; } .robot-card-head { justify-content: space-between; gap: 8px; } .robot-card-head > div { display: grid; min-width: 0; gap: 3px; } .robot-card-head strong { overflow: hidden; color: #29435f; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; } .robot-card-head span { color: #8b9caf; font-size: 10px; } .robot-card-head :deep(.el-tag) { flex: 0 0 auto; border: 0; font-size: 10px; } .robot-task { display: grid; gap: 3px; margin-top: 12px; } .robot-task span, .robot-metrics b { color: #91a0b2; font-size: 10px; font-weight: 500; } .robot-task strong { overflow: hidden; color: #526b86; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; } .robot-progress { display: flex; align-items: center; gap: 8px; margin-top: 7px; } .robot-progress :deep(.el-progress) { flex: 1; } .robot-progress span { color: var(--blue); font-size: 10px; } .robot-metrics { gap: 10px; margin-top: 12px; } .robot-metrics span { display: grid; gap: 2px; color: #526b86; font-size: 10px; } .robot-metrics b { font-weight: 500; } .robot-heartbeat { justify-content: space-between; gap: 8px; margin-top: 11px; padding-top: 8px; border-top: 1px solid #edf2f7; color: #8a9aab; font-size: 10px; } .robot-heartbeat span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .robot-heartbeat span:first-child { display: inline-flex; align-items: center; gap: 5px; color: #5f7791; } .status-dot { width: 6px; height: 6px; border-radius: 50%; } .status-dot.ok { background: var(--green); } .status-dot.bad { background: var(--red); } .analytics-grid { display: grid; grid-template-columns: 1.1fr .9fr; gap: 14px; } .analytics-panel { min-height: 210px; } .recent-task-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; padding: 4px 18px 18px; } .recent-task-item { min-width: 0; padding: 7px 0; } .recent-task-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 6px; color: #5b718d; font-size: 11px; } .recent-task-head span { display: inline-flex; align-items: center; gap: 6px; } .recent-task-head i { width: 7px; height: 7px; border-radius: 50%; } .recent-task-head strong { color: var(--ink); font-size: 18px; font-variant-numeric: tabular-nums; } .recent-task-item small { display: block; margin-top: 4px; color: #94a3b5; font-size: 10px; }
.chart-actions { display: flex; align-items: center; gap: 5px; } .chart-actions :deep(.el-select) { width: 92px; } .chart-actions :deep(.el-select__wrapper) { min-height: 30px; } .alarm-trend-panel :deep(.chart-card) { padding-inline: 4px; }
@media (max-width: 1200px) { .command-grid { grid-template-columns: minmax(0, 1.6fr) minmax(310px, 1fr); } .map-tools :deep(.el-select:first-child) { width: 150px !important; } .robot-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 1100px) and (min-width: 901px) { .execution-grid { grid-template-columns: minmax(250px, .78fr) minmax(360px, 1.22fr); } .execution-grid .robot-panel { grid-column: 1 / -1; } .execution-grid .robot-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); max-height: none; overflow: visible; } }
@media (max-width: 900px) { .kpi-grid, .command-grid, .execution-grid, .analytics-grid { grid-template-columns: 1fr; } .map-panel, .risk-panel { min-height: auto; } .map-stage { height: 480px; } .risk-panel { min-height: 520px; } .dashboard-header { align-items: flex-start; flex-direction: column; } .execution-grid .robot-grid { max-height: none; overflow: visible; } }
@media (max-width: 600px) { .dashboard { gap: 10px; } .kpi-grid { gap: 8px; } .kpi-card { min-height: 104px; padding: 13px 12px; gap: 9px; } .kpi-icon { width: 36px; height: 36px; flex-basis: 36px; font-size: 18px; } .kpi-value { font-size: 25px; } .kpi-link { display: none; } .panel-toolbar { align-items: flex-start; flex-direction: column; } .map-tools { width: 100%; justify-content: flex-start; flex-wrap: wrap; } .map-stage { height: 390px; margin-inline: 6px; } .map-task-progress { left: 8px; right: 8px; grid-template-columns: auto 1fr auto; } .robot-grid { grid-template-columns: 1fr; } .risk-item { align-items: flex-start; flex-direction: column; } .risk-actions { align-self: flex-end; } .asset-summary { flex-wrap: wrap; } .header-state { align-self: stretch; justify-content: center; } .welcome-card :deep(.el-card__body) { padding: 16px 18px 14px; } .welcome-inner { grid-template-columns: 1fr; gap: 10px; } .welcome-online { grid-column: 1; grid-row: auto; justify-self: start; padding-inline: 12px; } .quick-actions { grid-column: 1; width: 100%; } .quick-actions :deep(.el-button) { min-width: 112px; flex: 1 1 112px; padding-inline: 12px; } }
</style>
