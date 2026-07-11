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
  ]
    .filter(Boolean)
    .join('；')
}

function enrichWorkOrder(order) {
  const loc = order.location || {}
  const resolutionForm = order.resolutionForm
  const reviewForm = order.reviewForm
  return {
    ...order,
    locationLabel: locationLabel(order),
    locationSite: loc.siteName || '-',
    locationArea: loc.areaName || loc.routeName || '-',
    locationCheckpoint: loc.checkpointName || '路线行进中',
    locationAddress: loc.address || '-',
    resolutionSubmittedLabel: resolutionForm
      ? `${resolutionForm.submittedBy} · ${(resolutionForm.submittedAt || '').slice(0, 16).replace('T', ' ')}`
      : '',
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
  buildLocationFromAlarm,
  locationLabel,
  resolutionSummary,
  enrichWorkOrder,
}
