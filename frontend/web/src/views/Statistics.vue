<template>
  <div>
    <PageHeader
      title="统计分析"
      description="巡检任务、告警与设备运行趋势"
      :breadcrumbs="[{ label: '数据中心' }, { label: '统计分析' }]"
    />

    <el-row :gutter="16">
      <el-col :span="6" v-for="kpi in kpis" :key="kpi.label">
        <el-card class="stat-card" shadow="hover">
          <div class="kpi-value">{{ kpi.value }}</div>
          <div class="kpi-label">{{ kpi.label }}</div>
          <div class="kpi-trend" :class="kpi.up ? 'up' : 'down'">{{ kpi.trend }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>近 7 日告警趋势</template>
          <ChartCard :option="alarmTrendOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>任务完成率</template>
          <ChartCard :option="taskGaugeOption" :height="280" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>告警类型分布</template>
          <ChartCard :option="alarmTypeOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>告警级别分布</template>
          <ChartCard :option="severityOption" :height="280" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>机器人电量</template>
          <ChartCard :option="batteryGaugeOption" :height="280" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>各站点巡检次数</template>
          <ChartCard :option="siteBarOption" :height="300" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>任务状态堆叠对比</template>
          <ChartCard :option="taskStackOption" :height="300" />
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
const { kpis, weeklyAlarmCounts, siteInspectionCounts, completionRate } = useAnalytics()

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

const taskGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    startAngle: 200,
    endAngle: -20,
    min: 0,
    max: 100,
    progress: { show: true, width: 14 },
    axisLine: { lineStyle: { width: 14 } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    detail: {
      valueAnimation: true,
      fontSize: 28,
      offsetCenter: [0, '10%'],
      formatter: '{value}%',
    },
    data: [{ value: completionRate.value }],
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

const batteryGaugeOption = computed(() => ({
  tooltip: { formatter: '{b}: {c}%' },
  series: robotStore.robots.map((r, i) => ({
    type: 'gauge',
    center: [`${(i + 0.5) * (100 / robotStore.robots.length)}%`, '55%'],
    radius: '70%',
    min: 0,
    max: 100,
    startAngle: 200,
    endAngle: -20,
    progress: { show: true, width: 8 },
    axisLine: { lineStyle: { width: 8 } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    title: { offsetCenter: [0, '75%'], fontSize: 11 },
    detail: { fontSize: 14, offsetCenter: [0, '20%'], formatter: '{value}%' },
    data: [{ value: r.battery, name: r.name.replace('机器人-', '') }],
  })),
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
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a5fb4;
}

.kpi-label {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}

.kpi-trend {
  font-size: 12px;
  margin-top: 8px;
}

.kpi-trend.up {
  color: #67c23a;
}

.kpi-trend.down {
  color: #f56c6c;
}
</style>
