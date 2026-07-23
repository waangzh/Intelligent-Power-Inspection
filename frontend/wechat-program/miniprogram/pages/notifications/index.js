const api = require('../../services/index')
const { NOTIFICATION_TYPE_LABELS } = require('../../utils/constants')
const { formatBusinessMessage } = require('../../utils/display-text')
const { formatDateTimeShort } = require('../../utils/date-time')
const { getUiPreferences } = require('../../utils/ui-preferences')

const TYPE_OPTIONS = [
  { value: '', label: '全部类型' },
  { value: 'ALARM', label: '告警' },
  { value: 'TASK', label: '任务' },
  { value: 'WORKORDER', label: '工单' },
  { value: 'SYSTEM', label: '系统' },
]

Page({
  data: {
    list: [],
    filtered: [],
    typeFilter: '',
    typeLabel: '全部类型',
    typeOptions: TYPE_OPTIONS,
    unreadOnly: false,
    showDetail: false,
    detail: null,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/notifications/index')) return
    const unreadOnly = getUiPreferences().notificationsUnreadOnly
    this.setData({ unreadOnly }, () => {
      this.load()
      app.refreshBadges()
    })
  },

  async load() {
    try {
      const userId = getApp().globalData.user.id
      const list = (await api.getNotifications(userId)).map((n) => ({
        ...n,
        typeLabel: NOTIFICATION_TYPE_LABELS[n.type] || n.type,
        time: formatDateTimeShort(n.createdAt),
        content: formatBusinessMessage(n.content),
      }))
      this.setData({ list })
      this.applyFilter()
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  applyFilter() {
    const { list, typeFilter, unreadOnly } = this.data
    let filtered = list
    if (typeFilter) filtered = filtered.filter((n) => n.type === typeFilter)
    if (unreadOnly) filtered = filtered.filter((n) => !n.read)
    this.setData({ filtered })
  },

  onTypeChange(e) {
    const opt = TYPE_OPTIONS[e.detail.value]
    this.setData({ typeFilter: opt.value, typeLabel: opt.label })
    this.applyFilter()
  },

  toggleUnread() {
    this.setData({ unreadOnly: !this.data.unreadOnly })
    this.applyFilter()
  },

  stop() {},

  async markRead(e) {
    const id = e.currentTarget.dataset.id
    try {
      await api.markNotificationRead(id)
      this.load()
      getApp().refreshBadges()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  remove(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除消息',
      content: '确认删除该消息？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.removeNotification(id)
          wx.showToast({ title: '已删除' })
          this.load()
          getApp().refreshBadges()
        } catch (err) {
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        }
      },
    })
  },

  async markAllRead() {
    try {
      await api.markAllNotificationsRead(getApp().globalData.user.id)
      wx.showToast({ title: '已全部标为已读' })
      this.load()
      getApp().refreshBadges()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  async openItem(e) {
    const id = e.currentTarget.dataset.id
    let item = this.data.filtered.find((n) => n.id === id) || this.data.list.find((n) => n.id === id)
    if (!item) return
    if (!item.read) {
      try {
        await api.markNotificationRead(item.id)
        item = { ...item, read: true }
        const list = this.data.list.map((n) => (n.id === item.id ? item : n))
        this.setData({ list })
        this.applyFilter()
        getApp().refreshBadges()
      } catch (err) {
        wx.showToast({ title: err.message || '标记已读失败', icon: 'none' })
      }
    }
    this.setData({
      showDetail: true,
      detail: item,
    })
  },

  closeDetail() {
    this.setData({ showDetail: false, detail: null })
  },
})
