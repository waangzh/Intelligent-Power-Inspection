const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')

Page({
  data: {
    records: [],
    filtered: [],
    keyword: '',
    alarmFilter: '',
    detail: null,
    showDetail: false,
    checkpointRows: [],
    canExport: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/records/index')) return
    this.setData({ canExport: hasPermission(app.globalData.user.role, 'record:export') })
    this.load()
  },

  async load() {
    const records = (await api.getRecords()).map((r) => ({
      ...r,
      time: r.completedAt ? r.completedAt.slice(0, 16).replace('T', ' ') : '',
    }))
    this.setData({ records })
    this.applyFilter()
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
    this.applyFilter()
  },

  setAlarmFilter(e) {
    this.setData({ alarmFilter: e.currentTarget.dataset.v })
    this.applyFilter()
  },

  applyFilter() {
    const { records, keyword, alarmFilter } = this.data
    let list = records
    const kw = keyword.trim().toLowerCase()
    if (kw) {
      list = list.filter((r) =>
        (r.taskName || '').toLowerCase().includes(kw) ||
        (r.routeName || '').toLowerCase().includes(kw))
    }
    if (alarmFilter === 'has') list = list.filter((r) => r.alarmCount > 0)
    if (alarmFilter === 'none') list = list.filter((r) => r.alarmCount === 0)
    this.setData({ filtered: list })
  },

  openDetail(e) {
    const detail = this.data.records.find((r) => r.id === e.currentTarget.dataset.id)
    const checkpointRows = Array.from({ length: detail.checkpointCount || 3 }, (_, i) => ({
      name: `检查点 ${i + 1}`,
      result: i < (detail.checkpointCount - detail.alarmCount) ? '正常' : '异常',
      alarm: i >= (detail.checkpointCount - detail.alarmCount) ? '有' : '无',
    }))
    this.setData({ detail, checkpointRows, showDetail: true })
  },

  closeDetail() { this.setData({ showDetail: false }) },

  exportRecords() {
    wx.showToast({ title: 'Excel 已导出（演示）' })
  },

  exportPdf() {
    wx.showToast({ title: 'PDF 报告已生成（演示）' })
  },
})
