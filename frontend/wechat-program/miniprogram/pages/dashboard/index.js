const api = require('../../services/index')
const { computeAnalytics } = require('../../utils/analytics')
const { hasPermission } = require('../../utils/permission')
const { ALARM_SEVERITY_LABELS } = require('../../utils/constants')

Page({
  data: {
    user: null,
    greeting: '',
    stats: [],
    recentAlarms: [],
    activeTasks: [],
    robots: [],
    schedule: [],
    unack: 0,
    canControl: false,
    sites: [],
    siteIndex: 0,
    activeSite: null,
    displayRoute: null,
    robotPosition: null,
    alarmChartData: [],
    completionRate: 0,
    prefsLoaded: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/dashboard/index')) return
    const user = app.globalData.user
    const h = new Date().getHours()
    this.setData({
      user,
      greeting: h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好',
      canControl: hasPermission(user.role, 'task:control'),
      prefsLoaded: false,
    })
    this.load()
    app.refreshBadges()
  },

  async load() {
    const [d, prefs] = await Promise.all([api.fetchDashboard(), api.getPreferences()])
    const analytics = computeAnalytics(d)
    const alarmChartData = analytics.weeklyAlarmCounts.map((v, i) => ({
      label: `${6 - i}天`,
      value: v,
    }))
    const robotName = (id) => d.robots.find((r) => r.id === id)?.name || '机器人'
    const active = d.tasks.filter((t) => ['DISPATCHED', 'RUNNING', 'CREATED'].includes(t.status))
    const schedule = active.length
      ? active.slice(0, 4).map((t) => ({
        time: new Date(t.startedAt || t.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        text: `${t.name}（${robotName(t.robotId)}）`,
      }))
      : [
        { time: '08:00', text: '主变区例行巡检（机器人-A1）' },
        { time: '10:30', text: 'GIS 专项巡检（机器人-B2）' },
        { time: '14:00', text: '电容器组巡检（机器人-C3）' },
        { time: '16:00', text: '夜间预检任务待命' },
      ]
    const runningCount = d.tasks.filter((t) =>
      ['RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status)).length
    const stats = [
      { label: '站点数量', value: d.sites.length, trend: `覆盖 ${d.sites.length} 座变电站`, up: true },
      { label: '巡检路线', value: d.routes.length, trend: `共 ${d.routes.length} 条路线`, up: true },
      { label: '进行中任务', value: runningCount, trend: '实时更新', up: true },
      { label: '未确认告警', value: d.unack, trend: d.unack ? '需及时处理' : '暂无待处理', up: !d.unack },
    ]
    let siteIndex = this.data.siteIndex || 0
    if (!this.data.prefsLoaded && prefs.defaultSiteId) {
      const idx = d.sites.findIndex((s) => s.id === prefs.defaultSiteId)
      if (idx >= 0) siteIndex = idx
    }
    const activeSite = d.sites[siteIndex] || d.sites[0]
    const activeTask = d.activeTasks[0]
    const displayRoute = activeTask
      ? d.routes.find((r) => r.id === activeTask.routeId)
      : d.routes.find((r) => r.siteId === activeSite?.id)
    const robotPosition = activeTask
      ? d.robots.find((r) => r.id === activeTask.robotId)?.position
      : null
    this.setData({
      stats,
      recentAlarms: d.alarms.slice(0, 5).map((a) => ({
        ...a,
        severityLabel: ALARM_SEVERITY_LABELS[a.severity],
        sevType: a.severity === 'CRITICAL' ? 'danger' : 'warning',
      })),
      activeTasks: d.activeTasks,
      robots: d.robots.map((r) => ({
        ...r,
        statusType: r.status === 'ONLINE' ? 'success' : r.status === 'BUSY' ? 'warning' : 'info',
      })),
      unack: d.unack,
      schedule,
      alarmChartData,
      completionRate: analytics.completionRate,
      sites: d.sites,
      siteIndex: activeSite ? d.sites.findIndex((s) => s.id === activeSite.id) : 0,
      activeSite,
      displayRoute: displayRoute || null,
      robotPosition,
      prefsLoaded: true,
    })
  },

  onSiteChange(e) {
    this.setData({ siteIndex: Number(e.detail.value), prefsLoaded: true })
    this.load()
  },

  go(e) {
    const url = e.currentTarget.dataset.url
    const tabs = ['/pages/dashboard/index', '/pages/monitor/index', '/pages/alarms/index', '/pages/tasks/index', '/pages/profile/info/index']
    if (tabs.some((t) => url.startsWith(t.replace('/index', '')))) wx.switchTab({ url: url.split('?')[0] })
    else wx.navigateTo({ url })
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/tasks/detail/index?id=${e.currentTarget.dataset.id}` })
  },

  async pauseTask(e) { await api.pauseTask(e.currentTarget.dataset.id); this.load() },
  async resumeTask(e) { await api.resumeTask(e.currentTarget.dataset.id); this.load() },
  async takeoverTask(e) {
    await api.takeoverTask(e.currentTarget.dataset.id)
    this.load()
  },
})
