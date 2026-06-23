const api = require('../../../services/index')
const { ROLE_LABELS } = require('../../../utils/constants')

Page({
  data: {
    form: { oldPassword: '', newPassword: '', confirmPassword: '' },
    username: '',
    roleLabel: '',
    updatedLabel: '',
  },

  onShow() {
    if (!getApp().requireAuth('/pages/profile/security/index')) return
    const user = getApp().globalData.user
    const updated = user.updatedAt || user.createdAt
    this.setData({
      username: user.username,
      roleLabel: ROLE_LABELS[user.role],
      updatedLabel: updated ? updated.slice(0, 19).replace('T', ' ') : '-',
    })
  },

  onInput(e) {
    this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value })
  },

  resetForm() {
    this.setData({ form: { oldPassword: '', newPassword: '', confirmPassword: '' } })
  },

  async submit() {
    const { oldPassword, newPassword, confirmPassword } = this.data.form
    if (!oldPassword || !newPassword) {
      wx.showToast({ title: '请填写完整', icon: 'none' })
      return
    }
    try {
      await api.changePassword({ oldPassword, newPassword, confirmPassword })
      wx.showToast({ title: '密码已修改' })
      this.resetForm()
    } catch (e) {
      wx.showToast({ title: e.message || '修改失败', icon: 'none' })
    }
  },
})
