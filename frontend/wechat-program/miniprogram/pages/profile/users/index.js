const api = require('../../../services/index')
const { ROLE_LABELS } = require('../../../utils/constants')
const { USER_ROLE_VALUES } = require('../../../generated/domain-enums')
const { syncTabBar } = require('../../../utils/tab-page')

const ROLE_OPTIONS = USER_ROLE_VALUES.map((value) => ({ value, label: ROLE_LABELS[value] || value }))

Page({
  data: {
    users: [],
    roleOptions: ROLE_OPTIONS,
    loading: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/profile/users/index')) return
    if (!app.requirePermission('user:manage', ['ADMIN'])) return
    syncTabBar(this)
    app.refreshBadges()
    this.load()
  },

  decorate(user, currentUserId) {
    const roleIndex = ROLE_OPTIONS.findIndex((o) => o.value === user.role)
    const enabled = user.enabled !== false
    return {
      ...user,
      roleLabel: ROLE_LABELS[user.role] || user.role,
      roleIndex: roleIndex < 0 ? 0 : roleIndex,
      enabled,
      statusLabel: enabled ? '已启用' : '已禁用',
      createdLabel: user.createdAt ? user.createdAt.slice(0, 16).replace('T', ' ') : '',
      isSelf: user.id === currentUserId,
    }
  },

  async load() {
    this.setData({ loading: true })
    try {
      const currentUserId = getApp().globalData.user.id
      const list = await api.listUsers()
      const users = list
        .slice()
        .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
        .map((u) => this.decorate(u, currentUserId))
      this.setData({ users })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async onRoleChange(e) {
    const { id } = e.currentTarget.dataset
    const option = ROLE_OPTIONS[Number(e.detail.value)]
    if (!option) return
    try {
      await api.updateUserRole(id, option.value)
      wx.showToast({ title: '角色已更新' })
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '更新失败', icon: 'none' })
    }
  },

  toggleEnabled(e) {
    const { id } = e.currentTarget.dataset
    const currentlyEnabled = e.currentTarget.dataset.enabled === true || e.currentTarget.dataset.enabled === 'true'
    const nextEnabled = !currentlyEnabled
    wx.showModal({
      title: nextEnabled ? '启用用户' : '禁用用户',
      content: nextEnabled ? '确认启用该用户？' : '禁用后该用户将无法登录系统，确认禁用？',
      confirmColor: nextEnabled ? '#1768f2' : '#f04438',
      success: (res) => {
        if (!res.confirm) return
        api.toggleUserEnabled(id, nextEnabled)
          .then(() => {
            wx.showToast({ title: nextEnabled ? '已启用' : '已禁用' })
            this.load()
          })
          .catch((err) => {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          })
      },
    })
  },
})
