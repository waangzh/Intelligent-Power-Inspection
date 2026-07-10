<template>
  <div class="statistics-page pi-page">
    <PageHeader
      title="统计分析"
      description="巡检任务、告警与设备运行趋势"
      :breadcrumbs="[{ label: '数据中心' }, { label: '统计分析' }]"
    />

    <el-row :gutter="16" class="metric-grid">
      <el-col v-for="(kpi, index) in kpis" :key="kpi.label" :xs="24" :sm="12" :lg="6">
        <el-card class="metric-card" :class="`metric-${kpiMetas[index].tone}`" shadow="never">
          <div class="metric-icon" aria-hidden="true">
            <el-icon :size="27"><component :is="kpiMetas[index].icon" /></el-icon>
          </div>
          <div class="metric-content">
            <div class="kpi-line">
              <strong class="kpi-value">{{ kpi.value }}</strong>
              <span class="kpi-label">{{ kpi.label }}</span>
            </div>
            <div class="kpi-trend" :class="kpi.up ? 'up' : 'down'">
              <el-icon><component :is="kpi.up ? 'Top' : 'Bottom'" /></el-icon>
              <span>{{ kpi.trend }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :lg="13">
        <el-card class="chart-panel" shadow="never">
          <template #header>
            <div class="panel-heading">
              <span>近 7 日告警趋势</span>
              <el-tag size="small" effect="plain">近 7 天</el-tag>
            </div>
          </template>
          <ChartCard :option="alarmTrendOption" :height="248" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="11">
        <el-card class="chart-panel" shadow="never">
          <template #header>任务完成率</template>
          <div class="gauge-wrap">
            <ChartCard :option="taskGaugeOption" :height="248" />
            <el-tag class="gauge-status" type="success" size="small" effect="light">
              {{ completionRate >= 80 ? '进度良好' : completionRate >= 50 ? '稳步推进' : '需要关注' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :md="12" :xl="8">
        <el-card class="chart-panel compact-panel" shadow="never">
          <template #header>告警类型分布</template>
          <ChartCard :option="alarmTypeOption" :height="225" />
          <div class="panel-foot">共 {{ alarmStore.alarms.length }} 条告警记录</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" :xl="8">
        <el-card class="chart-panel compact-panel" shadow="never">
          <template #header>告警级别分布</template>
          <ChartCard :option="severityOption" :height="225" />
          <div class="panel-foot">按严重程度统计当前告警</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="24" :xl="8">
        <el-card class="chart-panel compact-panel battery-panel" shadow="never">
          <template #header>机器人电量</template>
          <div v-if="robotStore.robots.length" class="battery-list">
            <div v-for="robot in robotStore.robots" :key="robot.id" class="battery-item">
              <span class="robot-icon" :class="robot.status === 'OFFLINE' ? 'is-offline' : ''">
                <el-icon><Cpu /></el-icon>
              </span>
              <span class="robot-name">{{ robot.name }}</span>
              <el-progress
                class="battery-progress"
                :percentage="robot.battery"
                :stroke-width="7"
                :show-text="false"
                :color="batteryColor(robot.battery)"
              />
              <strong class="battery-value">{{ robot.battery }}%</strong>
              <el-tag :type="robot.status === 'OFFLINE' ? 'info' : 'success'" size="small" effect="light">
                {{ robot.status === 'OFFLINE' ? '离线' : '在线' }}
              </el-tag>
            </div>
          </div>
          <div v-else class="empty-hint">暂无机器人数据</div>
          <div class="panel-foot">{{ onlineRobotCount }} / {{ robotStore.robots.length }} 在线</div>
        </el-card>
      </el-col>
    </el-row>

    <div class="analysis-strip">
      <span class="analysis-icon"><el-icon><Opportunity /></el-icon></span>
      <strong>分析结论</strong>
      <span class="analysis-copy">{{ analysisSummary }}</span>
      <span class="analysis-time"><el-icon><Clock /></el-icon> 数据实时汇总</span>
    </div>

    <div class="more-heading">
      <div>
        <strong>更多运营统计</strong>
        <span>保留原有站点与任务状态分析能力</span>
      </div>
    </div>
    <el-row :gutter="16" class="more-grid">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-panel" shadow="never">
          <template #header>各站点巡检次数</template>
          <ChartCard :option="siteBarOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-panel" shadow="never">
          <template #header>任务状态堆叠对比</template>
          <ChartCard :option="taskStackOption" :height="280" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ChartCard from '@/components/ChartCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAnalytics } from '@/composables/useAnalytics'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useTaskStore } from '@/stores/task'
import { ALARM_SEVERITY_LABELS, DETECTION_LABELS } from '@/types'

const taskStore = useTaskStore()
const alarmStore = useAlarmStore()
const robotStore = useRobotStore()
const { kpis, weeklyAlarmCounts, siteInspectionCounts, completionRate, completedTasks, totalTasks } = useAnalytics()

const kpiMetas = [
  { icon: 'Tickets', tone: 'blue' },
  { icon: 'Warning', tone: 'orange' },
  { icon: 'PieChart', tone: 'teal' },
  { icon: 'Cpu', tone: 'blue' },
]

const onlineRobotCount = computed(() => robotStore.robots.filter((robot) => robot.status !== 'OFFLINE').length)
const analysisSummary = computed(() => {
  const alarmText = alarmStore.unacknowledgedCount
    ? `当前有 ${alarmStore.unacknowledgedCount} 条告警待确认`
    : '当前无未确认告警'
  const progressText = completionRate.value >= 80
    ? '任务完成率良好'
    : completionRate.value >= 50
      ? '任务正在稳步推进'
      : '任务完成进度需要关注'
  return `${alarmText}；${progressText}，请继续保持巡检计划执行并关注设备运行状态。`
})

function batteryColor(percentage: number) {
  if (percentage >= 60) return '#16b86a'
  if (percentage >= 30) return '#ff9d16'
  return '#ff5733'
}

const alarmTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 0, left: 0, data: ['告警数量（次）'], textStyle: { color: '#64748b', fontSize: 11 } },
  grid: { left: 34, right: 16, top: 38, bottom: 28 },
  xAxis: {
    type: 'category',
    data: ['6天前', '5天前', '4天前', '3天前', '2天前', '昨天', '今天'],
    boundaryGap: false,
    axisLine: { lineStyle: { color: '#dfe6ef' } },
    axisTick: { show: false },
    axisLabel: { color: '#6f7e94', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#6f7e94', fontSize: 11 },
    splitLine: { lineStyle: { color: '#e8edf4', type: 'dashed' } },
  },
  series: [{
    name: '告警数量（次）',
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 7,
    data: weeklyAlarmCounts.value,
    areaStyle: { opacity: 0.08, color: '#ff8a00' },
    itemStyle: { color: '#ff8a00', borderColor: '#fff', borderWidth: 2 },
    lineStyle: { width: 2.5, color: '#ff8a00' },
  }],
}))

const taskGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    startAngle: 205,
    endAngle: -25,
    min: 0,
    max: 100,
    splitNumber: 4,
    center: ['50%', '64%'],
    radius: '88%',
    progress: { show: true, width: 14, roundCap: true, itemStyle: { color: '#2468f2' } },
    axisLine: { lineStyle: { width: 14, color: [[1, '#e8edf5']] }, roundCap: true },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: {
      distance: -42,
      color: '#718096',
      fontSize: 10,
      formatter: (value: number) => `${value}%`,
    },
    pointer: { show: false },
    title: { offsetCenter: [0, '30%'], color: '#718096', fontSize: 12 },
    detail: {
      valueAnimation: true,
      color: '#10213d',
      fontSize: 31,
      fontWeight: 700,
      offsetCenter: [0, '-2%'],
      formatter: '{value}%',
    },
    data: [{ value: completionRate.value, name: `已完成 ${completedTasks.value} / ${totalTasks.value} 项` }],
  }],
}))

const alarmTypeOption = computed(() => {
  const counts: Record<string, number> = {}
  alarmStore.alarms.forEach((a) => {
    const label = DETECTION_LABELS[a.type]
    counts[label] = (counts[label] || 0) + 1
  })
  const data = Object.entries(counts).map(([name, value]) => ({ name, value }))
  return {
    tooltip: { trigger: 'item' },
    color: ['#3478f6', '#ff9f1a', '#27b6ad', '#8b5cf6', '#ef5b5b'],
    legend: { right: 4, top: 'center', orient: 'vertical', type: 'scroll', itemWidth: 8, itemHeight: 8, textStyle: { color: '#64748b', fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['30%', '50%'],
      avoidLabelOverlap: true,
      label: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: data.length ? data : [{ name: '暂无数据', value: 1 }],
    }],
  }
})

const severityOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 34, right: 12, top: 24, bottom: 28 },
  xAxis: {
    type: 'category',
    data: Object.values(ALARM_SEVERITY_LABELS),
    axisLine: { lineStyle: { color: '#dfe6ef' } },
    axisTick: { show: false },
    axisLabel: { color: '#6f7e94', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#6f7e94', fontSize: 11 },
    splitLine: { lineStyle: { color: '#e8edf4', type: 'dashed' } },
  },
  series: [{
    type: 'bar',
    data: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) =>
      alarmStore.alarms.filter((a) => a.severity === s).length,
    ),
    itemStyle: {
      color: (params: { dataIndex: number }) => ['#ff5733', '#ff9d16', '#2468f2', '#12b8aa'][params.dataIndex],
      borderRadius: [4, 4, 0, 0],
    },
    label: { show: true, position: 'top', color: '#40516b', fontWeight: 600 },
    barMaxWidth: 34,
  }],
}))

const siteBarOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 42, right: 18, top: 24, bottom: 40 },
  xAxis: {
    type: 'category',
    data: siteInspectionCounts.value.map((s) => s.site.name.replace('变电站', '')),
    axisLabel: { rotate: 15, color: '#6f7e94' },
    axisLine: { lineStyle: { color: '#dfe6ef' } },
    axisTick: { show: false },
  },
  yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#e8edf4', type: 'dashed' } } },
  series: [{
    type: 'bar',
    data: siteInspectionCounts.value.map((s) => s.count),
    itemStyle: {
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: '#2468f2' },
          { offset: 1, color: '#69a2ff' },
        ],
      },
      borderRadius: [5, 5, 0, 0],
    },
    barWidth: 36,
  }],
}))

const taskStackOption = computed(() => {
  const statuses = [
    { key: 'COMPLETED', label: '已完成', color: '#67c23a' },
    { key: 'RUNNING', label: '进行中', color: '#409eff' },
    { key: 'PAUSED', label: '已暂停', color: '#e6a23c' },
    { key: 'CREATED', label: '待下发', color: '#909399' },
    { key: 'CANCELLED', label: '已取消', color: '#f56c6c' },
  ]
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#64748b' } },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: ['当前任务'], axisLine: { lineStyle: { color: '#dfe6ef' } }, axisTick: { show: false }, axisLabel: { color: '#6f7e94' } },
    yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#e8edf4', type: 'dashed' } } },
    series: statuses.map((s) => ({
      name: s.label,
      type: 'bar',
      stack: 'total',
      data: [taskStore.tasks.filter((t) => t.status === s.key).length],
      itemStyle: { color: s.color, borderRadius: 3 },
      barWidth: 60,
    })),
  }
})
</script>

<style scoped>
.metric-grid > .el-col,
.section-row > .el-col,
.more-grid > .el-col {
  margin-bottom: 16px;
}

.metric-card {
  position: relative;
  min-height: 116px;
}

.metric-card::after {
  position: absolute;
  right: -24px;
  bottom: -34px;
  width: 110px;
  height: 110px;
  border-radius: 28px;
  content: '';
  opacity: 0.7;
  transform: rotate(45deg);
}

:deep(.metric-card .el-card__body) {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 114px;
  padding: 18px 20px;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 54px;
  width: 54px;
  height: 54px;
  border-radius: 14px;
}

.metric-blue .metric-icon {
  color: var(--pi-primary);
  background: var(--pi-primary-soft);
}

.metric-blue::after {
  background: linear-gradient(135deg, transparent, #edf4ff);
}

.metric-orange .metric-icon {
  color: var(--pi-warning);
  background: var(--pi-warning-soft);
}

.metric-orange::after {
  background: linear-gradient(135deg, transparent, #fff4e5);
}

.metric-teal .metric-icon {
  color: var(--pi-info);
  background: var(--pi-info-soft);
}

.metric-teal::after {
  background: linear-gradient(135deg, transparent, #e9faf8);
}

.metric-content {
  min-width: 0;
}

.kpi-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.kpi-value {
  color: var(--pi-text);
  font-size: 29px;
  font-variant-numeric: tabular-nums;
  font-weight: 750;
  line-height: 1;
}

.kpi-label {
  color: var(--pi-text-regular);
  font-size: 13px;
}

.kpi-trend {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  margin-top: 10px;
}

.kpi-trend.up {
  color: var(--pi-success);
}

.kpi-trend.down {
  color: var(--pi-warning);
}

.section-row {
  margin-top: 0;
}

.chart-panel {
  height: 100%;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.gauge-wrap {
  position: relative;
}

.gauge-status {
  position: absolute;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
}

:deep(.compact-panel .el-card__body) {
  padding-bottom: 12px;
}

.panel-foot {
  min-height: 24px;
  margin: 0 -18px -12px;
  padding: 8px 18px 0;
  color: var(--pi-text-muted);
  border-top: 1px solid var(--pi-divider);
  font-size: 11px;
}

.battery-list {
  min-height: 225px;
  padding: 6px 0;
}

.battery-item {
  display: grid;
  grid-template-columns: 30px minmax(94px, 1fr) minmax(90px, 1.4fr) 44px auto;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  border-bottom: 1px solid var(--pi-divider);
}

.battery-item:last-child {
  border-bottom: 0;
}

.robot-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--pi-primary);
  border-radius: 50%;
  background: var(--pi-primary-soft);
}

.robot-icon.is-offline {
  color: #8b97a9;
  background: #f0f2f5;
}

.robot-name {
  overflow: hidden;
  color: var(--pi-text-regular);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.battery-progress {
  width: 100%;
}

.battery-value {
  color: var(--pi-text);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.analysis-strip {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  margin-bottom: 22px;
  padding: 9px 14px;
  color: var(--pi-text-regular);
  border: 1px solid #dce8f7;
  border-radius: 10px;
  background: linear-gradient(90deg, #f5f9ff, #fff);
  box-shadow: var(--pi-shadow-card);
  font-size: 12px;
}

.analysis-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #fff;
  border-radius: 8px;
  background: var(--pi-primary);
}

.analysis-strip strong {
  color: var(--pi-primary-dark);
  white-space: nowrap;
}

.analysis-time {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--pi-text-muted);
  white-space: nowrap;
}

.more-heading {
  margin-bottom: 12px;
}

.more-heading div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.more-heading strong {
  color: var(--pi-text);
  font-size: 15px;
}

.more-heading span {
  color: var(--pi-text-muted);
  font-size: 12px;
}

@media (max-width: 1199px) {
  .analysis-strip {
    grid-template-columns: auto auto 1fr;
  }

  .analysis-time {
    display: none;
  }
}

@media (max-width: 767px) {
  .metric-card {
    min-height: 102px;
  }

  :deep(.metric-card .el-card__body) {
    min-height: 100px;
  }

  .analysis-strip {
    grid-template-columns: auto 1fr;
  }

  .analysis-copy {
    grid-column: 1 / -1;
    line-height: 1.6;
  }

  .battery-item {
    grid-template-columns: 30px minmax(80px, 1fr) minmax(80px, 1.2fr) 40px;
  }

  .battery-item .el-tag {
    display: none;
  }

  .more-heading div {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }
}
</style>
