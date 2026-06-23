const api = require('../../../services/index')

const AVATAR_MAX_SIZE = 2 * 1024 * 1024

Page({
  data: {
    user: null,
    preview: '',
    dirty: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/profile/avatar/index')) return
    const user = app.globalData.user
    this.setData({ user, preview: user.avatarUrl || '', dirty: false })
  },

  chooseImage() {
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
            const preview = `data:image/jpeg;base64,${r.data}`
            this.setData({ preview, dirty: true })
          },
          fail: () => wx.showToast({ title: '读取图片失败', icon: 'none' }),
        })
      },
    })
  },

  async resetDefault() {
    const user = this.data.user
    const preview = api.generateDefaultAvatar(user.displayName, user.id)
    this.setData({ preview, dirty: preview !== user.avatarUrl })
  },

  async save() {
    if (!this.data.preview) {
      wx.showToast({ title: '请先选择图片', icon: 'none' })
      return
    }
    try {
      const user = await api.updateProfile({ avatarUrl: this.data.preview })
      getApp().setUser(user)
      wx.showToast({ title: '头像已保存' })
      this.setData({ dirty: false })
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    }
  },
})
