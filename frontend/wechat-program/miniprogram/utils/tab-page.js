function syncTabBar(page) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (!tabBar) return
  if (typeof tabBar.initTabBar === 'function') tabBar.initTabBar()
  const route = page.route ? `/${page.route}` : ''
  if (route && typeof tabBar.setSelected === 'function') tabBar.setSelected(route)
}

module.exports = { syncTabBar }
