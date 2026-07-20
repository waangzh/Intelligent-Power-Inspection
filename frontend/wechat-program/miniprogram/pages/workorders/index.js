const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const workOrderPerm = require('../../utils/work-order-permission')
const { WORK_ORDER_STATUS_LABELS, WORK_ORDER_PRIORITY_LABELS, ALARM_SEVERITY_LABELS, DETECTION_LABELS } = require('../../utils/constants')
const {
  FAULT_TYPE_OPTIONS,
  HANDLING_METHOD_OPTIONS,
  REVIEW_CONCLUSION_OPTIONS,
  REVIEW_CONCLUSION_LABELS,
  CONCLUSIONS_REQUIRING_FOLLOW_UP,
  enrichWorkOrder,
  isWorkOrderUnassigned,
} = require('../../utils/work-order')
const { syncTabBar } = require('../../utils/tab-page')
const { resolvePhotoSrc } = require('../../utils/work-order-photo')

function withPhotoPreview(order) {
  if (!order?.resolutionForm?.photos?.length) return order
  const photoSrcs = order.resolutionForm.photos.map(resolvePhotoSrc)
  return {
    ...order,
    resolutionForm: { ...order.resolutionForm, photoSrcs },
  }
}

const EMPTY_RESOLVE_FORM = {
  faultType: '',
  faultTypeIndex: -1,
  handlingMethod: '',
  handlingMethodIndex: -1,
  replacedParts: '',
  testResult: '',
  conclusion: '',
  conclusionIndex: -1,
  remarks: '',
  photoItems: [],
}

const REVIEW_CONCLUSION_LABEL_LIST = REVIEW_CONCLUSION_OPTIONS.map((v) => REVIEW_CONCLUSION_LABELS[v])

Page({
  data: {
    orders: [],
    filtered: [],
    statusFilter: '',
    statusChips: [],
    showStatusChips: false,
    scopeFilter: 'POOL',
    poolCount: 0,
    mineCount: 0,
    emptyHint: '暂无工单',
    isDispatcher: false,
    detail: null,
    showDetail: false,
    showResolve: false,
    showReview: false,
    resolvingId: '',
    reviewingId: '',
    claimingId: '',
    resolveForm: { ...EMPTY_RESOLVE_FORM },
    reviewForm: { result: 'PASS', comment: '' },
    faultTypeOptions: FAULT_TYPE_OPTIONS,
    handlingMethodOptions: HANDLING_METHOD_OPTIONS,
    conclusionOptions: REVIEW_CONCLUSION_LABEL_LIST,
    needsFollowUpPlan: false,
    canCreate: false,
    canProcess: false,
    canReview: false,
    user: null,
    pageDesc: '',
    pendingAlarms: [],
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/workorders/index')) return
    if (!app.requirePermission('workorder:view')) return
    syncTabBar(this)
    const user = app.globalData.user
    const perms = app.globalData.permissions
    const isAdmin = user.role === 'ADMIN'
    const isDispatcher = user.role === 'DISPATCHER'
    this.setData({
      user,
      isDispatcher,
      canCreate: hasPermission(perms, 'workorder:create'),
      canProcess: hasPermission(perms, 'workorder:process'),
      canReview: hasPermission(perms, 'workorder:review'),
      pageDesc: isAdmin
        ? '告警转工单与复核'
        : '接单大厅抢单、现场处置与提交复核',
    })
    this.load()
    app.refreshBadges()
  },

  async load() {
    const user = this.data.user || getApp().globalData.user
    const perms = getApp().globalData.permissions
    const canCreate = workOrderPerm.canCreateWorkOrder(user, perms)
    let pendingAlarms = []
    let orders = []

    if (canCreate) {
      const snapshot = await api.tryAutoConvertPendingAlarms().catch(() => ({
        alarms: [],
        orders: [],
      }))
      orders = snapshot.orders || []
      const linkedAlarmIds = new Set(
        orders.filter((o) => o.alarmId).map((o) => o.alarmId),
      )
      pendingAlarms = (snapshot.alarms || [])
        .filter((a) => !linkedAlarmIds.has(a.id))
        .slice(0, 8)
        .map((a) => ({
          ...a,
          severityLabel: ALARM_SEVERITY_LABELS[a.severity],
          typeLabel: DETECTION_LABELS[a.type] || a.type,
          sevType: a.severity === 'CRITICAL' ? 'danger' : a.severity === 'HIGH' ? 'warning' : 'info',
          time: a.createdAt ? a.createdAt.slice(0, 16).replace('T', ' ') : '',
        }))
    } else {
      orders = await api.getWorkOrders()
    }
    orders = workOrderPerm.filterVisibleWorkOrders(orders, user)
    orders = orders.map((o) => {
      const enriched = enrichWorkOrder({
        ...o,
        statusLabel: WORK_ORDER_STATUS_LABELS[o.status],
        priorityLabel: WORK_ORDER_PRIORITY_LABELS[o.priority],
        createdLabel: o.createdAt ? o.createdAt.slice(0, 16).replace('T', ' ') : '',
      })
      return {
        ...enriched,
        canClaim: workOrderPerm.canClaimOrder(o, user, perms),
        canSubmitReview: workOrderPerm.canSubmitReview(o, user, perms),
        canConfirmReview: workOrderPerm.canConfirmReview(o, user, perms),
      }
    })

    const isDispatcher = user.role === 'DISPATCHER'
    const poolCount = isDispatcher ? orders.filter((o) => isWorkOrderUnassigned(o)).length : 0
    const mineCount = isDispatcher
      ? orders.filter((o) => workOrderPerm.isWorkOrderAssignee(o, user)).length
      : 0

    this.setData({ orders, poolCount, mineCount, pendingAlarms })
    this.rebuildFilters()
  },

  switchScope(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.scopeFilter) return
    this.setData({ scopeFilter: key, statusFilter: '' })
    this.rebuildFilters()
  },

  filterByStatus(e) {
    this.setData({ statusFilter: e.currentTarget.dataset.key })
    this.applyFilter()
  },

  rebuildFilters() {
    const { orders, scopeFilter, user, isDispatcher } = this.data
    const scoped = workOrderPerm.filterByScope(orders, user, isDispatcher ? scopeFilter : 'ALL')
    const counts = { PENDING: 0, PROCESSING: 0, REVIEW: 0 }
    scoped.forEach((o) => {
      if (counts[o.status] !== undefined) counts[o.status]++
    })

    let statusChips = []
    let showStatusChips = true
    if (isDispatcher && scopeFilter === 'POOL') {
      showStatusChips = false
    } else if (isDispatcher && scopeFilter === 'MINE') {
      statusChips = [
        { key: '', label: '全部', value: scoped.length },
        { key: 'PROCESSING', label: '处理中', value: counts.PROCESSING },
        { key: 'REVIEW', label: '待复核', value: counts.REVIEW },
      ]
    } else {
      statusChips = [
        { key: '', label: '全部', value: scoped.length },
        { key: 'PENDING', label: '待接单', value: counts.PENDING },
        { key: 'PROCESSING', label: '处理中', value: counts.PROCESSING },
        { key: 'REVIEW', label: '待复核', value: counts.REVIEW },
      ]
    }

    let emptyHint = '暂无工单'
    if (isDispatcher && scopeFilter === 'POOL') emptyHint = '暂无待接工单'
    else if (isDispatcher && scopeFilter === 'MINE') emptyHint = '暂无我的工单'

    this.setData({ statusChips, showStatusChips, emptyHint })
    this.applyFilter()
  },

  applyFilter() {
    const { orders, statusFilter, scopeFilter, user } = this.data
    let list = workOrderPerm.filterByScope(orders, user, scopeFilter)
    if (statusFilter) list = list.filter((o) => o.status === statusFilter)
    this.setData({ filtered: list })
  },

  openDetail(e) {
    const order = this.data.orders.find((o) => o.id === e.currentTarget.dataset.id)
    this.setData({ detail: withPhotoPreview(order), showDetail: true })
  },

  closeDetail() { this.setData({ showDetail: false }) },

  async claim(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    const perms = getApp().globalData.permissions
    if (!workOrderPerm.canClaimOrder(order, this.data.user, perms)) {
      wx.showToast({ title: '无法接单', icon: 'none' })
      return
    }
    this.setData({ claimingId: id })
    try {
      await api.claimWorkOrder(id)
      wx.showToast({ title: '接单成功，开始处置' })
      this.setData({ scopeFilter: 'MINE', statusFilter: '' })
      getApp().refreshBadges()
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '接单失败', icon: 'none' })
    } finally {
      this.setData({ claimingId: '' })
    }
  },

  openResolve(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    const perms = getApp().globalData.permissions
    if (!workOrderPerm.canSubmitReview(order, this.data.user, perms)) {
      wx.showToast({ title: '仅接单调度员可提交复核', icon: 'none' })
      return
    }
    this.setData({
      resolvingId: id,
      resolveForm: { ...EMPTY_RESOLVE_FORM },
      needsFollowUpPlan: false,
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

  onConclusionChange(e) {
    const index = Number(e.detail.value)
    const conclusion = REVIEW_CONCLUSION_OPTIONS[index]
    this.setData({
      'resolveForm.conclusionIndex': index,
      'resolveForm.conclusion': conclusion,
      needsFollowUpPlan: CONCLUSIONS_REQUIRING_FOLLOW_UP.includes(conclusion),
    })
  },

  onResolveFieldInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`resolveForm.${field}`]: e.detail.value })
  },

  choosePhotos() {
    const remain = 3 - this.data.resolveForm.photoItems.length
    if (remain <= 0) {
      wx.showToast({ title: '最多上传 3 张', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const items = res.tempFiles.map((f) => ({ localPath: f.tempFilePath }))
        this.setData({
          'resolveForm.photoItems': [...this.data.resolveForm.photoItems, ...items],
        })
      },
    })
  },

  removePhoto(e) {
    const index = Number(e.currentTarget.dataset.index)
    const photoItems = [...this.data.resolveForm.photoItems]
    photoItems.splice(index, 1)
    this.setData({ 'resolveForm.photoItems': photoItems })
  },

  previewResolvePhoto(e) {
    const index = Number(e.currentTarget.dataset.index)
    const urls = this.data.resolveForm.photoItems.map((p) => p.localPath)
    wx.previewImage({ current: urls[index], urls })
  },

  previewDetailPhoto(e) {
    const index = Number(e.currentTarget.dataset.index)
    const urls = this.data.detail.resolutionForm.photoSrcs
    wx.previewImage({ current: urls[index], urls })
  },

  openReview(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    const perms = getApp().globalData.permissions
    if (!workOrderPerm.canConfirmReview(order, this.data.user, perms)) {
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

  async createFromAlarm(e) {
    const id = e.currentTarget.dataset.id
    const alarm = this.data.pendingAlarms.find((a) => a.id === id)
    if (!alarm) return
    try {
      await api.createWorkOrderFromAlarm(alarm, this.data.user)
      wx.showToast({ title: '工单已创建' })
      getApp().refreshBadges()
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '创建失败', icon: 'none' })
    }
  },

  stop() {},

  async submitResolve() {
    const { resolveForm, resolvingId } = this.data
    if (!resolveForm.faultType || !resolveForm.handlingMethod || !resolveForm.testResult.trim()) {
      wx.showToast({ title: '请填写故障类型、处理方式、试验结果', icon: 'none' })
      return
    }
    if (!resolveForm.conclusion) {
      wx.showToast({ title: '请选择处理结论', icon: 'none' })
      return
    }
    if (this.data.needsFollowUpPlan && !resolveForm.remarks.trim()) {
      wx.showToast({ title: '部分消缺/未消缺时请填写补充说明', icon: 'none' })
      return
    }
    try {
      wx.showLoading({ title: '提交中' })
      const photos = []
      for (const item of resolveForm.photoItems) {
        photos.push(await api.uploadWorkOrderPhoto(item.localPath))
      }
      await api.submitWorkOrderResolution(resolvingId, {
        faultType: resolveForm.faultType,
        handlingMethod: resolveForm.handlingMethod,
        replacedParts: resolveForm.replacedParts.trim() || undefined,
        testResult: resolveForm.testResult.trim(),
        conclusion: resolveForm.conclusion,
        remarks: resolveForm.remarks.trim() || undefined,
        photos: photos.length ? photos : undefined,
      })
      this.setData({ showResolve: false })
      wx.showToast({ title: '已提交复核' })
      getApp().refreshBadges()
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '提交失败', icon: 'none' })
    } finally {
      wx.hideLoading()
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
      getApp().refreshBadges()
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '复核失败', icon: 'none' })
    }
  },
})
