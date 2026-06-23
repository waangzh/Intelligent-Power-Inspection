const api = require('../../../services/index')

Page({
  data: {
    loading: false,
    form: { username: '', displayName: '', phone: '', password: '', confirmPassword: '', agreed: false },
  },
  onInput(e) {
    const k = e.currentTarget.dataset.k
    this.setData({ [`form.${k}`]: e.detail.value })
  },
  toggleAgreed() { this.setData({ 'form.agreed': !this.data.form.agreed }) },
  goLogin() { wx.navigateBack() },
  async handleRegister() {
    this.setData({ loading: true })
    try {
      await api.register(this.data.form)
      wx.showToast({ title: '注册成功' })
      setTimeout(() => wx.navigateBack(), 800)
    } catch (e) {
      wx.showToast({ title: e.message || '注册失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
