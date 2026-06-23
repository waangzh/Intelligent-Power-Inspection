const api = require('../../services/index')
const { computeAnalytics } = require('../../utils/analytics')
const { DETECTION_LABELS, ALARM_SEVERITY_LABELS } = require('../../utils/constants')

Page({
  data: {
    kpis: [],
    alarmTrend: [],
    alarmTypeChart: [],
    severityChart: [],
    siteStats: [],
    batteryChart: [],
    taskStack: [],
    completionRate: 0,
    robotOnlineRate: 0,
  },

  onShow() {
    if (!getApp().requireAuth('/pages/statistics/index')) return
    this.load()
  },

  async load() {
    const [sites, routes, tasks, records, alarms, robots] = await Promise.all([
      api.getSites(), api.getRoutes(), api.getTasks(), api.getRecords(), api.getAlarms(), api.getRobots(),
    ])
    const analytics = computeAnalytics({ sites, routes, tasks, records, alarms, robots })
    const typeCounts = {}
    alarms.forEach((a) => {
      const label = DETECTION_LABELS[a.type] || a.type
      typeCounts[label] = (typeCounts[label] || 0) + 1
    })
    const alarmTypeChart = Object.entries(typeCounts).map(([label, value]) => ({ label, value }))
    const severityChart = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({
      label: ALARM_SEVERITY_LABELS[s],
      value: alarms.filter((a) => a.severity === s).length,
      color: { CRITICAL: '#f56c6c', HIGH: '#e6a23c', MEDIUM: '#409eff', LOW: '#909399' }[s],
    }))
    const batteryChart = robots.map((r) => ({ label: r.name.slice(-2), value: r.battery }))
    const statusKeys = ['CREATED', 'RUNNING', 'COMPLETED', 'CANCELLED']
    const taskStack = statusKeys.map((s) => ({
      label: s,
      value: tasks.filter((t) => t.status === s).length,
    }))
    this.setData({
      kpis: analytics.kpis,
      alarmTrend: analytics.weeklyAlarmCounts.map((v, i) => ({ label: `${6 - i}天`, value: v })),
      alarmTypeChart,
      severityChart,
      siteStats: analytics.siteInspectionCounts.map((s) => ({ label: s.site.name, value: s.count })),
      batteryChart,
      taskStack,
      completionRate: analytics.completionRate,
      robotOnlineRate: analytics.robotOnlineRate,
    })
  },
})
