const api = require('../../services/index')
const { syncTabBar, openPage, refreshTabBarBadges } = require('../../utils/tab-page')
const { ALARM_SEVERITY_LABELS } = require('../../utils/constants')
const { formatDateTimeShort, formatTimeShort } = require('../../utils/date-time')
const { formatBusinessMessage } = require('../../utils/display-text')

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
    loading: false,
    loadError: '',
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/dashboard/index')) return
    syncTabBar(this)
    const { resolveSession } = require('../../utils/session-user')
    const { user, role } = resolveSession()
    const h = new Date().getHours()
    this.setData({
      user,
      isDispatcher: role === 'DISPATCHER',
      isViewer: role === 'VIEWER',
      greeting: h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好',
    })
    this.load()
    refreshTabBarBadges(this)
  },

  async load() {
    this.setData({ loading: true, loadError: '' })
    try {
      const overview = await api.fetchDashboard()
      const { counts, rates, weeklyAlarmCounts, recentAlarms, activeTaskItems, robotItems } = overview

    const alarmChartData = (weeklyAlarmCounts || []).map((v, i) => {
      const d = new Date()
      d.setDate(d.getDate() - (6 - i))
      const label = i === 6 ? '今' : `${d.getMonth() + 1}/${d.getDate()}`
      return { label, value: Number(v) || 0 }
    })

    const robotName = (id) => (robotItems || []).find((r) => r.id === id)?.name || '机器人'
    const active = activeTaskItems || []
    const schedule = active.length
      ? active.slice(0, 4).map((t) => ({
        time: formatTimeShort(t.startedAt || t.createdAt),
        text: `${t.name}（${robotName(t.robotId)}）`,
      }))
      : []

    const unack = counts.unacknowledgedAlarms || 0
    const stats = [
      { label: '站点数量', value: counts.sites, trend: `覆盖 ${counts.sites} 座变电站`, up: true },
      { label: '巡检路线', value: counts.routes, trend: `共 ${counts.routes} 条路线`, up: true },
      { label: '进行中任务', value: counts.activeTasks, trend: '实时更新', up: true },
    ]
    if (!this.data.isDispatcher) {
      stats.push({ label: '未确认告警', value: unack, trend: unack ? '需及时处理' : '暂无待处理', up: !unack })
    } else {
      stats.push({ label: '工单处置', value: '—', trend: '请至工单 Tab 接单', up: true })
    }

      this.setData({
        stats,
        recentAlarms: (recentAlarms || []).slice(0, 5).map((a) => ({
          ...a,
          message: formatBusinessMessage(a.message),
          severityLabel: ALARM_SEVERITY_LABELS[a.severity],
          sevType: a.severity === 'CRITICAL' ? 'danger' : 'warning',
        })),
        unack,
        schedule,
        alarmChartData,
        completionRate: rates.taskCompletion || 0,
      })
    } catch (err) {
      this.setData({ loadError: err.message || '加载失败，请检查后端服务与登录状态' })
      wx.showToast({ title: this.data.loadError, icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  go(e) {
    openPage(e.currentTarget.dataset.url)
  },
})
