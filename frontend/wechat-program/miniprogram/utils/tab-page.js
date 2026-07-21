const { openPage: openTabPage } = require('../config/tab-bar')
const { dismissKeyForRoute } = require('./tab-badge')
const { resolveSession } = require('./session-user')

function syncTabBar(page) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (!tabBar) return
  if (typeof tabBar.initTabBar === 'function') {
    tabBar.initTabBar()
    return
  }
  const route = page.route ? `/${page.route}` : ''
  if (route && typeof tabBar.setSelected === 'function') tabBar.setSelected(route)
  if (typeof tabBar.syncBadges === 'function') {
    tabBar.syncBadges()
  } else {
    const app = getApp()
    if (app?.applyTabBarBadges) app.applyTabBarBadges()
  }
}

function refreshTabBarBadges(page, options = {}) {
  syncTabBar(page)
  const app = getApp()
  if (!app?.refreshBadges) return Promise.resolve()
  return app.refreshBadges().then(() => {
    if (options.dismissKey && app.dismissTabBadge) {
      app.dismissTabBadge(options.dismissKey)
    } else if (page?.route) {
      const dismissKey = dismissKeyForRoute(page.route)
      if (dismissKey && options.dismissOnShow && app.dismissTabBadge) {
        app.dismissTabBadge(dismissKey)
      }
    }
    syncTabBar(page)
    app.refreshCurrentInlineTabBar?.()
  })
}

function openPage(url) {
  const { role, permissions } = resolveSession()
  openTabPage(url, permissions, role)
}

module.exports = { syncTabBar, refreshTabBarBadges, openPage }
