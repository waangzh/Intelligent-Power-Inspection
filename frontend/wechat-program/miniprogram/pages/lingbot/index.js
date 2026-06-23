const api = require('../../services/index')

const STATUS_LABELS = { PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }

Page({
  data: {
    jobs: [],
    sites: [],
    routes: [],
    showForm: false,
    form: { name: '', siteIndex: 0 },
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/lingbot/index')) return
    if (!app.requirePermission('lingbot:manage')) return
    this.load()
  },

  async load() {
    try {
      const [jobs, sites, routes] = await Promise.all([
        api.getLingBotJobs(), api.getSites(), api.getRoutes(),
      ])
      const list = jobs.map((j) => ({
        ...j,
        statusLabel: STATUS_LABELS[j.status] || j.status,
        statusType: j.status === 'COMPLETED' ? 'success' : j.status === 'PROCESSING' ? 'primary' : 'info',
        pointCountLabel: j.pointCount ? `${(j.pointCount / 10000).toFixed(1)}万` : '0',
        createdLabel: j.createdAt ? j.createdAt.slice(0, 16).replace('T', ' ') : '',
      }))
      this.setData({ jobs: list, sites, routes })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  toggleForm() {
    this.setData({ showForm: !this.data.showForm, form: { name: '', siteIndex: 0 } })
  },

  onInput(e) { this.setData({ 'form.name': e.detail.value }) },
  onSiteChange(e) { this.setData({ 'form.siteIndex': Number(e.detail.value) }) },

  async createJob() {
    const site = this.data.sites[this.data.form.siteIndex]
    const name = this.data.form.name.trim()
    if (!site || !name) {
      wx.showToast({ title: '请填写完整信息', icon: 'none' })
      return
    }
    try {
      await api.createLingBotJob(site.id, site.name, name)
      wx.showToast({ title: '任务已创建' })
      this.setData({ showForm: false })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '创建失败', icon: 'none' })
    }
  },

  async simulate(e) {
    try {
      await api.simulateLingBotProgress(e.currentTarget.dataset.id)
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },
})
