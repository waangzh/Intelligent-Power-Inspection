const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const { WORK_ORDER_STATUS_LABELS, WORK_ORDER_PRIORITY_LABELS } = require('../../utils/constants')

const REVIEW_CONCLUSION_OPTIONS = [
  { value: 'RESOLVED', label: '已消缺' },
  { value: 'PARTIALLY_RESOLVED', label: '部分消缺' },
  { value: 'UNRESOLVED', label: '未消缺' },
  { value: 'FALSE_ALARM', label: '误报' },
]

function createReviewForm() {
  return { conclusion: 'RESOLVED', conclusionLabel: '已消缺', onsiteFinding: '', handlingMeasures: '', followUpPlan: '' }
}

function decorateOrder(order) {
  const review = order.review && {
    ...order.review,
    conclusionLabel: REVIEW_CONCLUSION_OPTIONS.find((item) => item.value === order.review.conclusion)?.label || order.review.conclusion,
    submittedLabel: order.review.submittedAt ? order.review.submittedAt.slice(0, 16).replace('T', ' ') : '',
  }
  return {
    ...order,
    review,
    statusLabel: WORK_ORDER_STATUS_LABELS[order.status],
    priorityLabel: WORK_ORDER_PRIORITY_LABELS[order.priority],
    createdLabel: order.createdAt ? order.createdAt.slice(0, 16).replace('T', ' ') : '',
  }
}

Page({
  data: {
    orders: [],
    filtered: [],
    statusFilter: '',
    statusCards: [],
    detail: null,
    showDetail: false,
    showResolve: false,
    reviewConclusionOptions: REVIEW_CONCLUSION_OPTIONS,
    reviewConclusionIndex: 0,
    reviewForm: createReviewForm(),
    requiresFollowUp: false,
    resolvingId: '',
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/workorders/index')) return
    if (!app.requirePermission('workorder:view')) return
    this.load()
  },

  async load() {
    const orders = (await api.getWorkOrders()).map(decorateOrder)
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
    this.setData({ resolvingId: e.currentTarget.dataset.id, reviewConclusionIndex: 0, reviewForm: createReviewForm(), requiresFollowUp: false, showResolve: true })
  },

  closeResolve() { this.setData({ showResolve: false }) },

  onReviewConclusionChange(e) {
    const reviewConclusionIndex = Number(e.detail.value)
    const option = REVIEW_CONCLUSION_OPTIONS[reviewConclusionIndex]
    this.setData({
      reviewConclusionIndex,
      'reviewForm.conclusion': option.value,
      'reviewForm.conclusionLabel': option.label,
      requiresFollowUp: ['PARTIALLY_RESOLVED', 'UNRESOLVED'].includes(option.value),
    })
  },

  onReviewFieldInput(e) {
    this.setData({ [`reviewForm.${e.currentTarget.dataset.field}`]: e.detail.value })
  },

  stop() {},

  async submitResolve() {
    const { conclusion, onsiteFinding, handlingMeasures, followUpPlan } = this.data.reviewForm
    const review = {
      conclusion,
      onsiteFinding: onsiteFinding.trim(),
      handlingMeasures: handlingMeasures.trim(),
      followUpPlan: followUpPlan.trim(),
    }
    if (review.onsiteFinding.length < 10 || review.handlingMeasures.length < 10) {
      wx.showToast({ title: '现场检查情况和处理措施至少填写 10 个字符', icon: 'none' })
      return
    }
    if (this.data.requiresFollowUp && !review.followUpPlan) {
      wx.showToast({ title: '请填写遗留风险与后续计划', icon: 'none' })
      return
    }
    await api.updateWorkOrderStatus(this.data.resolvingId, 'REVIEW', { review })
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
