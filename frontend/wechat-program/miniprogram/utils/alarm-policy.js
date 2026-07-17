const { loadFromStorage, saveToStorage } = require('./storage')

/**
 * 告警转工单策略 — 仅 Mock 模式使用本地 wx.storage。
 * 真实后端请通过 services/index.js 的 get/updateAlarmWorkOrderPolicy 访问 /alarms/work-order-policy。
 */

const STORAGE_KEY = 'pi_alarm_escalation_policy'

const DEFAULT_POLICY = {
  CRITICAL: 'AUTO',
  HIGH: 'AUTO',
  MEDIUM: 'MANUAL',
  LOW: 'MANUAL',
}

const POLICY_ROWS = [
  { severity: 'CRITICAL', hint: '紧急告警到达后自动创建工单' },
  { severity: 'HIGH', hint: '高级别告警可自动或人工转工单' },
  { severity: 'MEDIUM', hint: '中级告警建议人工确认后转工单' },
  { severity: 'LOW', hint: '低级告警默认仅记录' },
]

function loadPolicy() {
  const saved = loadFromStorage(STORAGE_KEY, null)
  if (!saved || typeof saved !== 'object') return { ...DEFAULT_POLICY }
  return { ...DEFAULT_POLICY, ...saved }
}

function savePolicy(policy) {
  saveToStorage(STORAGE_KEY, policy)
}

function shouldAutoConvert(severity, policy) {
  return (policy || loadPolicy())[severity] === 'AUTO'
}

function setMode(severity, mode) {
  const policy = loadPolicy()
  policy[severity] = mode
  savePolicy(policy)
  return policy
}

function getPolicyRows(rules) {
  const policy = rules || loadPolicy()
  const { ALARM_SEVERITY_LABELS } = require('./constants')
  return POLICY_ROWS.map((row) => ({
    ...row,
    label: ALARM_SEVERITY_LABELS[row.severity],
    mode: policy[row.severity],
    sevType: row.severity === 'CRITICAL' ? 'danger' : row.severity === 'HIGH' ? 'warning' : 'info',
  }))
}

module.exports = {
  DEFAULT_POLICY,
  POLICY_ROWS,
  loadPolicy,
  savePolicy,
  shouldAutoConvert,
  setMode,
  getPolicyRows,
}
