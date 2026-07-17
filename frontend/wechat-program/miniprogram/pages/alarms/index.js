const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const { syncTabBar } = require('../../utils/tab-page')
const { ALARM_SEVERITY_LABELS, DETECTION_LABELS } = require('../../utils/constants')

const SEVERITY_OPTIONS = [
  { value: '', label: '全部级别' },
  { value: 'CRITICAL', label: '紧急' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
]

Page({
  data: {
    alarms: [],
    filtered: [],
    selected: null,
    keyword: '',
    ackFilter: 'all',
    severityFilter: '',
    severityLabel: '全部级别',
    severityOptions: SEVERITY_OPTIONS,
    stats: { total: 0, unack: 0, critical: 0, high: 0, medium: 0 },
    severityChart: [],
    canAck: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/alarms/index')) return
    syncTabBar(this)
    const perms = app.globalData.permissions
    this.setData({
      canAck: hasPermission(perms, 'alarm:ack'),
    })
    this.load()
    app.refreshBadges()
  },

  async load() {
    try {
      const [alarms, orders] = await Promise.all([
        api.getAlarms(),
        api.getWorkOrders().catch(() => []),
      ])
      const workOrderByAlarm = {}
      orders.forEach((o) => {
        if (o.alarmId) workOrderByAlarm[o.alarmId] = o
      })
      const enriched = alarms.map((a) => ({
        ...a,
        severityLabel: ALARM_SEVERITY_LABELS[a.severity],
        typeLabel: DETECTION_LABELS[a.type] || a.type,
        sevType: a.severity === 'CRITICAL' ? 'danger' : a.severity === 'HIGH' ? 'warning' : 'info',
        time: a.createdAt ? a.createdAt.slice(0, 16).replace('T', ' ') : '',
        hasWorkOrder: !!workOrderByAlarm[a.id],
        workOrderLabel: workOrderByAlarm[a.id]?.autoConverted ? '已自动转工单' : '已关联工单',
      }))
      const stats = {
        total: enriched.length,
        unack: enriched.filter((a) => !a.acknowledged).length,
        critical: enriched.filter((a) => a.severity === 'CRITICAL').length,
        high: enriched.filter((a) => a.severity === 'HIGH').length,
        medium: enriched.filter((a) => a.severity === 'MEDIUM').length,
      }
      const severityChart = [
        { label: '紧急', value: stats.critical, color: '#f56c6c' },
        { label: '高', value: stats.high, color: '#e6a23c' },
        { label: '中', value: stats.medium, color: '#409eff' },
      ]
      this.setData({
        alarms: enriched,
        stats,
        severityChart,
        selected: this.data.selected || enriched[0] || null,
      })
      this.applyFilter()
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
    this.applyFilter()
  },

  setAckFilter(e) {
    this.setData({ ackFilter: e.currentTarget.dataset.v })
    this.applyFilter()
  },

  applyFilter() {
    const { alarms, severityFilter, keyword, ackFilter } = this.data
    let list = alarms
    if (ackFilter === 'pending') list = list.filter((a) => !a.acknowledged)
    if (severityFilter) list = list.filter((a) => a.severity === severityFilter)
    if (keyword.trim()) list = list.filter((a) => a.message.includes(keyword.trim()))
    this.setData({ filtered: list })
  },

  onSeverityChange(e) {
    const opt = SEVERITY_OPTIONS[e.detail.value]
    this.setData({ severityFilter: opt.value, severityLabel: opt.label })
    this.applyFilter()
  },

  selectAlarm(e) {
    const alarm = this.data.alarms.find((a) => a.id === e.currentTarget.dataset.id)
    this.setData({ selected: alarm })
  },

  async ackOne(e) {
    const id = e.currentTarget.dataset.id
    await api.acknowledgeAlarm(id)
    wx.showToast({ title: '已确认' })
    this.load()
    getApp().refreshBadges()
  },

  async ackAll() {
    wx.showModal({
      title: '全部确认',
      content: '确认将所有告警标记为已确认？',
      success: async (res) => {
        if (!res.confirm) return
        await api.acknowledgeAllAlarms()
        this.load()
        getApp().refreshBadges()
      },
    })
  },

  stop() {},
})
