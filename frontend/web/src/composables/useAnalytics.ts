import { computed } from 'vue'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useRobotHeartbeatStore } from '@/stores/robotHeartbeat'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'
import { isRobotOnline } from '@/utils/robotStatus'

const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return startOfDay(d)
}

function isInRange(iso: string, start: Date, end: Date) {
  const t = new Date(iso).getTime()
  return t >= start.getTime() && t < end.getTime()
}

function trendText(current: number, previous: number): { text: string; up: boolean } {
  if (previous === 0) {
    return current > 0 ? { text: '较上周 新增', up: true } : { text: '与上周持平', up: true }
  }
  const pct = Math.round(((current - previous) / previous) * 100)
  if (pct === 0) return { text: '与上周持平', up: true }
  return { text: `较上周 ${pct > 0 ? '+' : ''}${pct}%`, up: pct >= 0 }
}

export function useAnalytics() {
  const taskStore = useTaskStore()
  const alarmStore = useAlarmStore()
  const robotStore = useRobotStore()
  const siteStore = useSiteStore()
  const routeStore = useRouteStore()

  const thisWeekStart = daysAgo(6)
  const lastWeekStart = daysAgo(13)
  const lastWeekEnd = thisWeekStart
  const now = new Date()

  const weeklyAlarmCounts = computed(() => {
    const counts = [0, 0, 0, 0, 0, 0, 0]
    alarmStore.alarms.forEach((a) => {
      const d = new Date(a.createdAt)
      const diff = Math.floor((startOfDay(now).getTime() - startOfDay(d).getTime()) / 86400000)
      if (diff >= 0 && diff < 7) counts[6 - diff]++
    })
    return counts
  })

  const alarmTrendByWeekday = computed(() => {
    const counts = [0, 0, 0, 0, 0, 0, 0]
    alarmStore.alarms.forEach((a) => {
      const d = new Date(a.createdAt)
      const day = d.getDay()
      const idx = day === 0 ? 6 : day - 1
      counts[idx]++
    })
    return { labels: WEEKDAY_LABELS, counts }
  })

  const thisWeekAlarms = computed(() =>
    alarmStore.alarms.filter((a) => isInRange(a.createdAt, thisWeekStart, now)).length,
  )
  const lastWeekAlarms = computed(() =>
    alarmStore.alarms.filter((a) => isInRange(a.createdAt, lastWeekStart, lastWeekEnd)).length,
  )

  const totalTasks = computed(() => taskStore.tasks.length + taskStore.records.length)
  const completedTasks = computed(
    () => taskStore.tasks.filter((t) => t.status === 'COMPLETED').length + taskStore.records.length,
  )
  const completionRate = computed(() => {
    const total = taskStore.tasks.length + taskStore.records.length
    if (total === 0) return 0
    return Math.round((completedTasks.value / total) * 100)
  })

  const heartbeatStore = useRobotHeartbeatStore()
  const onlineRobotCount = computed(() => {
    if (heartbeatStore.loaded) return heartbeatStore.onlineCount
    return robotStore.robots.filter((r) => isRobotOnline(r, heartbeatStore.isOnline(r.id))).length
  })
  const robotTotalCount = computed(() => (
    heartbeatStore.loaded ? heartbeatStore.totalCount : robotStore.robots.length
  ))
  const robotOnlineRate = computed(() => {
    const total = robotTotalCount.value
    if (total === 0) return 0
    return Math.round((onlineRobotCount.value / total) * 100)
  })

  const siteInspectionCounts = computed(() =>
    siteStore.sites.map((site) => {
      const routeIds = new Set(routeStore.routes.filter((r) => r.siteId === site.id).map((r) => r.id))
      const fromTasks = taskStore.tasks.filter((t) => routeIds.has(t.routeId) && t.status === 'COMPLETED').length
      const fromRecords = taskStore.records.filter((r) => r.routeName && routeStore.routes.some((rt) => rt.siteId === site.id && rt.name === r.routeName)).length
      return { site, count: fromTasks + fromRecords }
    }),
  )

  const severityCounts = computed(() => {
    const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
    alarmStore.alarms.forEach((a) => counts[a.severity]++)
    return counts
  })

  const kpis = computed(() => {
    const alarmTrend = trendText(thisWeekAlarms.value, lastWeekAlarms.value)
    const taskTrend = trendText(
      taskStore.tasks.filter((t) => isInRange(t.createdAt, thisWeekStart, now)).length,
      taskStore.tasks.filter((t) => isInRange(t.createdAt, lastWeekStart, lastWeekEnd)).length,
    )
    return [
      { label: '累计巡检任务', value: totalTasks.value, trend: taskTrend.text, up: taskTrend.up },
      { label: '本周告警', value: thisWeekAlarms.value, trend: alarmTrend.text, up: !alarmTrend.up },
      { label: '任务完成率', value: `${completionRate.value}%`, trend: `已完成 ${completedTasks.value} 项`, up: completionRate.value >= 80 },
      { label: '机器人在线率', value: `${robotOnlineRate.value}%`, trend: `${onlineRobotCount.value}/${robotTotalCount.value} 在线`, up: robotOnlineRate.value >= 50 },
    ]
  })

  return {
    kpis,
    weeklyAlarmCounts,
    alarmTrendByWeekday,
    completionRate,
    completedTasks,
    robotOnlineRate,
    siteInspectionCounts,
    severityCounts,
    totalTasks,
  }
}
