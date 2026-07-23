const api = require('../../../services/index')
const { hasPermission } = require('../../../utils/permission')
const alarmPolicy = require('../../../utils/alarm-policy')
const { getUiPreferences, saveUiPreferences } = require('../../../utils/ui-preferences')

Page({
  data: {
    prefs: {
      notifyAlarm: true,
      notifyTask: true,
      notifySystem: true,
      defaultSiteId: '',
      sidebarCollapsed: false,
    },
    uiPrefs: {
      showGpsTrack: true,
      notificationsUnreadOnly: false,
    },
    canManagePolicy: false,
    policyRows: [],
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/profile/settings/index')) return
    const user = app.globalData.user
    const perms = app.globalData.permissions
    this.setData({ canManagePolicy: hasPermission(perms, 'alarm:policy') })
    this.load()
  },

  async refreshPolicyRows(rules) {
    let policyRules = rules
    if (!policyRules) {
      try {
        const policy = await api.getAlarmWorkOrderPolicy()
        policyRules = policy.rules
      } catch (e) {
        policyRules = alarmPolicy.DEFAULT_POLICY
      }
    }
    const { ALARM_SEVERITY_LABELS } = require('../../../utils/constants')
    const rows = alarmPolicy.POLICY_ROWS.map((row) => ({
      ...row,
      label: ALARM_SEVERITY_LABELS[row.severity],
      mode: policyRules[row.severity],
      sevType: row.severity === 'CRITICAL' ? 'danger' : row.severity === 'HIGH' ? 'warning' : 'info',
    }))
    this.setData({ policyRows: rows })
  },

  async load() {
    try {
      const prefs = await api.getPreferences()
      this.setData({
        prefs: {
          notifyAlarm: !!prefs?.notifyAlarm,
          notifyTask: !!prefs?.notifyTask,
          notifySystem: !!prefs?.notifySystem,
          defaultSiteId: prefs?.defaultSiteId || '',
          sidebarCollapsed: !!prefs?.sidebarCollapsed,
        },
        uiPrefs: getUiPreferences(),
      })
      if (this.data.canManagePolicy) await this.refreshPolicyRows()
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  async onSwitch(e) {
    const key = e.currentTarget.dataset.key
    const prefs = { ...this.data.prefs, [key]: e.detail.value }
    this.setData({ prefs })
    try {
      await api.savePreferences(prefs)
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
      this.load()
    }
  },

  onUiSwitch(e) {
    const key = e.currentTarget.dataset.key
    const uiPrefs = saveUiPreferences({ [key]: e.detail.value })
    this.setData({ uiPrefs })
  },

  async setPolicyMode(e) {
    const { severity, mode } = e.currentTarget.dataset
    try {
      const current = await api.getAlarmWorkOrderPolicy()
      const rules = { ...current.rules, [severity]: mode }
      const updated = await api.updateAlarmWorkOrderPolicy(rules)
      await this.refreshPolicyRows(updated.rules)
      wx.showToast({ title: '策略已更新', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    }
  },
})
