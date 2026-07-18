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
  return resolveAssigneeName(order) || '待接单'
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
  if (!loc) return '-'
  if (loc.checkpointName) {
    return `${loc.routeName || ''} · ${loc.checkpointName}`.replace(/^ · | · $/g, '') || '-'
  }
  return loc.areaName || loc.routeName || '-'
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
  const handlingMeasures = form.replacedParts
    ? `现场处理方式为${form.handlingMethod}，更换部件：${form.replacedParts}`
    : `现场处理方式为${form.handlingMethod}`
  return {
    conclusion: form.conclusion,
    onsiteFinding: `现场故障类型为${form.faultType}，试验/复测结果：${form.testResult}`,
    handlingMeasures,
    followUpPlan: form.remarks || undefined,
  }
}

function enrichWorkOrder(order) {
  const loc = order.location || {}
  const resolutionForm = order.resolutionForm
  const reviewForm = order.reviewForm
  return {
    ...order,
    assigneeLabel: workOrderAssigneeLabel(order),
    locationLabel: locationLabel(order),
    locationSite: loc.siteName || '-',
    locationArea: loc.areaName || loc.routeName || '-',
    locationCheckpoint: loc.checkpointName || '路线行进中',
    locationAddress: loc.address || '-',
    resolutionSubmittedLabel: resolutionForm
      ? `${resolutionForm.submittedBy} · ${(resolutionForm.submittedAt || '').slice(0, 16).replace('T', ' ')}`
      : '',
    resolutionConclusionLabel: resolutionForm?.conclusion ? REVIEW_CONCLUSION_LABELS[resolutionForm.conclusion] : '',
    reviewResultLabel: reviewForm
      ? (reviewForm.result === 'PASS' ? '通过' : '退回')
      : '',
    reviewSubmittedLabel: reviewForm
      ? `${reviewForm.reviewedBy} · ${(reviewForm.reviewedAt || '').slice(0, 16).replace('T', ' ')}`
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
  locationLabel,
  resolutionSummary,
  buildReviewFromResolveForm,
  enrichWorkOrder,
}
