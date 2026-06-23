const api = require('../../services/index')
const { ROLE_LABELS } = require('../../utils/constants')

const ROLE_OPTIONS = ['ADMIN', 'DISPATCHER', 'VIEWER']
const ROLE_TABLE = [
  { role: '管理员', desc: '系统全权管理', perms: '全部功能 + 用户管理' },
  { role: '调度员', desc: '日常运维调度', perms: '任务下发/控制、站点路线编辑、告警确认' },
  { role: '观察员', desc: '只读浏览', perms: '查看监控、告警、记录，不可操作任务' },
]

Page({
  data: {
    users: [],
    currentUserId: '',
    roleOptions: ROLE_OPTIONS,
    roleLabelList: ROLE_OPTIONS.map((r) => ROLE_LABELS[r]),
    roleLabels: ROLE_LABELS,
    roleTable: ROLE_TABLE,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/users/index')) return
    if (!app.requirePermission('user:manage', ['ADMIN'])) return
    this.setData({ currentUserId: app.globalData.user.id })
    this.load()
  },

  async load() {
    try {
      const currentUserId = getApp().globalData.user.id
      const users = (await api.listUsers()).map((u) => ({
        ...u,
        roleLabel: ROLE_LABELS[u.role],
        isSelf: u.id === currentUserId,
        createdLabel: u.createdAt ? u.createdAt.slice(0, 16).replace('T', ' ') : '',
        bioText: u.bio || '-',
        phoneText: u.phone || '-',
      }))
      this.setData({ users, currentUserId })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  async changeRole(e) {
    const userId = e.currentTarget.dataset.id
    if (userId === this.data.currentUserId) {
      wx.showToast({ title: '不能修改自己的角色', icon: 'none' })
      return
    }
    const role = ROLE_OPTIONS[e.detail.value]
    const user = this.data.users.find((u) => u.id === userId)
    if (!user || user.role === role) return
    wx.showModal({
      title: '修改角色',
      content: `将 ${user.displayName} 的角色改为 ${ROLE_LABELS[role]}？`,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.updateUserRole(userId, role)
          wx.showToast({ title: '已更新' })
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '更新失败', icon: 'none' })
        }
      },
    })
  },
})
