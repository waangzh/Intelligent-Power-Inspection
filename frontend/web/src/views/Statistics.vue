<template>
  <div>
    <PageHeader
      title="统计分析"
      description="巡检任务、告警与设备运行趋势"
      :breadcrumbs="[{ label: '数据中心' }, { label: '统计分析' }]"
    />

    <el-row :gutter="12" class="statistics-kpis">
      <el-col :xs="24" :sm="12" :lg="6" v-for="(kpi, index) in kpis" :key="kpi.label">
        <el-card :class="['stat-card', `kpi-card-${index}`]" shadow="never">
          <div class="kpi-inner">
            <div class="kpi-icon"><el-icon><component :is="kpiIcons[index]" /></el-icon></div>
            <div>
              <div class="kpi-value">{{ kpi.value }}</div>
              <div class="kpi-label">{{ kpi.label }}</div>
              <div class="kpi-trend" :class="kpi.up ? 'up' : 'down'">{{ kpi.trend }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="statistics-primary">
      <el-col :xs="24" :lg="13">
        <el-card class="analytics-card" shadow="never">
          <template #header>近 7 日告警趋势</template>
          <ChartCard :option="alarmTrendOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="11">
        <el-card class="analytics-card task-completion-card" shadow="never">
          <template #header>任务完成率</template>
          <TaskCompletionGauge
            :percentage="completionRate"
            :completed="completedTasks"
            :total="totalTasks"
            :status-label="taskProgressStatus.label"
            :status-tone="taskProgressStatus.tone"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="statistics-secondary">
      <el-col :xs="24" :md="8">
        <el-card class="analytics-card" shadow="never">
          <template #header>告警类型分布</template>
          <ChartCard :option="alarmTypeOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="analytics-card" shadow="never">
          <template #header>告警级别分布</template>
          <ChartCard :option="severityOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="analytics-card robot-health-card" shadow="never">
          <template #header>机器人运行状态</template>
          <div v-if="robotHealthRows.length" class="robot-health-list">
            <div v-for="robot in robotHealthRows" :key="robot.id" class="robot-health-row">
              <div class="robot-health-avatar" :class="robot.tone"><el-icon><Cpu /></el-icon></div>
              <span class="robot-health-name">{{ robot.name }}</span>
              <el-progress :percentage="robot.score" :show-text="false" :color="robot.color" />
              <strong>{{ robot.score }}%</strong>
              <el-tag :type="robot.online ? 'success' : 'info'" size="small">{{ robot.online ? '在线' : '离线' }}</el-tag>
            </div>
          </div>
          <div v-else class="empty-hint">暂无机器人数据</div>
          <div class="robot-health-footer">{{ robotHealthRows.filter((robot) => robot.online).length }} / {{ robotHealthRows.length }} 在线</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="statistics-details">
      <el-col :xs="24" :lg="12">
        <el-card class="analytics-card" shadow="never">
          <template #header>各站点巡检次数</template>
          <ChartCard :option="siteBarOption" :height="300" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="analytics-card" shadow="never">
          <template #header>任务状态堆叠对比</template>
          <ChartCard :option="taskStackOption" :height="300" />
        </el-card>
      </el-col>
    </el-row>

    <div class="analysis-summary">
      <el-icon><Opportunity /></el-icon>
      <strong>分析结论</strong>
      <span>本周系统运行稳定，建议持续关注告警趋势与任务执行状态。</span>
      <time>数据统计时间：{{ new Date().toLocaleString('zh-CN') }}</time>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ChartCard from '@/components/ChartCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import TaskCompletionGauge from '@/components/TaskCompletionGauge.vue'
import { useAnalytics } from '@/composables/useAnalytics'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useTaskStore } from '@/stores/task'
import { ALARM_SEVERITY_LABELS, DETECTION_LABELS } from '@/types'

const taskStore = useTaskStore()
const alarmStore = useAlarmStore()
const robotStore = useRobotStore()
const { kpis, weeklyAlarmCounts, siteInspectionCounts, completionRate, completedTasks, totalTasks } = useAnalytics()
const kpiIcons = ['Tickets', 'Bell', 'DataAnalysis', 'Cpu']

const taskProgressStatus = computed(() => {
  if (totalTasks.value === 0) return { label: '暂无任务', tone: 'neutral' }
  if (completionRate.value >= 60) return { label: '进度良好', tone: 'good' }
  if (completionRate.value >= 30) return { label: '持续推进', tone: 'watch' }
  return { label: '需要关注', tone: 'alert' }
})

const alarmTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 30, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['6天前', '5天前', '4天前', '3天前', '2天前', '昨天', '今天'],
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{
    type: 'line',
    smooth: true,
    data: weeklyAlarmCounts.value,
    areaStyle: { opacity: 0.2, color: '#e6a23c' },
    itemStyle: { color: '#e6a23c' },
    lineStyle: { width: 3 },
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
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['35%', '60%'],
      roseType: 'area',
      data: data.length ? data : [{ name: '暂无数据', value: 1 }],
    }],
  }
})

const severityOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: Object.values(ALARM_SEVERITY_LABELS) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{
    type: 'bar',
    data: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) =>
      alarmStore.alarms.filter((a) => a.severity === s).length,
    ),
    itemStyle: {
      color: (params: { dataIndex: number }) => ['#f56c6c', '#e6a23c', '#409eff', '#909399'][params.dataIndex],
    },
    barWidth: 36,
  }],
}))

const robotHealthRows = computed(() => robotStore.robots.map((robot, index) => {
  const telemetry = robot.telemetry
  const online = robot.status !== 'OFFLINE'
  const score = !online
    ? 0
    : telemetry?.bridgeReachable === false
      ? 45
      : telemetry?.nav2Status === 'running' || telemetry?.patrolState === 'running'
        ? 92
        : 78
  return {
    id: robot.id,
    name: robot.name,
    online,
    score,
    color: online ? (index === 1 ? '#ff9700' : '#33b947') : '#a6b4c5',
    tone: `robot-tone-${index % 3}`,
  }
}))

const siteBarOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 40 },
  xAxis: {
    type: 'category',
    data: siteInspectionCounts.value.map((s) => s.site.name.replace('变电站', '')),
    axisLabel: { rotate: 15 },
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{
    type: 'bar',
    data: siteInspectionCounts.value.map((s) => s.count),
    itemStyle: {
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: '#1a5fb4' },
          { offset: 1, color: '#4a9eff' },
        ],
      },
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
    legend: { top: 0 },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: ['当前任务'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: statuses.map((s) => ({
      name: s.label,
      type: 'bar',
      stack: 'total',
      data: [taskStore.tasks.filter((t) => t.status === s.key).length],
      itemStyle: { color: s.color },
      barWidth: 60,
    })),
  }
})
</script>

<style scoped>
.statistics-kpis,
.statistics-primary,
.statistics-secondary,
.statistics-details {
  margin-top: 12px;
}

.statistics-kpis :deep(.el-card) {
  min-height: 96px;
  overflow: hidden;
  position: relative;
  isolation: isolate;
  border-color: #e1eaf7;
  background: linear-gradient(135deg, #fff 0%, #f5f9ff 100%);
}

.statistics-kpis :deep(.el-card)::after {
  position: absolute;
  z-index: -1;
  right: -28px;
  bottom: -56px;
  width: 145px;
  height: 145px;
  content: '';
  opacity: 0.5;
  background: linear-gradient(135deg, transparent 50%, #dceaff 50%);
  transform: rotate(-16deg);
}

.kpi-inner {
  display: flex;
  align-items: center;
  gap: 14px;
  position: relative;
  z-index: 1;
}

.kpi-icon {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 14px;
  color: #1768f2;
  background: #e6efff;
  font-size: 27px;
}

.kpi-card-1 :deep(.kpi-icon) { color: #f47a1c; background: #fff1e5; }
.kpi-card-2 :deep(.kpi-icon) { color: #08a99a; background: #e5f8f4; }
.kpi-card-3 :deep(.kpi-icon) { color: #1768f2; background: #e7f0ff; }
.statistics-kpis :deep(.kpi-card-1) { border-color: #f6e4d1; background: linear-gradient(135deg, #fff 0%, #fff7ef 100%); }
.statistics-kpis :deep(.kpi-card-2) { border-color: #dbeeea; background: linear-gradient(135deg, #fff 0%, #f1fbf8 100%); }
.statistics-kpis :deep(.kpi-card-3) { border-color: #dde9fa; background: linear-gradient(135deg, #fff 0%, #f3f8ff 100%); }
.statistics-kpis :deep(.kpi-card-1)::after { background: linear-gradient(135deg, transparent 50%, #ffe7d1 50%); }
.statistics-kpis :deep(.kpi-card-2)::after { background: linear-gradient(135deg, transparent 50%, #d9f4ee 50%); }
.statistics-kpis :deep(.kpi-card-3)::after { background: linear-gradient(135deg, transparent 50%, #dceaff 50%); }

.kpi-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--pi-text);
  line-height: 1;
}

.kpi-label {
  color: var(--pi-muted);
  font-size: 12px;
  margin-top: 4px;
}

.kpi-trend {
  font-size: 12px;
  margin-top: 6px;
}

.kpi-trend.up {
  color: #67c23a;
}

.kpi-trend.down {
  color: #f56c6c;
}

.analytics-card {
  height: 100%;
}

.task-completion-card :deep(.el-card__header) {
  padding: 18px 20px 6px;
  font-size: 17px;
  font-weight: 600;
}

.task-completion-card :deep(.el-card__body) {
  padding: 0 20px 28px;
}

.robot-health-card :deep(.el-card__body) {
  display: flex;
  min-height: 280px;
  flex-direction: column;
}

.robot-health-list {
  display: grid;
  gap: 15px;
  padding: 8px 0 12px;
}

.robot-health-row {
  display: grid;
  grid-template-columns: 30px minmax(78px, 1fr) minmax(60px, 1.4fr) 35px 42px;
  align-items: center;
  gap: 8px;
  color: #536a85;
  font-size: 12px;
}

.robot-health-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 10px;
  color: #1768f2;
  background: #e7f0ff;
  font-size: 17px;
}

.robot-tone-1 { color: #f48016; background: #fff0df; }
.robot-tone-2 { color: #09a99c; background: #e0f8f4; }

.robot-health-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.robot-health-row strong {
  color: #405873;
  text-align: right;
}

.robot-health-row :deep(.el-progress-bar__outer) {
  background: #eaf0f7;
}

.robot-health-row :deep(.el-tag) {
  justify-content: center;
  padding-inline: 5px;
}

.robot-health-footer {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #edf1f6;
  color: #90a0b5;
  font-size: 11px;
}

.analysis-summary {
  display: flex;
  align-items: center;
  min-height: 44px;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  border: 1px solid #dce8fb;
  border-radius: 10px;
  background: linear-gradient(90deg, #f3f8ff, #fff);
  color: #5d708a;
  font-size: 12px;
}

.analysis-summary > .el-icon {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 7px;
  color: #fff;
  background: #1768f2;
}

.analysis-summary strong {
  color: #1768f2;
}

.analysis-summary time {
  margin-left: auto;
  color: #8493a8;
  white-space: nowrap;
}

@media (max-width: 991px) {
  .statistics-primary :deep(.el-col),
  .statistics-secondary :deep(.el-col),
  .statistics-details :deep(.el-col) {
    margin-bottom: 12px;
  }
}

@media (max-width: 640px) {
  .analysis-summary {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .analysis-summary time {
    width: 100%;
    margin-left: 32px;
    white-space: normal;
  }

  .robot-health-row {
    grid-template-columns: 30px minmax(0, 1fr) 82px 34px;
  }

  .robot-health-row :deep(.el-tag) {
    display: none;
  }
}
</style>
