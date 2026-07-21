const FAULT_TYPE_OPTIONS = [
  '设备渗漏油',
  '表计异常',
  '开关/刀闸异常',
  '异物入侵',
  '火源/烟雾',
  '人员违章',
  '其他',
]

const HANDLING_METHOD_OPTIONS = [
  '现场清理',
  '紧固/复位',
  '更换部件',
  '临时隔离',
  '上报待检修',
  '误报关闭',
]

const {
  WORK_ORDER_REVIEW_CONCLUSION_VALUES: REVIEW_CONCLUSION_OPTIONS,
  WORK_ORDER_REVIEW_CONCLUSION_LABELS: REVIEW_CONCLUSION_LABELS,
} = require('../generated/domain-enums')
const { formatBusinessMessage, formatWorkOrderTitle } = require('./display-text')
const { formatDateTime, formatDateTimeShort } = require('./date-time')

/** 部分消缺 / 未消缺时，后端要求必须填写遗留风险与后续计划 */
const CONCLUSIONS_REQUIRING_FOLLOW_UP = ['PARTIALLY_RESOLVED', 'UNRESOLVED']

const ACTIVE_STATUSES = ['PROCESSING', 'REVIEW']

/** 后端曾把 assigneeName 默认成创建人，且未写入 assigneeId */
function isPhantomAssignee(order) {
  if (!order?.assigneeName?.trim()) return true
  if (order.assigneeId) return false
  return order.assigneeName === order.createdByName
}

/** 仅待处理工单可视为未指派 */
function isWorkOrderUnassigned(order) {
  if (order.status !== 'PENDING') return false
  return isPhantomAssignee(order)
}

function resolveAssigneeName(order) {
  if (!isPhantomAssignee(order) && order.assigneeName?.trim()) {
    return order.assigneeName.trim()
  }
  return order.resolutionForm?.submittedBy?.trim() || undefined
}

function workOrderAssigneeLabel(order) {
  const name = resolveAssigneeName(order)
  if (name) return name
  if (order.status === 'CLOSED' || order.status === 'CANCELLED') {
    return order.resolutionForm?.submittedBy?.trim()
      || order.reviewForm?.reviewedBy?.trim()
      || '-'
  }
  return '待接单'
}

/** 处理中/待复核却没有真实处理人，属于历史脏数据 */
function isInconsistentActiveOrder(order) {
  return ACTIVE_STATUSES.includes(order.status) && !resolveAssigneeName(order)
}

function normalizeWorkOrder(order) {
  if (isInconsistentActiveOrder(order)) {
    return {
      ...order,
      status: 'PENDING',
      assigneeName: undefined,
      assigneeId: undefined,
    }
  }

  if (isWorkOrderUnassigned(order)) {
    return { ...order, assigneeName: undefined, assigneeId: undefined }
  }

  // 已指派但仍停留在待处理，属于历史数据，应进入处理中
  if (order.status === 'PENDING' && !isPhantomAssignee(order)) {
    return { ...order, status: 'PROCESSING' }
  }

  return order
}

function canAssignOrder(order) {
  return order.status === 'PENDING' || order.status === 'PROCESSING'
}

function assignActionLabel(order) {
  return order.status === 'PENDING' && isWorkOrderUnassigned(order) ? '指派' : '改派'
}

function buildLocationFromAlarm(alarm, site) {
  const routeName = alarm.routeName || ''
  const checkpointName = alarm.checkpointName
  return {
    siteName: site?.name,
    routeName,
    checkpointName,
    areaName: checkpointName ? `${routeName} · ${checkpointName}` : routeName,
    address: site?.address,
  }
}

function locationLabel(order) {
  const loc = order?.location
  if (loc) {
    if (loc.checkpointName) {
      return `${loc.routeName || ''} · ${loc.checkpointName}`.replace(/^ · | · $/g, '') || '-'
    }
    return loc.areaName || loc.routeName || '-'
  }
  const desc = formatBusinessMessage(order?.locationDescription || '')
  return desc || '-'
}

function resolutionSummary(form) {
  return [
    `故障类型：${form.faultType}`,
    `处理方式：${form.handlingMethod}`,
    form.replacedParts ? `更换部件：${form.replacedParts}` : '',
    `试验结果：${form.testResult}`,
    form.remarks ? `备注：${form.remarks}` : '',
    form.photos?.length ? `现场照片：${form.photos.length} 张` : '',
  ]
    .filter(Boolean)
    .join('；')
}

/**
 * 后端 `/work-orders/{id}/status` 在流转到 REVIEW 时要求提交结构化的 review
 * （conclusion/onsiteFinding/handlingMeasures/followUpPlan），字段与现场处理表单不同，
 * 这里做一次转换，避免真实后端因缺少 review 而拒绝提交。
 */
function buildReviewFromResolveForm(form) {
  let handlingMeasures = form.replacedParts
    ? `现场处理方式为${form.handlingMethod}，更换部件：${form.replacedParts}`
    : `现场处理方式为${form.handlingMethod}`
  if (form.photos?.length) {
    handlingMeasures += `；已上传现场照片 ${form.photos.length} 张`
  }
  return {
    conclusion: form.conclusion,
    onsiteFinding: `现场故障类型为${form.faultType}，试验/复测结果：${form.testResult}`,
    handlingMeasures,
    followUpPlan: form.remarks || undefined,
  }
}

/** 与后端 normalizeReview 的 10～500 字限制对齐 */
function validateResolveFormForBackend(form) {
  const review = buildReviewFromResolveForm(form)
  if (review.onsiteFinding.length < 10) {
    return '试验结果过短，请补充说明（合计至少约 10 字）'
  }
  if (review.onsiteFinding.length > 500) {
    return '试验结果过长，请精简后再提交'
  }
  if (review.handlingMeasures.length < 10) {
    return '处理方式信息过短，请检查表单'
  }
  if (review.handlingMeasures.length > 500) {
    return '处理方式/更换部件描述过长，请精简后再提交'
  }
  if (review.followUpPlan && review.followUpPlan.length > 500) {
    return '补充说明不能超过 500 字'
  }
  return null
}

function composeLocationParts(siteName, routeName, checkpointName, address) {
  return [siteName, routeName, checkpointName, address]
    .map((v) => String(v || '').trim())
    .filter(Boolean)
    .join(' / ')
}

/** 前端展示：优先用后端 locationDescription，否则按 alarmId/siteId 关联补全 */
function resolveLocationDescription(order, context = {}) {
  const stored = formatBusinessMessage(order?.locationDescription || '')
  if (stored) return stored

  const { alarmsById = {}, sitesById = {} } = context
  const alarm = order?.alarmId ? alarmsById[order.alarmId] : null
  if (alarm) {
    const siteId = alarm.siteId || order.siteId
    const site = siteId ? sitesById[siteId] : null
    const routeName = String(alarm.routeName || '').trim()
    const checkpointName = String(alarm.checkpointName || '').trim()
    if (site?.name) {
      return composeLocationParts(
        site.name,
        routeName || null,
        checkpointName || (routeName ? '路线行进中' : null),
        site.address,
      )
    }
    if (!routeName) return checkpointName
    return checkpointName ? `${routeName} / ${checkpointName}` : routeName
  }

  const siteId = order?.siteId
  if (siteId && sitesById[siteId]) {
    const site = sitesById[siteId]
    return composeLocationParts(site.name, null, null, site.address)
  }
  return ''
}

function buildLocationContext(alarms = [], sites = []) {
  const alarmsById = {}
  alarms.forEach((alarm) => {
    if (alarm?.id) alarmsById[alarm.id] = alarm
  })
  const sitesById = {}
  sites.forEach((site) => {
    if (site?.id) sitesById[site.id] = site
  })
  return { alarmsById, sitesById }
}

function parseLocationDescription(desc) {
  const text = String(desc || '').trim()
  if (!text || text === '-') {
    return {
      locationSite: '-',
      locationArea: '-',
      locationCheckpoint: '-',
      locationAddress: '-',
    }
  }
  const parts = text.split(/\s*\/\s*/).filter(Boolean)
  if (parts.length >= 4) {
    return {
      locationSite: parts[0],
      locationArea: parts[1],
      locationCheckpoint: parts[2],
      locationAddress: parts.slice(3).join(' / '),
    }
  }
  if (parts.length === 3) {
    return {
      locationSite: parts[0],
      locationArea: parts[1],
      locationCheckpoint: parts[2],
      locationAddress: '-',
    }
  }
  if (parts.length === 2) {
    return {
      locationSite: '-',
      locationArea: parts[0],
      locationCheckpoint: parts[1],
      locationAddress: '-',
    }
  }
  if (parts.length === 1) {
    return {
      locationSite: parts[0],
      locationArea: '-',
      locationCheckpoint: '-',
      locationAddress: '-',
    }
  }
  return {
    locationSite: '-',
    locationArea: '-',
    locationCheckpoint: text,
    locationAddress: '-',
  }
}

function resolveLocationFields(order) {
  const loc = order.location || {}
  if (loc.siteName || loc.routeName || loc.checkpointName || loc.areaName || loc.address) {
    return {
      locationSite: loc.siteName || '-',
      locationArea: loc.areaName || loc.routeName || '-',
      locationCheckpoint: loc.checkpointName || '路线行进中',
      locationAddress: loc.address || '-',
    }
  }
  const desc = formatBusinessMessage(order.locationDescription || '')
  return parseLocationDescription(desc)
}

function countFilledLocationParts(fields) {
  return [fields.locationSite, fields.locationArea, fields.locationCheckpoint, fields.locationAddress]
    .filter((v) => v && v !== '-').length
}

function locationSummaryFrom(order, locFields) {
  const summary = formatBusinessMessage(order.locationDescription || '')
  if (summary) return summary
  const parts = [locFields.locationSite, locFields.locationArea, locFields.locationCheckpoint, locFields.locationAddress]
    .filter((v) => v && v !== '-')
  return parts.length ? parts.join(' / ') : '-'
}

function enrichWorkOrder(order, context = {}) {
  const resolvedLocationDescription = resolveLocationDescription(order, context)
  const displayOrder = resolvedLocationDescription
    ? { ...order, locationDescription: resolvedLocationDescription }
    : order
  const locFields = resolveLocationFields(displayOrder)
  const resolutionForm = order.resolutionForm
  const reviewForm = order.reviewForm
  const displayTitle = formatWorkOrderTitle(order.title)
  const displayDescription = formatBusinessMessage(order.description)
  const showDescription = !!(
    displayDescription
    && displayDescription !== displayTitle
    && !displayTitle.includes(displayDescription)
  )
  return {
    ...order,
    displayTitle,
    displayDescription,
    showDescription,
    assigneeLabel: workOrderAssigneeLabel(order),
    locationLabel: locationLabel(displayOrder),
    locationSummary: locationSummaryFrom(displayOrder, locFields),
    showLocationDetail: countFilledLocationParts(locFields) >= 2,
    ...locFields,
    createdLabel: formatDateTimeShort(order.createdAt),
    resolutionSubmittedLabel: resolutionForm
      ? `${resolutionForm.submittedBy} · ${formatDateTimeShort(resolutionForm.submittedAt)}`
      : '',
    resolutionConclusionLabel: resolutionForm?.conclusion ? REVIEW_CONCLUSION_LABELS[resolutionForm.conclusion] : '',
    reviewResultLabel: reviewForm
      ? (reviewForm.result === 'PASS' ? '通过' : '退回')
      : '',
    reviewSubmittedLabel: reviewForm
      ? `${reviewForm.reviewedBy} · ${formatDateTimeShort(reviewForm.reviewedAt)}`
      : '',
  }
}

module.exports = {
  FAULT_TYPE_OPTIONS,
  HANDLING_METHOD_OPTIONS,
  REVIEW_CONCLUSION_OPTIONS,
  REVIEW_CONCLUSION_LABELS,
  CONCLUSIONS_REQUIRING_FOLLOW_UP,
  isPhantomAssignee,
  isWorkOrderUnassigned,
  resolveAssigneeName,
  workOrderAssigneeLabel,
  isInconsistentActiveOrder,
  normalizeWorkOrder,
  canAssignOrder,
  assignActionLabel,
  buildLocationFromAlarm,
  buildLocationContext,
  resolveLocationDescription,
  formatWorkOrderTitle,
  formatBusinessMessage,
  locationLabel,
  resolutionSummary,
  buildReviewFromResolveForm,
  validateResolveFormForBackend,
  enrichWorkOrder,
}
