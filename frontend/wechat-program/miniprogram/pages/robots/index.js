const api = require('../../services/index')

Page({
  data: {
    robots: [],
    sites: [],
    statusStats: [],
    showForm: false,
    form: { name: '', model: '', serialNo: '', siteIndex: 0 },
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/robots/index')) return
    if (!app.requirePermission('robot:manage')) return
    this.load()
  },

  async load() {
    try {
      const [robots, sites] = await Promise.all([api.getRobots(), api.getSites()])
      const list = robots.map((r) => {
        const site = sites.find((s) => s.id === r.siteId)
        return {
          ...r,
          siteName: site ? site.name : '未绑定',
          statusLabel: { ONLINE: '在线', BUSY: '忙碌', CHARGING: '充电中', OFFLINE: '离线' }[r.status] || r.status,
          lastOnlineLabel: r.lastOnlineAt ? r.lastOnlineAt.slice(0, 16).replace('T', ' ') : '-',
        }
      })
      const statusStats = [
        { label: '总数', value: list.length },
        { label: '在线', value: list.filter((r) => r.status === 'ONLINE').length },
        { label: '任务中', value: list.filter((r) => r.status === 'BUSY').length },
        { label: '充电中', value: list.filter((r) => r.status === 'CHARGING').length },
      ]
      this.setData({ robots: list, sites, statusStats })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  toggleForm() {
    this.setData({ showForm: !this.data.showForm, form: { name: '', model: '', serialNo: '', siteIndex: 0 } })
  },

  onInput(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  onSiteChange(e) { this.setData({ 'form.siteIndex': Number(e.detail.value) }) },

  async addRobot() {
    const { name, model, serialNo, siteIndex } = this.data.form
    const site = this.data.sites[siteIndex]
    if (!name.trim()) {
      wx.showToast({ title: '请输入名称', icon: 'none' })
      return
    }
    try {
      await api.addRobot({ name: name.trim(), model, serialNo, siteId: site ? site.id : '' })
      wx.showToast({ title: '已添加' })
      this.setData({ showForm: false })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '添加失败', icon: 'none' })
    }
  },

  remove(e) {
    wx.showModal({
      title: '删除机器人',
      content: '确认删除该机器人？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.removeRobot(e.currentTarget.dataset.id)
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        }
      },
    })
  },
})
