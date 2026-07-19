const api = require('../../../services/index')
const { validatePassword } = require('../../../utils/storage')

Page({
  data: {
    loading: false,
    smsSending: false,
    smsCooldown: 0,
    smsBtnLabel: '发送验证码',
    form: {
      phone: '',
      smsCode: '',
      newPassword: '',
      confirmPassword: '',
    },
  },

  cooldownTimer: null,

  onUnload() {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer)
      this.cooldownTimer = null
    }
  },

  onInput(e) {
    const k = e.currentTarget.dataset.k
    this.setData({ [`form.${k}`]: e.detail.value })
  },

  goLogin() {
    wx.navigateBack()
  },

  validatePhone(phone) {
    const p = (phone || '').trim()
    if (!p) return '请输入手机号'
    if (!/^1\d{10}$/.test(p)) return '手机号格式不正确'
    return null
  },

  formatSmsBtnLabel(cooldown, sending) {
    if (sending) return '发送中...'
    if (cooldown > 0) return `${cooldown}s 后重发`
    return '发送验证码'
  },

  updateSmsBtn(cooldown, sending) {
    this.setData({
      smsCooldown: cooldown,
      smsSending: sending,
      smsBtnLabel: this.formatSmsBtnLabel(cooldown, sending),
    })
  },

  startCooldown(seconds) {
    const sec = Math.max(1, Math.floor(seconds || 30))
    this.updateSmsBtn(sec, false)
    if (this.cooldownTimer) clearInterval(this.cooldownTimer)
    this.cooldownTimer = setInterval(() => {
      const next = this.data.smsCooldown - 1
      if (next <= 0) {
        clearInterval(this.cooldownTimer)
        this.cooldownTimer = null
        this.updateSmsBtn(0, false)
      } else {
        this.updateSmsBtn(next, false)
      }
    }, 1000)
  },

  async handleSendSms() {
    const phoneErr = this.validatePhone(this.data.form.phone)
    if (phoneErr) {
      wx.showToast({ title: phoneErr, icon: 'none' })
      return
    }
    if (this.data.smsSending || this.data.smsCooldown > 0) return

    this.updateSmsBtn(this.data.smsCooldown, true)
    try {
      const result = await api.sendResetPasswordSms(this.data.form.phone.trim())
      wx.showToast({ title: result.message || '验证码已发送' })
      if (result.debugCode) {
        this.setData({ 'form.smsCode': result.debugCode })
        wx.showModal({
          title: '开发模式',
          content: `验证码：${result.debugCode}`,
          showCancel: false,
        })
      }
      this.startCooldown(result.resendIntervalSeconds || 30)
    } catch (e) {
      wx.showToast({ title: e.message || '验证码发送失败', icon: 'none' })
      this.updateSmsBtn(this.data.smsCooldown, false)
    }
  },

  validateForm() {
    const f = this.data.form
    const phoneErr = this.validatePhone(f.phone)
    if (phoneErr) return phoneErr
    const code = (f.smsCode || '').trim()
    if (!code) return '请输入短信验证码'
    if (!/^\d{4,8}$/.test(code)) return '验证码格式不正确'
    const pErr = validatePassword(f.newPassword)
    if (pErr) return pErr
    if (f.newPassword !== f.confirmPassword) return '两次输入的密码不一致'
    return null
  },

  async handleReset() {
    const err = this.validateForm()
    if (err) {
      wx.showToast({ title: err, icon: 'none' })
      return
    }

    const f = this.data.form
    this.setData({ loading: true })
    try {
      await api.resetPassword({
        phone: f.phone.trim(),
        smsCode: f.smsCode.trim(),
        newPassword: f.newPassword,
        confirmPassword: f.confirmPassword,
      })
      wx.showToast({ title: '密码已重置，请登录' })
      setTimeout(() => wx.navigateBack(), 800)
    } catch (e) {
      wx.showToast({ title: e.message || '重置失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
