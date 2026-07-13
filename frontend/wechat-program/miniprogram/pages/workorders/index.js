const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const workOrderPerm = require('../../utils/work-order-permission')
const { WORK_ORDER_STATUS_LABELS, WORK_ORDER_PRIORITY_LABELS, ALARM_SEVERITY_LABELS, DETECTION_LABELS } = require('../../utils/constants')
const {
  FAULT_TYPE_OPTIONS,
  HANDLING_METHOD_OPTIONS,
  enrichWorkOrder,
  assignActionLabel,
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
  remarks: '',
  photoItems: [],
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
    showAssign: false,
    resolvingId: '',
    reviewingId: '',
    assigningId: '',
    assignDialogTitle: '指派调度员',
    dispatchers: [],
    dispatcherLabels: [],
    assignForm: { dispatcherIndex: 0 },
    resolveForm: { ...EMPTY_RESOLVE_FORM },
    reviewForm: { result: 'PASS', comment: '' },
    faultTypeOptions: FAULT_TYPE_OPTIONS,
    handlingMethodOptions: HANDLING_METHOD_OPTIONS,
    canCreate: false,
    canAssign: false,
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
    const isAdmin = user.role === 'ADMIN'
    this.setData({
      user,
      canCreate: hasPermission(user.role, 'workorder:create'),
      canAssign: hasPermission(user.role, 'workorder:assign'),
      canProcess: hasPermission(user.role, 'workorder:process'),
      canReview: hasPermission(user.role, 'workorder:review'),
      pageDesc: isAdmin
        ? '告警转工单、指派/改派与复核'
        : '现场处置与提交复核',
    })
    this.loadDispatchers()
    this.load()
    app.refreshBadges()
  },

  async loadDispatchers() {
    const user = this.data.user || getApp().globalData.user
    if (!hasPermission(user?.role, 'workorder:assign')) return
    try {
      const users = await api.listUsers()
      const dispatchers = users.filter((u) => u.role === 'DISPATCHER')
      const dispatcherLabels = dispatchers.map((u) => `${u.displayName}（${u.username}）`)
      this.setData({ dispatchers, dispatcherLabels })
    } catch (e) {
      console.warn('loadDispatchers', e)
    }
  },

  async load() {
    const user = this.data.user || getApp().globalData.user
    const canCreate = workOrderPerm.canCreateWorkOrder(user)
    let pendingAlarms = []
    if (canCreate) {
      const alarmsBefore = await api.getAlarms()
      await api.tryAutoConvertPendingAlarms(alarmsBefore, user)
      const [alarms, ordersForAlarm] = await Promise.all([
        api.getAlarms(),
        api.getWorkOrders(),
      ])
      const linkedAlarmIds = new Set(
        ordersForAlarm.filter((o) => o.alarmId).map((o) => o.alarmId),
      )
      pendingAlarms = alarms
        .filter((a) => !linkedAlarmIds.has(a.id))
        .slice(0, 8)
        .map((a) => ({
          ...a,
          severityLabel: ALARM_SEVERITY_LABELS[a.severity],
          typeLabel: DETECTION_LABELS[a.type] || a.type,
          sevType: a.severity === 'CRITICAL' ? 'danger' : a.severity === 'HIGH' ? 'warning' : 'info',
          time: a.createdAt ? a.createdAt.slice(0, 16).replace('T', ' ') : '',
        }))
    }

    let orders = await api.getWorkOrders()
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
        canAssign: workOrderPerm.canAssignOrder(o, user),
        assignLabel: assignActionLabel(o),
        canSubmitReview: workOrderPerm.canSubmitReview(o, user),
        canConfirmReview: workOrderPerm.canConfirmReview(o, user),
      }
    })
    const counts = { PENDING: 0, PROCESSING: 0, REVIEW: 0, CLOSED: 0 }
    orders.forEach((o) => { if (counts[o.status] !== undefined) counts[o.status]++ })
    const isDispatcher = user.role === 'DISPATCHER'
    const statusCards = [
      { key: '', label: '全部', value: orders.length },
      ...(!isDispatcher ? [{ key: 'PENDING', label: '待处理', value: counts.PENDING }] : []),
      { key: 'PROCESSING', label: '处理中', value: counts.PROCESSING },
      { key: 'REVIEW', label: '待复核', value: counts.REVIEW },
    ]
    let statusFilter = this.data.statusFilter
    if (isDispatcher && statusFilter === 'PENDING') statusFilter = ''
    this.setData({ orders, statusCards, pendingAlarms, statusFilter })
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
    this.setData({ detail: withPhotoPreview(order), showDetail: true })
  },

  closeDetail() { this.setData({ showDetail: false }) },

  openAssign(e) {
    const id = e.currentTarget.dataset.id
    const order = this.data.orders.find((o) => o.id === id)
    if (!workOrderPerm.canAssignOrder(order, this.data.user)) {
      wx.showToast({ title: '无指派权限', icon: 'none' })
      return
    }
    const { dispatchers } = this.data
    if (!dispatchers.length) {
      wx.showToast({ title: '暂无调度员账号', icon: 'none' })
      return
    }
    let dispatcherIndex = 0
    if (order.assigneeId) {
      const idx = dispatchers.findIndex((u) => u.id === order.assigneeId)
      if (idx >= 0) dispatcherIndex = idx
    } else if (order.assigneeName) {
      const idx = dispatchers.findIndex((u) => u.displayName === order.assigneeName)
      if (idx >= 0) dispatcherIndex = idx
    }
    this.setData({
      assigningId: id,
      assignDialogTitle: assignActionLabel(order) === '改派' ? '改派调度员' : '指派调度员',
      assignForm: { dispatcherIndex },
      showAssign: true,
    })
  },

  closeAssign() { this.setData({ showAssign: false }) },

  onDispatcherChange(e) {
    this.setData({ 'assignForm.dispatcherIndex': Number(e.detail.value) })
  },

  async submitAssign() {
    const { assigningId, dispatchers, assignForm } = this.data
    const dispatcher = dispatchers[assignForm.dispatcherIndex]
    if (!dispatcher) {
      wx.showToast({ title: '请选择调度员', icon: 'none' })
      return
    }
    const order = this.data.orders.find((o) => o.id === assigningId)
    const isReassign = order && assignActionLabel(order) === '改派'
    try {
      await api.assignWorkOrder(assigningId, {
        id: dispatcher.id,
        name: dispatcher.displayName,
      })
      this.setData({ showAssign: false })
      wx.showToast({
        title: isReassign
          ? `已改派给 ${dispatcher.displayName}`
          : `已指派给 ${dispatcher.displayName}`,
      })
      getApp().refreshBadges()
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '指派失败', icon: 'none' })
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
