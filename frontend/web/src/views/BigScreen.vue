<template>
  <div class="bigscreen">
    <header class="bs-header">
      <div class="bs-title">
        <el-icon :size="28" color="#ffd700"><Lightning /></el-icon>
        <h1>电力智能巡检 · 集控中心</h1>
      </div>
      <div class="bs-clock">{{ clock }}</div>
      <div class="bs-actions">
        <el-button text class="bs-btn" @click="router.push('/dashboard')">退出大屏</el-button>
      </div>
    </header>

    <div class="bs-body">
      <div class="bs-col bs-left">
        <div class="bs-panel">
          <div class="panel-title">运行 KPI</div>
          <div class="kpi-grid">
            <div class="kpi" v-for="k in kpis" :key="k.label">
              <div class="kpi-val" :style="{ color: k.color }">{{ k.value }}</div>
              <div class="kpi-lbl">{{ k.label }}</div>
            </div>
          </div>
        </div>
        <div class="bs-panel">
          <div class="panel-title">告警趋势（7日）</div>
          <ChartCard :option="alarmChart" :height="200" />
        </div>
        <div class="bs-panel">
          <div class="panel-title">任务完成率</div>
          <ChartCard :option="gaugeChart" :height="180" />
        </div>
      </div>

      <div class="bs-col bs-center">
        <div class="bs-panel map-panel">
          <div class="panel-title">站点态势 · {{ activeSite?.name }}</div>
          <div class="map-wrap">
            <Map2D
              v-if="activeSite"
              :center="activeSite.center"
              :areas="siteStore.getAreasBySite(selectedSiteId)"
              :route="displayRoute"
              :robot-position="robotPosition"
            />
          </div>
        </div>
        <div class="bs-panel ticker-panel">
          <div class="panel-title">实时告警滚动</div>
          <div class="ticker-wrap">
            <div class="ticker" :style="{ animationDuration: `${Math.max(25, tickerAlarms.length * 5)}s` }">
              <span class="ticker-item">{{ tickerText }}</span>
              <span class="ticker-item">{{ tickerText }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="bs-col bs-right">
        <div class="bs-panel">
          <div class="panel-title">机器人状态</div>
          <div v-for="r in robotStore.robots" :key="r.id" class="robot-row">
            <span>{{ r.name }}</span>
            <el-tag size="small" :type="r.status === 'ONLINE' ? 'success' : 'info'">{{ r.status }}</el-tag>
            <el-progress :percentage="r.battery" :stroke-width="6" style="width: 80px" />
          </div>
        </div>
        <div class="bs-panel">
          <div class="panel-title">工单概况</div>
          <div class="wo-stats">
            <div v-for="s in woStats" :key="s.label" class="wo-stat">
              <span class="wo-val">{{ s.value }}</span>
              <span class="wo-lbl">{{ s.label }}</span>
            </div>
          </div>
        </div>
        <div class="bs-panel">
          <div class="panel-title">进行中任务</div>
          <div v-for="t in activeTasks" :key="t.id" class="task-row">
            <span class="task-name">{{ t.name }}</span>
            <el-progress :percentage="t.progress" :stroke-width="6" />
          </div>
          <div v-if="!activeTasks.length" class="empty-mini">暂无进行中任务</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ChartCard from '@/components/ChartCard.vue'
import Map2D from '@/components/Map2D.vue'
import { useAnalytics } from '@/composables/useAnalytics'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { useWorkOrderStore } from '@/stores/workOrder'
import { ALARM_SEVERITY_LABELS } from '@/types'

const router = useRouter()
const siteStore = useSiteStore()
const routeStore = useRouteStore()
const taskStore = useTaskStore()
const robotStore = useRobotStore()
const alarmStore = useAlarmStore()
const workOrderStore = useWorkOrderStore()
const { weeklyAlarmCounts, completionRate, robotOnlineRate } = useAnalytics()

const clock = ref('')
const selectedSiteId = ref(siteStore.sites[0]?.id ?? '')
let timer: ReturnType<typeof setInterval> | null = null

const activeSite = computed(() => siteStore.getSiteById(selectedSiteId.value))
const displayRoute = computed(() => routeStore.getRoutesBySite(selectedSiteId.value)[0] ?? null)
const robotPosition = computed(() => robotStore.robots.find((r) => r.siteId === selectedSiteId.value)?.position ?? null)

const kpis = computed(() => [
  { label: '站点', value: siteStore.sites.length, color: '#64b5ff' },
  { label: '在线机器人', value: robotStore.robots.filter((r) => r.status !== 'OFFLINE').length, color: '#67c23a' },
  { label: '未确认告警', value: alarmStore.unacknowledgedCount, color: '#f56c6c' },
  { label: '在线率', value: `${robotOnlineRate.value}%`, color: '#ffd700' },
])

const alarmChart = computed(() => ({
  backgroundColor: 'transparent',
  grid: { top: 20, bottom: 24, left: 36, right: 12 },
  xAxis: { type: 'category', data: ['一', '二', '三', '四', '五', '六', '日'], axisLabel: { color: '#8ab4d9' }, axisLine: { lineStyle: { color: '#2a4a6b' } } },
  yAxis: { type: 'value', axisLabel: { color: '#8ab4d9' }, splitLine: { lineStyle: { color: '#1e3a5f' } } },
  series: [{ type: 'bar', data: weeklyAlarmCounts.value, itemStyle: { color: '#e6a23c' } }],
}))

const gaugeChart = computed(() => ({
  backgroundColor: 'transparent',
  series: [{
    type: 'gauge',
    startAngle: 200,
    endAngle: -20,
    min: 0,
    max: 100,
    radius: '85%',
    center: ['50%', '58%'],
    progress: {
      show: true,
      width: 12,
      itemStyle: { color: '#64b5ff' },
    },
    axisLine: {
      lineStyle: {
        width: 12,
        color: [[1, '#2a4a6b']],
      },
    },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    detail: {
      valueAnimation: true,
      fontSize: 24,
      color: '#64b5ff',
      offsetCenter: [0, '12%'],
      formatter: '{value}%',
    },
    data: [{ value: completionRate.value }],
  }],
}))

const tickerAlarms = computed(() => alarmStore.alarms.slice(0, 10))

const tickerText = computed(() => {
  if (!tickerAlarms.value.length) return '暂无告警 · 系统运行正常'
  return tickerAlarms.value
    .map((a) => `[${ALARM_SEVERITY_LABELS[a.severity]}] ${a.message} · ${a.routeName}`)
    .join('　　　')
})

const woStats = computed(() => {
  const c = workOrderStore.statusCounts
  return [
    { label: '待处理', value: c.PENDING },
    { label: '处理中', value: c.PROCESSING },
    { label: '待复核', value: c.REVIEW },
  ]
})

const activeTasks = computed(() =>
  taskStore.tasks.filter((t) => ['RUNNING', 'PAUSED', 'DISPATCHED'].includes(t.status)).slice(0, 4),
)

function tickClock() {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  tickClock()
  timer = setInterval(tickClock, 1000)
  document.body.style.overflow = 'hidden'
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.bigscreen {
  min-height: 100vh;
  background: linear-gradient(180deg, #061018 0%, #0c2240 50%, #061018 100%);
  color: #e8f0fa;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 16px;
  box-sizing: border-box;
}

.bs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-bottom: 1px solid rgba(100, 181, 255, 0.2);
  margin-bottom: 12px;
}

.bs-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bs-title h1 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 4px;
  background: linear-gradient(90deg, #fff, #64b5ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.bs-clock {
  font-size: 16px;
  color: #64b5ff;
  font-variant-numeric: tabular-nums;
}

.bs-btn {
  color: #8ab4d9 !important;
}

.bs-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1.4fr 1fr;
  gap: 12px;
  min-height: 0;
}

.bs-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.bs-panel {
  background: rgba(12, 34, 64, 0.6);
  border: 1px solid rgba(100, 181, 255, 0.15);
  border-radius: 8px;
  padding: 12px 14px;
  flex: 1;
  min-height: 0;
}

.map-panel {
  flex: 2;
}

.panel-title {
  font-size: 13px;
  color: #64b5ff;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #ffd700;
}

.kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.kpi {
  text-align: center;
  padding: 10px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
}

.kpi-val {
  font-size: 26px;
  font-weight: 700;
}

.kpi-lbl {
  font-size: 11px;
  color: #8ab4d9;
  margin-top: 4px;
}

.map-wrap {
  height: calc(100% - 30px);
  min-height: 280px;
  border-radius: 6px;
  overflow: hidden;
}

.ticker-wrap {
  overflow: hidden;
  height: 36px;
}

.ticker {
  display: inline-flex;
  gap: 48px;
  white-space: nowrap;
  animation: ticker-scroll linear infinite;
}

.ticker-item {
  font-size: 13px;
  color: #e6a23c;
}

@keyframes ticker-scroll {
  0% { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

.robot-row,
.task-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(100, 181, 255, 0.1);
  font-size: 12px;
}

.task-name {
  width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wo-stats {
  display: flex;
  justify-content: space-around;
  text-align: center;
}

.wo-val {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: #ffd700;
}

.wo-lbl {
  font-size: 11px;
  color: #8ab4d9;
}

.empty-mini {
  font-size: 12px;
  color: #6b7c93;
  text-align: center;
  padding: 16px;
}
</style>
