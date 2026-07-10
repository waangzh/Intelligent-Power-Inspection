<template>
  <div class="dashboard">
    <PageHeader
      class="dashboard-page-header"
      title="运行总览"
      description="电力巡检平台运行态势一屏掌握"
      :breadcrumbs="[{ label: '监控中心' }, { label: '运行总览' }]"
    />

    <el-card v-if="authStore.user" shadow="never" class="welcome-card">
      <div class="welcome-inner">
        <div class="hero-grid" aria-hidden="true">
          <span v-for="line in 6" :key="line" :style="{ '--line-index': line }" />
        </div>
        <div class="hero-tower hero-tower-left" aria-hidden="true" />
        <div class="hero-tower hero-tower-right" aria-hidden="true" />

        <div class="welcome-content">
          <div class="welcome-text">
            <h2>{{ greeting }}，{{ authStore.user.displayName }}</h2>
            <p>{{ authStore.user.bio || '变电站智能巡检综合驾驶舱 · 站点、机器人、任务与告警一屏联动' }}</p>
          </div>
          <div class="status-pills">
            <div v-for="item in heroStatuses" :key="item.label" class="status-pill" :class="`is-${item.tone}`">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>

        <div class="welcome-aside">
          <div class="online-pill">
            <el-icon><Monitor /></el-icon>
            <strong>{{ onlineRobotCount }} / {{ robotStore.robots.length }}</strong>
            <span>机器人在线</span>
          </div>
          <div class="quick-actions" aria-label="快捷操作">
            <el-button text size="small" @click="router.push('/tasks')">任务调度</el-button>
            <el-button text size="small" @click="router.push('/alarms')">
              告警中心
              <el-badge v-if="alarmStore.unacknowledgedCount" :value="alarmStore.unacknowledgedCount" />
            </el-button>
            <el-button text size="small" @click="router.push('/statistics')">统计分析</el-button>
            <el-button text size="small" @click="router.push('/profile')">个人中心</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <section class="stats-grid" aria-label="核心运行指标">
      <el-card v-for="stat in stats" :key="stat.label" class="stat-card" :class="`stat-card--${stat.tone}`" shadow="never">
        <div class="stat-icon">
          <el-icon><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-copy">
          <div class="label">{{ stat.label }}</div>
          <div class="value">{{ stat.value }}</div>
          <div class="trend" :class="{ up: stat.up, attention: stat.tone === 'orange' && !stat.up }">{{ stat.trend }}</div>
        </div>
      </el-card>
    </section>

    <section class="overview-grid" aria-label="实时运行态势">
      <el-card shadow="never" class="dashboard-card map-panel">
        <template #header>
          <div class="card-head">
            <div>
              <span class="card-title">实时地图</span>
              <span class="card-subtitle">{{ activeSite?.name ?? '请选择站点' }}</span>
            </div>
            <el-select v-model="selectedSiteId" size="small" class="site-select" aria-label="选择站点">
              <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </div>
        </template>
        <div class="map-stage">
          <Map2D
            v-if="activeSite"
            :center="activeSite.center"
            :fallback-center="activeSite.center"
            :areas="siteStore.getAreasBySite(selectedSiteId)"
            :route="displayRoute"
            :robot-position="robotPosition"
          />
          <div v-else class="map-placeholder">
            <el-icon><Location /></el-icon>
            <span>暂无可展示的站点</span>
          </div>
          <div class="map-legend">
            <span><i class="legend-dot legend-dot--online" />在线轨迹</span>
            <span><i class="legend-dot legend-dot--checkpoint" />巡检点</span>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="dashboard-card completion-panel">
        <template #header>
          <div class="card-head">
            <span class="card-title">任务完成率</span>
            <span class="card-meta">实时统计</span>
          </div>
        </template>
        <div class="completion-chart">
          <ChartCard :option="taskChart" :height="252" />
        </div>
        <div class="completion-legend">
          <span><i class="legend-dot legend-dot--done" />已完成</span>
          <span><i class="legend-dot legend-dot--running" />进行中</span>
          <span><i class="legend-dot legend-dot--pending" />待处理</span>
        </div>
      </el-card>

      <div class="side-stack">
        <el-card shadow="never" class="dashboard-card schedule-panel">
          <template #header>
            <div class="card-head">
              <span class="card-title">今日巡检日程</span>
              <span class="card-meta">{{ schedule.length }} 项</span>
            </div>
          </template>
          <el-timeline class="schedule">
            <el-timeline-item v-for="s in schedule" :key="`${s.time}-${s.text}`" :timestamp="s.time" placement="top" color="#2468f2">
              {{ s.text }}
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card shadow="never" class="dashboard-card robot-panel">
          <template #header>
            <div class="card-head">
              <span class="card-title">机器人状态</span>
              <span class="card-meta">{{ onlineRobotCount }}/{{ robotStore.robots.length }} 在线</span>
            </div>
          </template>
          <div v-if="robotStore.robots.length" class="robot-list">
            <div v-for="robot in robotStore.robots" :key="robot.id" class="robot-item">
              <div class="robot-avatar"><el-icon><Monitor /></el-icon></div>
              <div class="robot-copy">
                <div class="robot-head">
                  <strong>{{ robot.name }}</strong>
                  <span>{{ robot.battery }}%</span>
                </div>
                <el-progress :percentage="robot.battery" :stroke-width="7" :show-text="false" :color="batteryColor(robot.battery)" />
              </div>
              <el-tag :type="robotStatusType(robot.status)" size="small" round>{{ robotStatusLabel(robot.status) }}</el-tag>
            </div>
          </div>
          <div v-else class="empty-hint compact">暂无机器人数据</div>
        </el-card>
      </div>
    </section>

    <section class="insight-grid" aria-label="趋势与设备健康">
      <el-card shadow="never" class="dashboard-card trend-panel">
        <template #header>
          <div class="card-head">
            <span class="card-title">近 7 日告警趋势</span>
            <span class="warning-badge">本周告警 {{ weeklyAlarmTotal }} 条</span>
          </div>
        </template>
        <ChartCard :option="alarmChart" :height="220" />
      </el-card>

      <el-card shadow="never" class="dashboard-card health-panel">
        <template #header>
          <div class="card-head">
            <span class="card-title">告警分布与设备健康</span>
            <span class="health-state" :class="{ attention: alarmStore.unacknowledgedCount > 0 }">
              <el-icon><CircleCheckFilled /></el-icon>
              {{ alarmStore.unacknowledgedCount > 0 ? '存在待处理告警' : '数据状态稳定' }}
            </span>
          </div>
        </template>
        <div class="health-layout">
          <div class="alarm-distribution">
            <ChartCard :option="alarmDistributionChart" :height="196" />
          </div>
          <div class="health-metrics">
            <div v-for="item in healthItems" :key="item.label" class="health-item">
              <div class="health-label">
                <span class="health-icon" :style="{ color: item.color, backgroundColor: `${item.color}14` }">
                  <el-icon><component :is="item.icon" /></el-icon>
                </span>
                <span>{{ item.label }}</span>
              </div>
              <el-progress :percentage="item.value" :stroke-width="8" :show-text="false" :color="item.color" />
              <strong>{{ item.value }}%</strong>
            </div>
          </div>
        </div>
      </el-card>
    </section>

    <section class="extension-section" aria-label="扩展运行详情">
      <div class="section-heading">
        <div>
          <span class="section-kicker">运行明细</span>
          <h3>告警与任务</h3>
        </div>
        <span>保留完整告警追踪与任务控制能力</span>
      </div>
      <div class="extension-grid">
        <el-card shadow="never" class="dashboard-card alarms-panel">
          <template #header>
            <div class="card-head">
              <span class="card-title">最新告警</span>
              <el-button text type="primary" size="small" @click="router.push('/alarms')">查看全部</el-button>
            </div>
          </template>
          <el-timeline v-if="recentAlarms.length" class="alarm-timeline">
            <el-timeline-item
              v-for="a in recentAlarms"
              :key="a.id"
              :type="a.severity === 'CRITICAL' ? 'danger' : 'warning'"
              :timestamp="formatTime(a.createdAt)"
              placement="top"
            >
              {{ a.message }}
            </el-timeline-item>
          </el-timeline>
          <div v-else class="empty-hint">暂无告警</div>
        </el-card>

        <el-card shadow="never" class="dashboard-card tasks-panel">
          <template #header>
            <div class="card-head">
              <span class="card-title">当前任务</span>
              <span class="card-meta">{{ activeTasks.length }} 项进行中</span>
            </div>
          </template>
          <el-table :data="activeTasks" size="small" empty-text="暂无进行中的任务">
            <el-table-column prop="name" label="任务名称" min-width="160">
              <template #default="{ row }">
                <el-link type="primary" @click="router.push(`/tasks/${row.id}`)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><TaskStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column label="进度" width="180">
              <template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="9" /></template>
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
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ChartCard from '@/components/ChartCard.vue'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import TaskStatusTag from '@/components/TaskStatusTag.vue'
import { useAnalytics } from '@/composables/useAnalytics'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { DETECTION_LABELS, type Robot } from '@/types'

const router = useRouter()
const { can } = usePermission()
const authStore = useAuthStore()
const { weeklyAlarmCounts, completionRate, completedTasks, robotOnlineRate, totalTasks } = useAnalytics()
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

const activeTaskCount = computed(() =>
  taskStore.tasks.filter((t) => ['RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status)).length,
)

const onlineRobotCount = computed(() => robotStore.robots.filter((robot) => robot.status !== 'OFFLINE').length)

const averageBattery = computed(() => {
  if (!robotStore.robots.length) return 0
  return Math.round(robotStore.robots.reduce((sum, robot) => sum + robot.battery, 0) / robotStore.robots.length)
})

const weeklyAlarmTotal = computed(() => weeklyAlarmCounts.value.reduce((sum, count) => sum + count, 0))

const heroStatuses = computed(() => {
  const siteCount = siteStore.sites.length
  const robotCount = robotStore.robots.length
  const offlineCount = robotCount - onlineRobotCount.value
  return [
    {
      label: siteCount ? `${siteCount} 个站点稳定运行` : '暂无站点接入',
      icon: siteCount ? 'CircleCheckFilled' : 'WarningFilled',
      tone: siteCount ? 'success' : 'warning',
    },
    {
      label: !robotCount ? '暂无机器人接入' : offlineCount ? `${offlineCount} 台机器人离线` : '机器人状态良好',
      icon: 'Monitor',
      tone: !robotCount ? 'info' : offlineCount ? 'warning' : 'success',
    },
    {
      label: activeTaskCount.value ? `${activeTaskCount.value} 项任务持续执行` : '当前暂无运行任务',
      icon: 'Timer',
      tone: activeTaskCount.value ? 'success' : 'info',
    },
  ]
})

const stats = computed(() => [
  {
    label: '站点数量',
    value: siteStore.sites.length,
    trend: `覆盖 ${siteStore.sites.length} 座变电站`,
    up: true,
    tone: 'blue',
    icon: 'OfficeBuilding',
  },
  {
    label: '巡检路线',
    value: routeStore.routes.length,
    trend: `共 ${routeStore.routes.length} 条路线`,
    up: true,
    tone: 'green',
    icon: 'Guide',
  },
  {
    label: '进行中任务',
    value: activeTaskCount.value,
    trend: '任务状态实时更新',
    up: true,
    tone: 'purple',
    icon: 'Tickets',
  },
  {
    label: '未确认告警',
    value: alarmStore.unacknowledgedCount,
    trend: alarmStore.unacknowledgedCount ? '需及时处理' : '暂无待处理',
    up: !alarmStore.unacknowledgedCount,
    tone: 'orange',
    icon: 'WarningFilled',
  },
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
  grid: { top: 26, bottom: 30, left: 36, right: 16 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['6天前', '5天前', '4天前', '3天前', '2天前', '昨天', '今天'],
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#dce5f1' } },
    axisLabel: { color: '#66758c', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#8794a8', fontSize: 11 },
    splitLine: { lineStyle: { color: '#e7edf5', type: 'dashed' } },
  },
  series: [{
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 7,
    data: weeklyAlarmCounts.value,
    areaStyle: { color: 'rgba(255, 108, 24, 0.08)' },
    itemStyle: { color: '#ff6c18', borderColor: '#fff', borderWidth: 2 },
    lineStyle: { color: '#ff6c18', width: 3 },
  }],
}))

const taskChart = computed(() => ({
  series: [{
    type: 'gauge',
    startAngle: 90,
    endAngle: -270,
    min: 0,
    max: 100,
    radius: '86%',
    progress: { show: true, width: 16, roundCap: true, itemStyle: { color: '#2468f2' } },
    axisLine: { lineStyle: { width: 16, color: [[1, '#e7edf8']] } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    anchor: { show: false },
    pointer: { show: false },
    title: { show: true, offsetCenter: [0, '25%'], color: '#748198', fontSize: 12 },
    detail: {
      valueAnimation: true,
      offsetCenter: [0, '-8%'],
      color: '#14233a',
      fontSize: 34,
      fontWeight: 700,
      formatter: '{value}%',
    },
    data: [{ value: completionRate.value, name: `已完成 ${completedTasks.value} 项 / 总计 ${totalTasks.value} 项` }],
  }],
}))

const alarmTypeData = computed(() => {
  const counts: Record<string, number> = {}
  alarmStore.alarms.forEach((alarm) => {
    const label = DETECTION_LABELS[alarm.type]
    counts[label] = (counts[label] ?? 0) + 1
  })
  const entries = Object.entries(counts)
  if (entries.length) return entries
  return Object.values(DETECTION_LABELS).slice(0, 3).map((label) => [label, 0] as [string, number])
})

const alarmDistributionChart = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { top: 18, right: 12, bottom: 42, left: 34 },
  xAxis: {
    type: 'category',
    data: alarmTypeData.value.map(([label]) => label),
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#dce5f1' } },
    axisLabel: { color: '#66758c', fontSize: 10, interval: 0, overflow: 'truncate', width: 56 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#8794a8', fontSize: 10 },
    splitLine: { lineStyle: { color: '#e7edf5', type: 'dashed' } },
  },
  series: [{
    type: 'bar',
    barMaxWidth: 34,
    data: alarmTypeData.value.map(([, value]) => value),
    itemStyle: {
      borderRadius: [5, 5, 0, 0],
      color: (params: { dataIndex: number }) => ['#2468f2', '#12b968', '#ff8a1e', '#20b6b0', '#7c4df3'][params.dataIndex % 5],
    },
    label: { show: true, position: 'top', color: '#526177', fontWeight: 600 },
  }],
}))

const healthItems = computed(() => [
  { label: '机器人在线率', value: robotOnlineRate.value, color: '#12b968', icon: 'Monitor' },
  { label: '任务完成率', value: completionRate.value, color: '#2468f2', icon: 'DataAnalysis' },
  { label: '电量均值', value: averageBattery.value, color: '#ff8a1e', icon: 'Lightning' },
])

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
.dashboard {
  --dashboard-blue: #2468f2;
  --dashboard-green: #12b968;
  --dashboard-orange: #ff6c18;
  --dashboard-text: #15243a;
  --dashboard-muted: #718096;
  --dashboard-line: #e4ebf4;
  display: grid;
  gap: 16px;
}

.dashboard-page-header {
  display: none;
}

.welcome-card {
  position: relative;
  overflow: hidden;
  border: 0;
  border-radius: 16px;
  background:
    radial-gradient(circle at 42% 12%, rgba(23, 201, 213, 0.26), transparent 28%),
    linear-gradient(112deg, #007b82 0%, #006a91 35%, #0757b9 72%, #0646a6 100%);
  box-shadow: 0 10px 28px rgba(5, 73, 142, 0.18);
  color: #fff;
}

.welcome-card :deep(.el-card__body) {
  padding: 0;
}

.welcome-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 148px;
  padding: 22px 26px;
  isolation: isolate;
}

.hero-grid {
  position: absolute;
  z-index: -1;
  inset: 0;
  overflow: hidden;
  opacity: 0.42;
}

.hero-grid::before,
.hero-grid::after {
  position: absolute;
  content: '';
  border: 1px solid rgba(130, 221, 255, 0.25);
  border-radius: 50%;
}

.hero-grid::before {
  width: 540px;
  height: 230px;
  right: -110px;
  bottom: -150px;
}

.hero-grid::after {
  width: 420px;
  height: 180px;
  right: 50px;
  bottom: -134px;
}

.hero-grid span {
  position: absolute;
  width: 58%;
  height: 1px;
  right: -6%;
  top: calc(12% + (var(--line-index) * 12%));
  background: linear-gradient(90deg, transparent, rgba(139, 226, 255, 0.42), transparent);
  transform: rotate(calc(-4deg + (var(--line-index) * 1deg)));
  transform-origin: right center;
}

.hero-tower {
  position: absolute;
  z-index: -1;
  bottom: -2px;
  width: 62px;
  height: 108px;
  border-right: 2px solid rgba(166, 232, 255, 0.45);
  border-left: 2px solid rgba(166, 232, 255, 0.45);
  clip-path: polygon(44% 0, 56% 0, 63% 23%, 82% 100%, 18% 100%, 37% 23%);
  opacity: 0.8;
}

.hero-tower::before,
.hero-tower::after {
  position: absolute;
  left: 4px;
  right: 4px;
  content: '';
  border-top: 2px solid rgba(166, 232, 255, 0.45);
}

.hero-tower::before {
  top: 32px;
  transform: rotate(24deg);
}

.hero-tower::after {
  top: 61px;
  transform: rotate(-24deg);
}

.hero-tower-left {
  right: 27%;
  transform: scale(0.78);
  transform-origin: bottom;
}

.hero-tower-right {
  right: 18%;
}

.welcome-content {
  min-width: 0;
  max-width: 68%;
}

.welcome-text {
  min-width: 0;
}

.welcome-text h2 {
  margin: 0;
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-shadow: 0 2px 10px rgba(0, 43, 91, 0.22);
}

.welcome-text p {
  margin: 7px 0 0;
  overflow: hidden;
  color: rgba(239, 251, 255, 0.88);
  font-size: 13px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 16px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 30px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 4px 12px rgba(4, 53, 110, 0.12);
  color: #3b526d;
  font-size: 12px;
  font-weight: 600;
  backdrop-filter: blur(8px);
}

.status-pill.is-success .el-icon { color: var(--dashboard-green); }
.status-pill.is-warning .el-icon { color: var(--dashboard-orange); }
.status-pill.is-info .el-icon { color: var(--dashboard-blue); }

.welcome-aside {
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 16px;
  max-width: 30%;
}

.online-pill {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid rgba(121, 255, 194, 0.42);
  border-radius: 24px;
  background: linear-gradient(115deg, rgba(17, 192, 107, 0.96), rgba(8, 161, 92, 0.92));
  box-shadow: 0 8px 20px rgba(0, 61, 77, 0.24);
  color: #fff;
  white-space: nowrap;
}

.online-pill .el-icon { font-size: 18px; }
.online-pill strong { font-size: 15px; letter-spacing: 0.02em; }
.online-pill span { font-size: 12px; }

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 2px;
}

.quick-actions :deep(.el-button) {
  margin: 0;
  padding: 5px 7px;
  color: rgba(255, 255, 255, 0.84);
  font-size: 12px;
}

.quick-actions :deep(.el-button:hover) {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.quick-actions :deep(.el-badge__content) {
  top: 0;
  right: -5px;
  border: 0;
  transform: scale(0.78) translateY(-30%);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  --stat-color: var(--dashboard-blue);
  position: relative;
  overflow: hidden;
  min-height: 108px;
  border: 1px solid var(--dashboard-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(31, 61, 101, 0.07);
}

.stat-card::after {
  position: absolute;
  right: -36px;
  bottom: -48px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: var(--stat-color);
  content: '';
  opacity: 0.045;
}

.stat-card--green { --stat-color: #12b968; }
.stat-card--purple { --stat-color: #7046e8; }
.stat-card--orange { --stat-color: #ff5b18; }

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 17px 18px;
}

.stat-icon {
  display: grid;
  flex: 0 0 54px;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 50%;
  background: var(--stat-color);
  box-shadow: 0 8px 16px color-mix(in srgb, var(--stat-color) 22%, transparent);
  color: #fff;
  font-size: 25px;
}

.stat-copy { min-width: 0; }

.stat-card .label {
  color: #526177;
  font-size: 13px;
  font-weight: 600;
}

.stat-card .value {
  margin-top: 1px;
  color: var(--stat-color);
  font-size: 29px;
  font-weight: 750;
  line-height: 1.15;
}

.trend {
  margin-top: 4px;
  overflow: hidden;
  color: #8490a2;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend.up { color: var(--dashboard-green); }
.trend.attention { color: var(--dashboard-orange); }

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.46fr) minmax(245px, 0.72fr) minmax(280px, 0.84fr);
  gap: 16px;
  align-items: stretch;
}

.dashboard-card {
  overflow: hidden;
  border: 1px solid var(--dashboard-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(31, 61, 101, 0.07);
}

.dashboard-card :deep(.el-card__header) {
  padding: 14px 16px;
  border-bottom: 1px solid #edf1f6;
}

.dashboard-card :deep(.el-card__body) { padding: 16px; }

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.card-head > div { min-width: 0; }

.card-title {
  color: var(--dashboard-text);
  font-size: 14px;
  font-weight: 700;
}

.card-subtitle {
  margin-left: 6px;
  color: #40536d;
  font-size: 12px;
  font-weight: 600;
}

.card-subtitle::before {
  margin-right: 6px;
  color: #b6c1cf;
  content: '·';
}

.card-meta {
  flex-shrink: 0;
  color: #8a96a8;
  font-size: 11px;
}

.site-select { width: 184px; }

.site-select :deep(.el-select__wrapper) {
  border-radius: 16px;
  background: #f6f9fd;
  box-shadow: 0 0 0 1px #dce5f0 inset;
}

.map-panel,
.completion-panel,
.side-stack { min-height: 424px; }

.map-panel :deep(.el-card__body) {
  height: 369px;
  padding: 0;
}

.map-stage {
  position: relative;
  height: 100%;
  overflow: hidden;
  background: #eef4ed;
}

.dashboard :deep(.map-container) {
  min-height: 100%;
  border: 0;
  border-radius: 0;
}

.map-placeholder {
  display: grid;
  height: 100%;
  place-content: center;
  gap: 8px;
  background: linear-gradient(45deg, rgba(36, 104, 242, 0.04) 25%, transparent 25%) 0 0 / 30px 30px, #f2f7f4;
  color: #7b899b;
  text-align: center;
}

.map-placeholder .el-icon {
  margin: 0 auto;
  color: var(--dashboard-blue);
  font-size: 28px;
}

.map-legend {
  position: absolute;
  z-index: 500;
  right: 12px;
  bottom: 12px;
  display: flex;
  gap: 12px;
  padding: 7px 11px;
  border: 1px solid rgba(214, 225, 237, 0.9);
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 4px 12px rgba(33, 57, 87, 0.1);
  color: #617087;
  font-size: 10px;
  backdrop-filter: blur(8px);
}

.map-legend span,
.completion-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-dot--online,
.legend-dot--done { background: var(--dashboard-green); }
.legend-dot--checkpoint,
.legend-dot--running { background: var(--dashboard-orange); }
.legend-dot--pending { background: #ff4e4e; }

.completion-panel :deep(.el-card__body) {
  display: flex;
  height: 369px;
  flex-direction: column;
  justify-content: center;
  padding: 8px 12px 14px;
}

.completion-chart { min-height: 0; }

.completion-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  color: #66758c;
  font-size: 10px;
}

.completion-legend span {
  padding: 5px 7px;
  border: 1px solid #e5ebf3;
  border-radius: 7px;
  background: #fff;
}

.side-stack {
  display: grid;
  grid-template-rows: minmax(0, 0.88fr) minmax(0, 1.12fr);
  gap: 16px;
}

.side-stack .dashboard-card { min-height: 0; }

.schedule-panel :deep(.el-card__body) {
  height: 142px;
  padding: 10px 16px 6px;
  overflow: auto;
}

.schedule {
  max-height: none;
  margin: 0;
  padding: 3px 0 0 4px;
  overflow: visible;
}

.schedule :deep(.el-timeline-item) { padding-bottom: 10px; }

.schedule :deep(.el-timeline-item__timestamp) {
  margin-bottom: 2px;
  color: #8190a4;
  font-size: 10px;
}

.schedule :deep(.el-timeline-item__content) {
  overflow: hidden;
  color: #2d405b;
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.robot-panel :deep(.el-card__body) {
  height: 176px;
  padding: 4px 16px;
  overflow: auto;
}

.robot-list { min-width: 0; }

.robot-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 0;
  border-bottom: 1px solid #edf1f6;
}

.robot-item:last-child { border-bottom: 0; }

.robot-avatar {
  display: grid;
  flex: 0 0 27px;
  width: 27px;
  height: 27px;
  place-items: center;
  border-radius: 50%;
  background: #edf4ff;
  color: var(--dashboard-blue);
  font-size: 14px;
}

.robot-copy {
  min-width: 0;
  flex: 1;
}

.robot-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 5px;
  color: #263a55;
  font-size: 10px;
}

.robot-head strong {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.robot-head span {
  flex-shrink: 0;
  color: #53647a;
  font-weight: 700;
}

.robot-item :deep(.el-tag) {
  height: 21px;
  padding: 0 7px;
  font-size: 9px;
}

.empty-hint.compact {
  padding: 48px 10px;
  font-size: 12px;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.96fr) minmax(0, 1.14fr);
  gap: 16px;
}

.warning-badge {
  padding: 4px 9px;
  border-radius: 12px;
  background: #fff4eb;
  color: var(--dashboard-orange);
  font-size: 10px;
  font-weight: 600;
}

.health-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--dashboard-green);
  font-size: 10px;
}

.health-state.attention { color: var(--dashboard-orange); }

.health-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.88fr);
  min-height: 220px;
}

.health-panel :deep(.el-card__body) { padding: 0; }

.alarm-distribution {
  min-width: 0;
  padding: 12px 12px 0;
}

.health-metrics {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 22px;
  padding: 20px 18px;
  border-left: 1px solid #edf1f6;
}

.health-item {
  display: grid;
  grid-template-columns: minmax(112px, 0.8fr) minmax(90px, 1fr) 38px;
  align-items: center;
  gap: 10px;
}

.health-label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #42536a;
  font-size: 11px;
  font-weight: 600;
}

.health-icon {
  display: grid;
  flex: 0 0 25px;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
}

.health-item strong {
  color: #27384f;
  font-size: 11px;
  text-align: right;
}

.extension-section { padding-top: 4px; }

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 0 2px;
}

.section-heading h3 {
  margin: 2px 0 0;
  color: var(--dashboard-text);
  font-size: 17px;
}

.section-heading > span {
  color: #8a96a8;
  font-size: 11px;
}

.section-kicker {
  color: var(--dashboard-blue);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.extension-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.72fr) minmax(0, 1.65fr);
  gap: 16px;
  align-items: stretch;
}

.alarms-panel,
.tasks-panel { min-height: 304px; }

.alarm-timeline {
  max-height: 250px;
  margin: 0;
  padding: 4px 4px 0 6px;
  overflow: auto;
}

.alarm-timeline :deep(.el-timeline-item__timestamp) {
  color: #8b98a9;
  font-size: 10px;
}

.alarm-timeline :deep(.el-timeline-item__content) {
  color: #344860;
  font-size: 12px;
  line-height: 1.45;
}

.tasks-panel :deep(.el-card__body) { padding: 4px 12px 12px; }

.tasks-panel :deep(.el-table) {
  --el-table-border-color: #edf1f6;
  --el-table-header-bg-color: #f7f9fc;
  --el-table-row-hover-bg-color: #f7faff;
  color: #40516a;
}

.tasks-panel :deep(.el-table th.el-table__cell) {
  color: #718096;
  font-size: 11px;
  font-weight: 600;
}

@media (max-width: 1360px) {
  .overview-grid {
    grid-template-columns: minmax(0, 1.4fr) minmax(255px, 0.72fr);
  }

  .side-stack {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: 216px;
    min-height: 0;
  }

  .schedule-panel :deep(.el-card__body),
  .robot-panel :deep(.el-card__body) { height: 162px; }

  .health-layout {
    grid-template-columns: minmax(0, 1fr) minmax(230px, 0.8fr);
  }
}

@media (max-width: 1024px) {
  .welcome-content { max-width: 64%; }
  .welcome-aside { max-width: 34%; }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-grid,
  .insight-grid { grid-template-columns: minmax(0, 1fr); }

  .side-stack { grid-column: auto; }
  .extension-grid { grid-template-columns: minmax(0, 1fr); }

  .alarms-panel,
  .tasks-panel { min-height: 0; }
}

@media (max-width: 760px) {
  .welcome-inner {
    align-items: flex-start;
    min-height: 0;
    flex-direction: column;
    gap: 18px;
    padding: 22px 20px;
  }

  .welcome-content,
  .welcome-aside {
    width: 100%;
    max-width: none;
  }

  .welcome-aside {
    align-items: flex-start;
    gap: 8px;
  }

  .quick-actions { justify-content: flex-start; }
  .hero-tower-left { right: 18%; }
  .hero-tower-right { right: 4%; }

  .side-stack {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: repeat(2, 216px);
  }

  .health-layout { grid-template-columns: minmax(0, 1fr); }

  .health-metrics {
    border-top: 1px solid #edf1f6;
    border-left: 0;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }
}

@media (max-width: 560px) {
  .dashboard { gap: 12px; }

  .stats-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .welcome-text h2 { font-size: 20px; }
  .welcome-text p { white-space: normal; }

  .status-pills {
    align-items: stretch;
    flex-direction: column;
  }

  .status-pill { width: 100%; }
  .online-pill { padding: 0 13px; }

  .overview-grid,
  .insight-grid,
  .extension-grid { gap: 12px; }

  .card-head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .site-select { width: 100%; }
  .map-panel :deep(.el-card__body) { height: 330px; }

  .map-panel,
  .completion-panel { min-height: 385px; }

  .health-item {
    grid-template-columns: minmax(105px, 0.8fr) minmax(70px, 1fr) 34px;
  }
}
</style>
