const api = require('../../../services/index')
const { hasPermission } = require('../../../utils/permission')
const alarmPolicy = require('../../../utils/alarm-policy')

Page({
  data: {
    prefs: {
      notifyAlarm: true,
      notifyTask: true,
      notifySystem: true,
      sidebarCollapsed: false,
      defaultSiteId: '',
    },
    sites: [],
    siteIndex: 0,
    siteLabel: '未设置',
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
        policyRules = alarmPolicy.loadPolicy()
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
      const [prefs, sites] = await Promise.all([api.getPreferences(), api.getSites()])
      const siteIndex = prefs.defaultSiteId
        ? Math.max(0, sites.findIndex((s) => s.id === prefs.defaultSiteId))
        : -1
      const siteLabel = siteIndex >= 0 ? sites[siteIndex].name : '未设置'
      this.setData({
        prefs: { ...prefs, defaultSiteId: prefs.defaultSiteId || '' },
        sites,
        siteIndex: siteIndex >= 0 ? siteIndex : 0,
        siteLabel,
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

  onSiteChange(e) {
    const idx = Number(e.detail.value)
    const site = this.data.sites[idx]
    const prefs = { ...this.data.prefs, defaultSiteId: site ? site.id : '' }
    this.setData({ prefs, siteIndex: idx, siteLabel: site ? site.name : '未设置' })
    api.savePreferences(prefs).catch(() => {
      wx.showToast({ title: '保存失败', icon: 'none' })
      this.load()
    })
  },

  clearSite() {
    const prefs = { ...this.data.prefs, defaultSiteId: '' }
    this.setData({ prefs, siteLabel: '未设置' })
    api.savePreferences(prefs)
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
