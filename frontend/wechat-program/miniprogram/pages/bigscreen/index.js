const api = require('../../services/index')
const { computeAnalytics } = require('../../utils/analytics')
const { ALARM_SEVERITY_LABELS } = require('../../utils/constants')

Page({
  data: {
    clock: '',
    kpis: [],
    alarmTrend: [],
    completionRate: 0,
    sites: [],
    siteIndex: 0,
    activeSite: null,
    mapCenter: { lat: 30.2741, lng: 120.1551 },
    displayRoute: null,
    areas: [],
    robotPos: null,
    tickerText: '暂无告警 · 系统运行正常',
    robots: [],
    woStats: [],
    activeTasks: [],
  },

  timer: null,

  onShow() {
    if (!getApp().requireAuth('/pages/bigscreen/index')) return
    this.load()
    this.updateClock()
    this.timer = setInterval(() => this.updateClock(), 1000)
  },

  onHide() {
    if (this.timer) clearInterval(this.timer)
  },

  onUnload() {
    if (this.timer) clearInterval(this.timer)
  },

  updateClock() {
    const now = new Date()
    const clock = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
    this.setData({ clock })
  },

  async load() {
    try {
      const [d, workOrders] = await Promise.all([api.fetchDashboard(), api.getWorkOrders()])
      const analytics = computeAnalytics(d)
      const siteIndex = this.data.siteIndex || 0
      const activeSite = d.sites[siteIndex] || d.sites[0] || null
      const displayRoute = activeSite ? d.routes.find((r) => r.siteId === activeSite.id) || null : null
      const allAreas = await api.getAreas()
      const areas = activeSite ? allAreas.filter((a) => a.siteId === activeSite.id) : []
      const robot = activeSite ? d.robots.find((r) => r.siteId === activeSite.id) : null
      const online = d.robots.filter((r) => r.status !== 'OFFLINE').length
      const kpis = [
        { label: '站点', value: d.sites.length, color: '#64b5ff' },
        { label: '在线机器人', value: online, color: '#67c23a' },
        { label: '未确认告警', value: d.unack, color: '#f56c6c' },
        { label: '在线率', value: `${analytics.robotOnlineRate}%`, color: '#ffd700' },
      ]
      const alarmTrend = analytics.weeklyAlarmCounts.map((v, i) => ({ label: ['一', '二', '三', '四', '五', '六', '日'][i], value: v }))
      const tickerAlarms = d.alarms.slice(0, 10)
      const tickerText = tickerAlarms.length
        ? tickerAlarms.map((a) => `[${ALARM_SEVERITY_LABELS[a.severity]}] ${a.message} · ${a.routeName || ''}`).join('　　')
        : '暂无告警 · 系统运行正常'
      const counts = { PENDING: 0, PROCESSING: 0, REVIEW: 0 }
      workOrders.forEach((o) => { if (counts[o.status] !== undefined) counts[o.status]++ })
      const woStats = [
        { label: '待处理', value: counts.PENDING },
        { label: '处理中', value: counts.PROCESSING },
        { label: '待复核', value: counts.REVIEW },
      ]
      const activeTasks = d.activeTasks.filter((t) => ['RUNNING', 'PAUSED', 'DISPATCHED'].includes(t.status)).slice(0, 4)
      this.setData({
        kpis,
        alarmTrend,
        completionRate: analytics.completionRate,
        sites: d.sites,
        siteIndex: activeSite ? d.sites.findIndex((s) => s.id === activeSite.id) : 0,
        activeSite,
        mapCenter: activeSite?.center || { lat: 30.2741, lng: 120.1551 },
        displayRoute,
        areas,
        robotPos: robot?.position || null,
        tickerText,
        robots: d.robots,
        woStats,
        activeTasks,
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onSiteChange(e) {
    this.setData({ siteIndex: Number(e.detail.value) })
    this.load()
  },

  exitBigscreen() {
    wx.switchTab({ url: '/pages/dashboard/index' })
  },
})
