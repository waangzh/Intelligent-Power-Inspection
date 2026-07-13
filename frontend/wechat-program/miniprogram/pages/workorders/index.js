const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const { WORK_ORDER_STATUS_LABELS, WORK_ORDER_PRIORITY_LABELS } = require('../../utils/constants')

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'REVIEW', label: '待复核' },
  { value: 'CLOSED', label: '已关闭' },
]

Page({
  data: {
    orders: [],
    filtered: [],
    statusFilter: '',
    statusCards: [],
    detail: null,
    showDetail: false,
    showResolve: false,
    resolveText: '',
    resolvingId: '',
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/workorders/index')) return
    if (!app.requirePermission('workorder:view')) return
    this.load()
  },

  async load() {
    const orders = (await api.getWorkOrders()).map((o) => ({
      ...o,
      statusLabel: WORK_ORDER_STATUS_LABELS[o.status],
      priorityLabel: WORK_ORDER_PRIORITY_LABELS[o.priority],
      createdLabel: o.createdAt ? o.createdAt.slice(0, 16).replace('T', ' ') : '',
    }))
    const counts = { PENDING: 0, PROCESSING: 0, REVIEW: 0, CLOSED: 0 }
    orders.forEach((o) => { if (counts[o.status] !== undefined) counts[o.status]++ })
    const statusCards = [
      { key: '', label: '全部', value: orders.length },
      { key: 'PENDING', label: '待处理', value: counts.PENDING },
      { key: 'PROCESSING', label: '处理中', value: counts.PROCESSING },
      { key: 'REVIEW', label: '待复核', value: counts.REVIEW },
    ]
    this.setData({ orders, statusCards })
    this.applyFilter()
  },

  filterByCard(e) {
    this.setData({ statusFilter: e.currentTarget.dataset.key })
    this.applyFilter()
  },

  applyFilter() {
    const { orders, statusFilter } = this.data
    this.setData({
      filtered: statusFilter ? orders.filter((o) => o.status === statusFilter) : orders,
    })
  },

  openDetail(e) {
    const detail = this.data.orders.find((o) => o.id === e.currentTarget.dataset.id)
    this.setData({ detail, showDetail: true })
  },

  closeDetail() { this.setData({ showDetail: false }) },

  accept(e) {
    this.claim(e.currentTarget.dataset.id)
  },

  async claim(id) {
    try {
      await api.claimWorkOrder(id)
      wx.showToast({ title: '接单成功' })
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '接单失败', icon: 'none' })
    }
  },

  openResolve(e) {
    this.setData({ resolvingId: e.currentTarget.dataset.id, resolveText: '', showResolve: true })
  },

  closeResolve() { this.setData({ showResolve: false }) },

  onResolveInput(e) { this.setData({ resolveText: e.detail.value }) },

  stop() {},

  async submitResolve() {
    if (!this.data.resolveText.trim()) {
      wx.showToast({ title: '请填写处理说明', icon: 'none' })
      return
    }
    await api.updateWorkOrderStatus(this.data.resolvingId, 'REVIEW', { resolution: this.data.resolveText })
    this.setData({ showResolve: false })
    wx.showToast({ title: '已提交复核' })
    this.load()
  },

  closeOrder(e) {
    this.updateStatus(e.currentTarget.dataset.id, 'CLOSED')
  },

  async updateStatus(id, status, extra) {
    await api.updateWorkOrderStatus(id, status, extra)
    wx.showToast({ title: '状态已更新' })
    this.load()
  },

  goAlarms() {
    wx.switchTab({ url: '/pages/alarms/index' })
  },
})
