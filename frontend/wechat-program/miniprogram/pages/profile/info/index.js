const api = require('../../../services/index')
const { profileMenuItems } = require('../../../config/menu')
const { syncTabBar } = require('../../../utils/tab-page')
const { isTabPage } = require('../../../config/tab-bar')
const { ROLE_LABELS } = require('../../../utils/constants')

Page({
  data: {
    user: null,
    roleLabel: '',
    profileMenu: profileMenuItems,
    form: { displayName: '', phone: '', bio: '' },
    editing: false,
    createdLabel: '',
    unreadNotifications: 0,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/profile/info/index')) return
    syncTabBar(this)
    const user = app.globalData.user
    this.setData({
      user,
      roleLabel: ROLE_LABELS[user.role],
      form: { displayName: user.displayName || '', phone: user.phone || '', bio: user.bio || '' },
      createdLabel: user.createdAt ? user.createdAt.slice(0, 16).replace('T', ' ') : '',
      unreadNotifications: app.globalData.unreadNotifications,
    })
    app.refreshBadges()
  },

  toggleEdit() {
    if (this.data.editing) {
      const user = getApp().globalData.user
      this.setData({
        editing: false,
        form: { displayName: user.displayName || '', phone: user.phone || '', bio: user.bio || '' },
      })
    } else {
      this.setData({ editing: true })
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    let value = e.detail.value
    if (field === 'bio' && value.length > 80) {
      value = value.slice(0, 80)
      wx.showToast({ title: '简介最多 80 字', icon: 'none' })
    }
    this.setData({ [`form.${field}`]: value })
  },

  async saveProfile() {
    if (!this.data.form.displayName.trim()) {
      wx.showToast({ title: '请填写姓名', icon: 'none' })
      return
    }
    try {
      const user = await api.updateProfile(this.data.form)
      getApp().setUser(user)
      wx.showToast({ title: '已保存' })
      this.setData({ user, editing: false, roleLabel: ROLE_LABELS[user.role] })
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    }
  },

  go(e) {
    const url = e.currentTarget.dataset.url
    if (isTabPage(url)) wx.switchTab({ url })
    else wx.navigateTo({ url })
  },

  goNotifications() {
    wx.navigateTo({ url: '/pages/notifications/index' })
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确认退出当前账号？',
      success: (res) => {
        if (!res.confirm) return
        api.logout()
        getApp().clearUser()
        wx.redirectTo({ url: '/pages/auth/login/index' })
      },
    })
  },
})
