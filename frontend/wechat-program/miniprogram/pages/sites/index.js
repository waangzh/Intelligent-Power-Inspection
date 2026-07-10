const api = require('../../services/index')

Page({
  data: {
    sites: [],
    areas: [],
    selectedId: '',
    currentSite: null,
    siteAreas: [],
    showForm: false,
    editingId: '',
    form: { name: '', address: '', description: '', lat: '30.2741', lng: '120.1551' },
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/sites/index')) return
    if (!app.requirePermission('site:edit')) return
    this.load()
  },

  async load() {
    const [sites, areas] = await Promise.all([api.getSites(), api.getAreas()])
    this.setData({ sites, areas })
    if (this.data.selectedId) this.selectSite({ currentTarget: { dataset: { id: this.data.selectedId } } })
    else if (sites.length) this.selectSite({ currentTarget: { dataset: { id: sites[0].id } } })
  },

  selectSite(e) {
    const id = e.currentTarget.dataset.id
    const currentSite = this.data.sites.find((s) => s.id === id)
    const siteAreas = this.data.areas.filter((a) => a.siteId === id)
    this.setData({ selectedId: id, currentSite, siteAreas })
  },

  stop() {},

  openAdd() {
    this.setData({
      showForm: true,
      editingId: '',
      form: { name: '', address: '', description: '', lat: '30.2741', lng: '120.1551' },
    })
  },

  openEdit(e) {
    const site = this.data.sites.find((s) => s.id === e.currentTarget.dataset.id)
    if (!site) return
    this.setData({
      showForm: true,
      editingId: site.id,
      form: {
        name: site.name,
        address: site.address || '',
        description: site.description || '',
        lat: String(site.center?.lat ?? 30.2741),
        lng: String(site.center?.lng ?? 120.1551),
      },
    })
  },

  closeForm() { this.setData({ showForm: false }) },

  onInput(e) {
    this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value })
  },

  async save() {
    const { form, editingId } = this.data
    if (!form.name.trim()) {
      wx.showToast({ title: '请输入站点名称', icon: 'none' })
      return
    }
    const site = {
      id: editingId || undefined,
      name: form.name.trim(),
      address: form.address,
      description: form.description,
      center: { lat: parseFloat(form.lat) || 30.2741, lng: parseFloat(form.lng) || 120.1551 },
      createdAt: editingId ? undefined : new Date().toISOString(),
    }
    await api.saveSite(site)
    wx.showToast({ title: '已保存' })
    this.setData({ showForm: false })
    this.load()
  },

  remove(e) {
    wx.showModal({
      title: '删除站点',
      content: '确认删除？关联区域将一并删除。',
      success: async (res) => {
        if (!res.confirm) return
        await api.removeSite(e.currentTarget.dataset.id)
        this.setData({ selectedId: '', currentSite: null })
        this.load()
      },
    })
  },

  async addArea() {
    const site = this.data.currentSite
    if (!site) return
    wx.showModal({
      title: '添加区域',
      editable: true,
      placeholderText: '区域名称',
      success: async (res) => {
        if (!res.confirm || !res.content) return
        const c = site.center
        const d = 0.00015
        await api.addArea({
          siteId: site.id,
          name: res.content.trim(),
          polygon: [
            { lat: c.lat + d, lng: c.lng - d },
            { lat: c.lat + d, lng: c.lng + d },
            { lat: c.lat - d, lng: c.lng + d },
            { lat: c.lat - d, lng: c.lng - d },
          ],
        })
        this.load()
      },
    })
  },

  async removeArea(e) {
    await api.removeArea(e.currentTarget.dataset.id)
    this.load()
  },
})
