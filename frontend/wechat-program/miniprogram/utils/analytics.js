function startOfDay(d) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return startOfDay(d)
}

function isInRange(iso, start, end) {
  const t = new Date(iso).getTime()
  return t >= start.getTime() && t < end.getTime()
}

function trendText(current, previous) {
  if (previous === 0) return current > 0 ? { text: '较上周 新增', up: true } : { text: '与上周持平', up: true }
  const pct = Math.round(((current - previous) / previous) * 100)
  if (pct === 0) return { text: '与上周持平', up: true }
  return { text: `较上周 ${pct > 0 ? '+' : ''}${pct}%`, up: pct >= 0 }
}

function computeAnalytics(data) {
  const { tasks, records, alarms, robots, sites, routes } = data
  const now = new Date()
  const thisWeekStart = daysAgo(6)
  const lastWeekStart = daysAgo(13)
  const lastWeekEnd = thisWeekStart

  const weeklyAlarmCounts = [0, 0, 0, 0, 0, 0, 0]
  alarms.forEach((a) => {
    const diff = Math.floor((startOfDay(now).getTime() - startOfDay(new Date(a.createdAt)).getTime()) / 86400000)
    if (diff >= 0 && diff < 7) weeklyAlarmCounts[6 - diff]++
  })

  const thisWeekAlarms = alarms.filter((a) => isInRange(a.createdAt, thisWeekStart, now)).length
  const lastWeekAlarms = alarms.filter((a) => isInRange(a.createdAt, lastWeekStart, lastWeekEnd)).length
  const totalTasks = tasks.length + records.length
  const completedTasks = tasks.filter((t) => t.status === 'COMPLETED').length + records.length
  const completionRate = totalTasks === 0 ? 0 : Math.round((completedTasks / totalTasks) * 100)
  const online = robots.filter((r) => r.status !== 'OFFLINE').length
  const robotOnlineRate = robots.length === 0 ? 0 : Math.round((online / robots.length) * 100)

  const alarmTrend = trendText(thisWeekAlarms, lastWeekAlarms)
  const taskTrend = trendText(
    tasks.filter((t) => isInRange(t.createdAt, thisWeekStart, now)).length,
    tasks.filter((t) => isInRange(t.createdAt, lastWeekStart, lastWeekEnd)).length,
  )

  const kpis = [
    { label: '累计巡检任务', value: totalTasks, trend: taskTrend.text, up: taskTrend.up },
    { label: '本周告警', value: thisWeekAlarms, trend: alarmTrend.text, up: !alarmTrend.up },
    { label: '任务完成率', value: `${completionRate}%`, trend: `已完成 ${completedTasks} 项`, up: completionRate >= 80 },
    { label: '机器人在线率', value: `${robotOnlineRate}%`, trend: `${online}/${robots.length} 在线`, up: robotOnlineRate >= 50 },
  ]

  const siteInspectionCounts = sites.map((site) => {
    const routeIds = new Set(routes.filter((r) => r.siteId === site.id).map((r) => r.id))
    const fromTasks = tasks.filter((t) => routeIds.has(t.routeId) && t.status === 'COMPLETED').length
    const fromRecords = records.filter((r) => routes.some((rt) => rt.siteId === site.id && rt.name === r.routeName)).length
    return { site, count: fromTasks + fromRecords }
  })

  return { kpis, weeklyAlarmCounts, completionRate, completedTasks, robotOnlineRate, siteInspectionCounts }
}

module.exports = { computeAnalytics }
