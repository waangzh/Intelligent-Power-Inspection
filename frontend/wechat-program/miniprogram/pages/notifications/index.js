const api = require('../../services/index')
const { mapNotificationLink } = require('../../config/menu')
const { isTabPage } = require('../../config/tab-bar')
const { NOTIFICATION_TYPE_LABELS } = require('../../utils/constants')
const { formatBusinessMessage } = require('../../utils/display-text')
const { formatDateTimeShort } = require('../../utils/date-time')

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
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/notifications/index')) return
    this.load()
    app.refreshBadges()
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
    const item = this.data.list.find((n) => n.id === e.currentTarget.dataset.id)
    if (!item) return
    if (!item.read) await api.markNotificationRead(item.id)
    getApp().refreshBadges()
    if (item.link) {
      const role = getApp().globalData.user?.role
      const path = mapNotificationLink(item.link, role)
      if (isTabPage(path)) wx.switchTab({ url: path.split('?')[0] })
      else wx.navigateTo({ url: path })
    }
    this.load()
  },
})
