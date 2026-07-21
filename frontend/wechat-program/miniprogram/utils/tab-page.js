const { openPage: openTabPage } = require('../config/tab-bar')

function syncTabBar(page) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (!tabBar) return
  const route = page.route ? `/${page.route}` : ''
  if (route && typeof tabBar.setSelected === 'function') tabBar.setSelected(route)
}

function openPage(url) {
  const app = getApp()
  openTabPage(url, app.globalData.permissions, app.globalData.user?.role)
}

module.exports = { syncTabBar, openPage }
