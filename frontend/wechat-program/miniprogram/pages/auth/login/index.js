const api = require('../../../services/index')
const { getRoleLandingPath } = require('../../../utils/role-landing')

Page({
  data: {
    username: '',
    password: '',
    remember: true,
    loading: false,
    checkingSession: false,
    redirect: '',
  },

  onLoad(options) {
    if (options.redirect) this.setData({ redirect: decodeURIComponent(options.redirect) })
    if (api.getSession()) this.setData({ checkingSession: true })
  },

  onShow() {
    const app = getApp()
    if (!app.globalData.user && !api.getSession()) {
      this.setData({ checkingSession: false, loading: false })
    }
  },

  onUser(e) { this.setData({ username: e.detail.value }) },
  onPass(e) { this.setData({ password: e.detail.value }) },
  toggleRemember() { this.setData({ remember: !this.data.remember }) },

  goRegister() { wx.navigateTo({ url: '/pages/auth/register/index' }) },

  goForgotPassword() { wx.navigateTo({ url: '/pages/auth/forgot-password/index' }) },

  async handleLogin() {
    const { username, password, remember } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true, checkingSession: true })
    try {
      const session = await api.login(username, password, remember)
      const app = getApp()
      app.applySession(session, { reloadPages: false, skipBadges: true })
      const url = this.data.redirect || getRoleLandingPath(session.user.role)
      app.enterMainApp(url)
      app.refreshBadges()
    } catch (e) {
      this.setData({ checkingSession: false })
      wx.showToast({ title: e.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
