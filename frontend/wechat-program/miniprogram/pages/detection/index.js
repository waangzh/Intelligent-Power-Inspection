const api = require('../../services/index')
const { DETECTION_LABELS } = require('../../utils/constants')

Page({
  data: {
    templates: [],
    showForm: false,
    form: { name: '', scope: 'ROUTE', description: '' },
    scopeOptions: ['ROUTE', 'CHECKPOINT'],
    scopeLabelList: ['路线级', '检查点级'],
    scopeLabels: { ROUTE: '路线级', CHECKPOINT: '检查点级' },
    typeLabels: DETECTION_LABELS,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/detection/index')) return
    if (!app.requirePermission('detection:manage')) return
    this.load()
  },

  async load() {
    try {
      const templates = (await api.getDetectionTemplates()).map((t) => ({
        ...t,
        scopeLabel: t.scope === 'ROUTE' ? '路线级' : '检查点级',
        typeLabels: (t.types || []).map((type) => DETECTION_LABELS[type] || type).join('、'),
        promptList: Object.entries(t.prompts || {}).map(([key, value]) => ({ key, value })),
      }))
      this.setData({ templates })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  toggleForm() {
    this.setData({ showForm: !this.data.showForm, form: { name: '', scope: 'ROUTE', description: '' } })
  },

  onInput(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  onScopeChange(e) {
    this.setData({ 'form.scope': this.data.scopeOptions[e.detail.value] })
  },

  async addTemplate() {
    const { name, scope, description } = this.data.form
    if (!name.trim()) {
      wx.showToast({ title: '请输入模板名称', icon: 'none' })
      return
    }
    const types = scope === 'ROUTE' ? ['PERSON', 'HELMET', 'OBSTACLE', 'FIRE'] : ['SWITCH', 'METER', 'OIL_LEAK']
    try {
      await api.addDetectionTemplate({ name: name.trim(), scope, description, types, prompts: {} })
      wx.showToast({ title: '已添加' })
      this.setData({ showForm: false })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '添加失败', icon: 'none' })
    }
  },

  openDoc() {
    wx.setClipboardData({
      data: 'https://huggingface.co/nvidia/LocateAnything-3B',
      success: () => wx.showToast({ title: '文档链接已复制', icon: 'none' }),
    })
  },

  remove(e) {
    wx.showModal({
      title: '删除模板',
      content: '确认删除该检测模板？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.removeDetectionTemplate(e.currentTarget.dataset.id)
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        }
      },
    })
  },
})
