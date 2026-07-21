const api = require('../../services/index')
const { syncTabBar, openPage } = require('../../utils/tab-page')
const { ALARM_SEVERITY_LABELS } = require('../../utils/constants')

Page({
  data: {
    user: null,
    isDispatcher: false,
    isViewer: false,
    greeting: '',
    stats: [],
    recentAlarms: [],
    schedule: [],
    unack: 0,
    alarmChartData: [],
    completionRate: 0,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/dashboard/index')) return
    syncTabBar(this)
    const user = app.globalData.user
    const h = new Date().getHours()
    this.setData({
      user,
      isDispatcher: user?.role === 'DISPATCHER',
      isViewer: user?.role === 'VIEWER',
      greeting: h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好',
    })
    this.load()
    app.refreshBadges()
  },

  async load() {
    const overview = await api.fetchDashboard()
    const { counts, rates, weeklyAlarmCounts, recentAlarms, activeTaskItems, robotItems } = overview

    const alarmChartData = (weeklyAlarmCounts || []).map((v, i) => ({
      label: `${6 - i}天`,
      value: v,
    }))

    const robotName = (id) => (robotItems || []).find((r) => r.id === id)?.name || '机器人'
    const active = activeTaskItems || []
    const schedule = active.length
      ? active.slice(0, 4).map((t) => ({
        time: new Date(t.startedAt || t.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        text: `${t.name}（${robotName(t.robotId)}）`,
      }))
      : []

    const unack = counts.unacknowledgedAlarms || 0
    const stats = [
      { label: '站点数量', value: counts.sites, trend: `覆盖 ${counts.sites} 座变电站`, up: true },
      { label: '巡检路线', value: counts.routes, trend: `共 ${counts.routes} 条路线`, up: true },
      { label: '进行中任务', value: counts.activeTasks, trend: '实时更新', up: true },
      { label: '未确认告警', value: unack, trend: unack ? '需及时处理' : '暂无待处理', up: !unack },
    ]

    this.setData({
      stats,
      recentAlarms: (recentAlarms || []).slice(0, 5).map((a) => ({
        ...a,
        severityLabel: ALARM_SEVERITY_LABELS[a.severity],
        sevType: a.severity === 'CRITICAL' ? 'danger' : 'warning',
      })),
      unack,
      schedule,
      alarmChartData,
      completionRate: rates.taskCompletion || 0,
    })
  },

  go(e) {
    openPage(e.currentTarget.dataset.url)
  },
})
