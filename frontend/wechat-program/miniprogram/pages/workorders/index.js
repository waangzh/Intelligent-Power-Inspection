const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const workOrderPerm = require('../../utils/work-order-permission')
const { WORK_ORDER_STATUS_LABELS, WORK_ORDER_PRIORITY_LABELS } = require('../../utils/constants')
const {
  FAULT_TYPE_OPTIONS,
  HANDLING_METHOD_OPTIONS,
  enrichWorkOrder,
} = require('../../utils/work-order')

const EMPTY_RESOLVE_FORM = {
  faultType: '',
  faultTypeIndex: -1,
  handlingMethod: '',
  handlingMethodIndex: -1,
  replacedParts: '',
  testResult: '',
  remarks: '',
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
    showReview: false,
    resolvingId: '',
    reviewingId: '',
    resolveForm: { ...EMPTY_RESOLVE_FORM },
    reviewForm: { result: 'PASS', comment: '' },
    faultTypeOptions: FAULT_TYPE_OPTIONS,
    handlingMethodOptions: HANDLING_METHOD_OPTIONS,
    canCreate: false,
    canAssign: false,
    canProcess: false,
    canReview: false,
    user: null,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/workorders/index')) return
    if (!app.requirePermission('workorder:view')) return
    const user = app.globalData.user
    this.setData({
      user,
      canCreate: hasPermission(user.role, 'workorder:create'),
      canAssign: hasPermission(user.role, 'workorder:assign'),
      canProcess: hasPermission(user.role, 'workorder:process'),
      canReview: hasPermission(user.role, 'workorder:review'),
    })
    this.load()
  },

  async load() {
    const user = this.data.user || getApp().globalData.user
    let orders = await api.getWorkOrders()
    if (user.role === 'DISPATCHER') {
      orders = orders.filter((o) => workOrderPerm.isWorkOrderAssignee(o, user))
    }
    orders = orders.map((o) => {
      const enriched = enrichWorkOrder({
        ...o,
        statusLabel: WORK_ORDER_STATUS_LABELS[o.status],
        priorityLabel: WORK_ORDER_PRIORITY_LABELS[o.priority],
        createdLabel: o.createdAt ? o.createdAt.slice(0, 16).replace('T', ' ') : '',
      })
      return {
        ...enriched,
        canAccept: workOrderPerm.canAcceptOrder(o, user),
        canSubmitReview: workOrderPerm.canSubmitReview(o, user),
        canConfirmReview: workOrderPerm.canConfirmReview(o, user),
      }
    })
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
    const order = this.data.orders.find((o) => o.id === e.currentTarget.dataset.id)
    this.setData({ detail: order, showDetail: true })
  },

  closeDetail() { this.setData({ showDetail: false }) },

  async accept(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    if (!workOrderPerm.canAcceptOrder(order, this.data.user)) {
      wx.showToast({ title: '无接单权限', icon: 'none' })
      return
    }
    try {
      await api.updateWorkOrderStatus(id, 'PROCESSING')
      wx.showToast({ title: '状态已更新' })
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  openResolve(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    if (!workOrderPerm.canSubmitReview(order, this.data.user)) {
      wx.showToast({ title: '仅指派调度员可提交复核', icon: 'none' })
      return
    }
    this.setData({
      resolvingId: id,
      resolveForm: { ...EMPTY_RESOLVE_FORM },
      showResolve: true,
    })
  },

  closeResolve() { this.setData({ showResolve: false }) },

  onFaultTypeChange(e) {
    const index = Number(e.detail.value)
    this.setData({
      'resolveForm.faultTypeIndex': index,
      'resolveForm.faultType': FAULT_TYPE_OPTIONS[index],
    })
  },

  onHandlingMethodChange(e) {
    const index = Number(e.detail.value)
    this.setData({
      'resolveForm.handlingMethodIndex': index,
      'resolveForm.handlingMethod': HANDLING_METHOD_OPTIONS[index],
    })
  },

  onResolveFieldInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`resolveForm.${field}`]: e.detail.value })
  },

  openReview(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    if (!workOrderPerm.canConfirmReview(order, this.data.user)) {
      wx.showToast({ title: '仅管理员可确认复核', icon: 'none' })
      return
    }
    this.setData({
      reviewingId: id,
      reviewForm: { result: 'PASS', comment: '' },
      showReview: true,
    })
  },

  closeReview() { this.setData({ showReview: false }) },

  setReviewResult(e) {
    this.setData({ 'reviewForm.result': e.currentTarget.dataset.v })
  },

  onReviewCommentInput(e) {
    this.setData({ 'reviewForm.comment': e.detail.value })
  },

  stop() {},

  async submitResolve() {
    const { resolveForm, resolvingId } = this.data
    if (!resolveForm.faultType || !resolveForm.handlingMethod || !resolveForm.testResult.trim()) {
      wx.showToast({ title: '请填写故障类型、处理方式、试验结果', icon: 'none' })
      return
    }
    try {
      await api.submitWorkOrderResolution(resolvingId, {
        faultType: resolveForm.faultType,
        handlingMethod: resolveForm.handlingMethod,
        replacedParts: resolveForm.replacedParts.trim() || undefined,
        testResult: resolveForm.testResult.trim(),
        remarks: resolveForm.remarks.trim() || undefined,
      })
      this.setData({ showResolve: false })
      wx.showToast({ title: '已提交复核' })
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '提交失败', icon: 'none' })
    }
  },

  async submitReview() {
    const { reviewForm, reviewingId } = this.data
    if (!reviewForm.comment.trim()) {
      wx.showToast({ title: '请填写复核意见', icon: 'none' })
      return
    }
    try {
      await api.submitWorkOrderReview(reviewingId, {
        result: reviewForm.result,
        comment: reviewForm.comment.trim(),
      })
      this.setData({ showReview: false })
      wx.showToast({
        title: reviewForm.result === 'PASS' ? '复核通过，工单已关闭' : '已退回，等待重新处理',
      })
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '复核失败', icon: 'none' })
    }
  },

  goAlarms() {
    wx.switchTab({ url: '/pages/alarms/index' })
  },
})
