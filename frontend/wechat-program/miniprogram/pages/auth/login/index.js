const api = require('../../../services/index')
const { getRoleLandingPath } = require('../../../utils/role-landing')

Page({
  data: {
    username: '',
    password: '',
    remember: true,
    loading: false,
    entering: false,
    enterText: '正在进入系统…',
    redirect: '',
  },

  onLoad(options) {
    if (options.redirect) this.setData({ redirect: decodeURIComponent(options.redirect) })
    if (api.getSession()) {
      this.setData({ entering: true, enterText: '正在进入系统…' })
      // 自动登录超时则回到登录表单，避免一直卡在「正在进入系统」
      this._enterTimer = setTimeout(() => {
        if (this.data.entering && !getApp().globalData.user) {
          this.setData({ entering: false, loading: false })
          wx.showToast({ title: '自动登录失败，请手动登录', icon: 'none' })
        }
      }, 8000)
    }
  },

  onUnload() {
    if (this._enterTimer) clearTimeout(this._enterTimer)
  },

  onShow() {
    const app = getApp()
    if (!app.globalData.user && !api.getSession()) {
      this.setData({ entering: false, loading: false })
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
    this.setData({ loading: true, entering: true, enterText: '正在登录…' })
    try {
      const app = getApp()
      app.clearUser({ redirect: false })
      const session = await api.login(username, password, remember)
      const url = this.data.redirect || getRoleLandingPath(session.user.role)
      app.applySession(session, {
        reloadPages: false,
        skipBadges: true,
        relaunch: true,
        landingUrl: url,
      })
      await app.refreshBadges()
    } catch (e) {
      this.setData({ entering: false, loading: false })
      wx.showToast({ title: e.message || '登录失败', icon: 'none' })
    }
  },
})
