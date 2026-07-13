const api = require('../../../services/index')
const { getRoleLandingPath } = require('../../../utils/role-landing')
const { isTabPage } = require('../../../config/tab-bar')

Page({
  data: { username: '', password: '', remember: true, loading: false, redirect: '' },

  onLoad(options) {
    const app = getApp()
    if (app.globalData.user) {
      wx.switchTab({ url: '/pages/dashboard/index' })
      return
    }
    if (options.redirect) this.setData({ redirect: decodeURIComponent(options.redirect) })
  },

  onUser(e) { this.setData({ username: e.detail.value }) },
  onPass(e) { this.setData({ password: e.detail.value }) },
  toggleRemember() { this.setData({ remember: !this.data.remember }) },

  fillDemo(e) {
    this.setData({ username: e.currentTarget.dataset.u, password: e.currentTarget.dataset.p })
  },

  goRegister() { wx.navigateTo({ url: '/pages/auth/register/index' }) },

  async handleLogin() {
    const { username, password, remember } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const session = await api.login(username, password, remember)
      getApp().setUser(session.user)
      wx.showToast({ title: '登录成功' })
      const url = this.data.redirect || getRoleLandingPath(session.user.role)
      if (isTabPage(url)) {
        wx.switchTab({ url: url.split('?')[0] })
      } else {
        wx.redirectTo({ url })
      }
    } catch (e) {
      wx.showToast({ title: e.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
