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
    this.setData({ canManagePolicy: hasPermission(user.role, 'alarm:policy') })
    this.refreshPolicyRows()
    this.load()
  },

  refreshPolicyRows() {
    this.setData({ policyRows: alarmPolicy.getPolicyRows() })
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

  setPolicyMode(e) {
    const { severity, mode } = e.currentTarget.dataset
    alarmPolicy.setMode(severity, mode)
    this.refreshPolicyRows()
    wx.showToast({ title: '策略已更新', icon: 'success' })
  },
})
