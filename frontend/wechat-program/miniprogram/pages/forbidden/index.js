Page({
  goBack() {
    const pages = getCurrentPages()
    if (pages.length > 1) wx.navigateBack()
    else wx.switchTab({ url: '/pages/dashboard/index' })
  },

  goHome() {
    wx.switchTab({ url: '/pages/dashboard/index' })
  },
})
