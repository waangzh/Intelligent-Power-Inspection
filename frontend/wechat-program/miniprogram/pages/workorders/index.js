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
  validateResolveFormForBackend,
  buildLocationContext,
} = require('../../utils/work-order')
const { syncTabBar, refreshTabBarBadges } = require('../../utils/tab-page')
const { formatBusinessMessage } = require('../../utils/display-text')
const { formatDateTimeShort } = require('../../utils/date-time')
const { resolvePhotoSrc } = require('../../utils/work-order-photo')

function withPhotoPreview(order) {
  if (!order?.resolutionForm?.photos?.length) return order
  const photoSrcs = order.resolutionForm.photos.map(resolvePhotoSrc).filter(Boolean)
  return {
    ...order,
    resolutionForm: { ...order.resolutionForm, photoSrcs },
  }
}

function emptyResolveForm() {
  return {
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
}

const REVIEW_CONCLUSION_LABEL_LIST = REVIEW_CONCLUSION_OPTIONS.map((v) => REVIEW_CONCLUSION_LABELS[v])

const STATUS_EMPTY_HINTS = {
  ALL: '暂无工单',
  PENDING: '暂无待接单工单',
  PROCESSING: '暂无处理中工单',
  REVIEW: '暂无待复核工单',
}

function normalizeStatusFilter(value) {
  if (value === undefined || value === null || value === '' || value === 'ALL') return 'ALL'
  return String(value)
}

Page({
  data: {
    orders: [],
    filtered: [],
    statusFilter: 'ALL',
    statusChips: [],
    showStatusChips: false,
    scopeFilter: 'POOL',
    poolCount: 0,
    mineCount: 0,
    emptyHint: '暂无工单',
    isDispatcher: false,
    isViewer: false,
    pageTitle: '工单管理',
    detail: null,
    showDetail: false,
    showResolve: false,
    showReview: false,
    resolvingId: '',
    reviewingId: '',
    claimingId: '',
    resolveForm: emptyResolveForm(),
    reviewForm: { result: 'PASS', comment: '' },
    faultTypeOptions: FAULT_TYPE_OPTIONS,
    handlingMethodOptions: HANDLING_METHOD_OPTIONS,
    conclusionOptions: REVIEW_CONCLUSION_LABEL_LIST,
    needsFollowUpPlan: false,
    submittingResolve: false,
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
    const isViewer = user.role === 'VIEWER'
    const pageTitle = isViewer ? '工单' : '工单管理'
    wx.setNavigationBarTitle({ title: pageTitle })
    this.setData({
      user,
      isDispatcher,
      isViewer,
      pageTitle,
      canCreate: hasPermission(perms, 'workorder:create'),
      canProcess: hasPermission(perms, 'workorder:process'),
      canReview: hasPermission(perms, 'workorder:review'),
      pageDesc: isViewer
        ? '查看工单处置记录（只读）'
        : isAdmin
          ? '告警转工单与复核'
          : '接单大厅抢单、现场处置与提交复核',
    })
    this.load()
    refreshTabBarBadges(this)
  },

  async load() {
    const user = this.data.user || getApp().globalData.user
    const perms = getApp().globalData.permissions
    const canCreate = workOrderPerm.canCreateWorkOrder(user, perms)
    let pendingAlarms = []
    let orders = []

    const [sites, allAlarms] = await Promise.all([
      api.getSites().catch(() => []),
      api.getAlarms().catch(() => []),
    ])
    const locationContext = buildLocationContext(allAlarms, sites)

    if (canCreate) {
      const snapshot = await api.tryAutoConvertPendingAlarms().catch(() => ({
        alarms: [],
        orders: [],
      }))
      orders = snapshot.orders || []
      ;(snapshot.alarms || []).forEach((alarm) => {
        if (alarm?.id) locationContext.alarmsById[alarm.id] = alarm
      })
      const linkedAlarmIds = new Set(
        orders.filter((o) => o.alarmId).map((o) => o.alarmId),
      )
      pendingAlarms = (snapshot.alarms || [])
        .filter((a) => !linkedAlarmIds.has(a.id))
        .slice(0, 8)
        .map((a) => ({
          ...a,
          message: formatBusinessMessage(a.message),
          severityLabel: ALARM_SEVERITY_LABELS[a.severity],
          typeLabel: DETECTION_LABELS[a.type] || a.type,
          sevType: a.severity === 'CRITICAL' ? 'danger' : a.severity === 'HIGH' ? 'warning' : 'info',
          time: formatDateTimeShort(a.createdAt),
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
      }, locationContext)
      return withPhotoPreview({
        ...enriched,
        showDescription: enriched.showDescription,
        canClaim: workOrderPerm.canClaimOrder(o, user, perms),
        canSubmitReview: workOrderPerm.canSubmitReview(o, user, perms),
        canConfirmReview: workOrderPerm.canConfirmReview(o, user, perms),
      })
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
    this.setData({ scopeFilter: key, statusFilter: 'ALL' })
    this.rebuildFilters()
  },

  filterByStatus(e) {
    const statusFilter = normalizeStatusFilter(e.currentTarget.dataset.status)
    if (statusFilter === this.data.statusFilter) return
    this.setData({
      statusFilter,
      emptyHint: STATUS_EMPTY_HINTS[statusFilter] || STATUS_EMPTY_HINTS.ALL,
    })
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
        { key: 'ALL', label: '全部', value: scoped.length },
        { key: 'PROCESSING', label: '处理中', value: counts.PROCESSING },
        { key: 'REVIEW', label: '待复核', value: counts.REVIEW },
      ]
    } else {
      statusChips = [
        { key: 'ALL', label: '全部', value: scoped.length },
        { key: 'PENDING', label: '待接单', value: counts.PENDING },
        { key: 'PROCESSING', label: '处理中', value: counts.PROCESSING },
        { key: 'REVIEW', label: '待复核', value: counts.REVIEW },
      ]
    }

    let emptyHint = STATUS_EMPTY_HINTS[this.data.statusFilter] || STATUS_EMPTY_HINTS.ALL
    if (isDispatcher && scopeFilter === 'POOL') emptyHint = '暂无待接工单'
    else if (isDispatcher && scopeFilter === 'MINE' && this.data.statusFilter === 'ALL') emptyHint = '暂无我的工单'

    this.setData({ statusChips, showStatusChips, emptyHint })
    this.applyFilter()
  },

  applyFilter() {
    const { orders, statusFilter, scopeFilter, user, isDispatcher } = this.data
    const scope = isDispatcher ? scopeFilter : 'ALL'
    let list = workOrderPerm.filterByScope(orders, user, scope)
    if (statusFilter && statusFilter !== 'ALL') {
      list = list.filter((o) => o.status === statusFilter)
    }
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
      this.setData({ scopeFilter: 'MINE', statusFilter: 'ALL' })
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
      resolveForm: emptyResolveForm(),
      needsFollowUpPlan: false,
      showResolve: true,
    })
  },

  closeResolve() {
    const { resolvingId, resolveForm } = this.data
    const pending = (resolveForm.photoItems || [])
      .map((item) => item.uploadedUrl)
      .filter(Boolean)
    if (pending.length) {
      api.discardWorkOrderPhotos(resolvingId, pending)
    }
    this.setData({ showResolve: false })
  },

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
    const removed = photoItems[index]
    photoItems.splice(index, 1)
    this.setData({ 'resolveForm.photoItems': photoItems })
    if (removed?.uploadedUrl) {
      api.discardWorkOrderPhoto(this.data.resolvingId, removed.uploadedUrl)
    }
  },

  previewResolvePhoto(e) {
    const index = Number(e.currentTarget.dataset.index)
    const urls = this.data.resolveForm.photoItems.map((p) => p.localPath)
    wx.previewImage({ current: urls[index], urls })
  },

  previewDetailPhoto(e) {
    const index = Number(e.currentTarget.dataset.index)
    const urls = this.data.detail?.resolutionForm?.photoSrcs || []
    if (!urls.length) {
      wx.showToast({ title: '暂无照片', icon: 'none' })
      return
    }
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
    const { resolveForm, resolvingId, submittingResolve } = this.data
    if (submittingResolve) return
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
    const limitError = validateResolveFormForBackend({
      faultType: resolveForm.faultType,
      handlingMethod: resolveForm.handlingMethod,
      replacedParts: resolveForm.replacedParts.trim() || undefined,
      testResult: resolveForm.testResult.trim(),
      conclusion: resolveForm.conclusion,
      remarks: resolveForm.remarks.trim() || undefined,
      photos: resolveForm.photoItems.length ? resolveForm.photoItems.map(() => 'x') : undefined,
    })
    if (limitError) {
      wx.showToast({ title: limitError, icon: 'none' })
      return
    }
    this.setData({ submittingResolve: true })
    try {
      wx.showLoading({ title: '提交中' })
      const photoItems = [...resolveForm.photoItems]
      const photos = []
      for (let i = 0; i < photoItems.length; i += 1) {
        const item = photoItems[i]
        if (item.uploadedUrl) {
          photos.push(item.uploadedUrl)
          continue
        }
        const url = await api.uploadWorkOrderPhoto(resolvingId, item.localPath)
        photoItems[i] = { ...item, uploadedUrl: url }
        photos.push(url)
      }
      if (photoItems.some((item, i) => item.uploadedUrl !== resolveForm.photoItems[i]?.uploadedUrl)) {
        this.setData({ 'resolveForm.photoItems': photoItems })
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
      this.setData({ submittingResolve: false })
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
