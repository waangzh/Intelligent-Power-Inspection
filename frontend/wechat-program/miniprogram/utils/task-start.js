function startModeReason(eligibility, startMode) {
  if (!eligibility) return '正在核验启动条件'
  if (startMode === 'REMOTE_IMMEDIATE') {
    return eligibility.remoteImmediateStartIneligibleReason
      || eligibility.ineligibleReason
      || '远程立即启动条件未满足'
  }
  return eligibility.localConfirmStartIneligibleReason
    || eligibility.ineligibleReason
    || '本地确认启动条件未满足'
}

function isStartModeEligible(eligibility, startMode) {
  if (!eligibility) return false
  return startMode === 'REMOTE_IMMEDIATE'
    ? !!eligibility.remoteImmediateStartEligible
    : !!eligibility.localConfirmStartEligible
}

function buildStartModeOptions(eligibility, { canStartRemote = false, canStartLocal = false } = {}) {
  const options = []
  if (canStartRemote) {
    options.push({
      label: '远程立即启动',
      mode: 'REMOTE_IMMEDIATE',
      eligible: isStartModeEligible(eligibility, 'REMOTE_IMMEDIATE'),
      reason: startModeReason(eligibility, 'REMOTE_IMMEDIATE'),
    })
  }
  if (canStartLocal) {
    options.push({
      label: '下发并等待本地确认',
      mode: 'LOCAL_CONFIRM',
      eligible: isStartModeEligible(eligibility, 'LOCAL_CONFIRM'),
      reason: startModeReason(eligibility, 'LOCAL_CONFIRM'),
    })
  }
  return options
}

function formatStartBlockMessage(eligibility, options) {
  if (!options.length) return '当前账号没有任务启动权限'
  const lines = options.map((item) => `${item.label}：${item.reason}`)
  if (eligibility?.ineligibleReason && !lines.length) return eligibility.ineligibleReason
  return lines.join('\n')
}

function showTaskError(message, title = '操作失败') {
  const text = String(message || '操作失败').trim()
  if (text.length > 18) {
    wx.showModal({ title, content: text, showCancel: false })
    return
  }
  wx.showToast({ title: text, icon: 'none' })
}

function assertStartEligible(eligibility, startMode) {
  if (isStartModeEligible(eligibility, startMode)) return
  const err = new Error(startModeReason(eligibility, startMode))
  err.code = 'START_INELIGIBLE'
  throw err
}

module.exports = {
  startModeReason,
  isStartModeEligible,
  buildStartModeOptions,
  formatStartBlockMessage,
  showTaskError,
  assertStartEligible,
}
