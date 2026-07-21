const api = require('../../../services/index')
const { profileMenuItems } = require('../../../config/menu')
const { syncTabBar, openPage } = require('../../../utils/tab-page')
const { ROLE_LABELS } = require('../../../utils/constants')
const { hasPermission } = require('../../../utils/permission')

const AVATAR_MAX_SIZE = 2 * 1024 * 1024

Page({
  data: {
    user: null,
    roleLabel: '',
    profileMenu: profileMenuItems,
    form: { displayName: '', phone: '', bio: '' },
    avatarPreview: '',
    editing: false,
    createdLabel: '',
    unreadNotifications: 0,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/profile/info/index')) return
    syncTabBar(this)
    const user = app.globalData.user
    const permissions = app.globalData.permissions
    this.setData({
      user,
      roleLabel: ROLE_LABELS[user.role],
      form: { displayName: user.displayName || '', phone: user.phone || '', bio: user.bio || '' },
      avatarPreview: user.avatarUrl || '',
      createdLabel: user.createdAt ? user.createdAt.slice(0, 16).replace('T', ' ') : '',
      unreadNotifications: app.globalData.unreadNotifications,
      profileMenu: profileMenuItems.filter((item) => !item.permission || hasPermission(permissions, item.permission)),
    })
    app.refreshBadges()
  },

  toggleEdit() {
    if (this.data.editing) {
      const user = getApp().globalData.user
      this.setData({
        editing: false,
        form: { displayName: user.displayName || '', phone: user.phone || '', bio: user.bio || '' },
        avatarPreview: user.avatarUrl || '',
      })
    } else {
      const user = this.data.user
      this.setData({
        editing: true,
        avatarPreview: user.avatarUrl || '',
      })
    }
  },

  chooseAvatar() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: (res) => {
        const file = res.tempFiles[0]
        if (file.size > AVATAR_MAX_SIZE) {
          wx.showToast({ title: '图片不能超过 2MB', icon: 'none' })
          return
        }
        wx.getFileSystemManager().readFile({
          filePath: file.tempFilePath,
          encoding: 'base64',
          success: (r) => {
            this.setData({ avatarPreview: `data:image/jpeg;base64,${r.data}` })
          },
          fail: () => wx.showToast({ title: '读取图片失败', icon: 'none' }),
        })
      },
    })
  },

  resetAvatar() {
    this.setData({ avatarPreview: '' })
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
      const payload = { ...this.data.form }
      if (this.data.avatarPreview !== (this.data.user.avatarUrl || '')) {
        payload.avatarUrl = this.data.avatarPreview
      }
      const user = await api.updateProfile(payload)
      getApp().setUser(user)
      wx.showToast({ title: '已保存' })
      this.setData({
        user,
        editing: false,
        avatarPreview: user.avatarUrl || '',
        roleLabel: ROLE_LABELS[user.role],
      })
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    }
  },

  go(e) {
    openPage(e.currentTarget.dataset.url)
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
