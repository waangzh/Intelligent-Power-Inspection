const { hasPermission } = require('../utils/permission')

const TAB_DASHBOARD = {
  pagePath: '/pages/dashboard/index',
  text: '总览',
  iconPath: '/assets/tabbar/dashboard.png',
  selectedIconPath: '/assets/tabbar/dashboard-active.png',
}

const TAB_DASHBOARD_ALARM_BADGE = {
  ...TAB_DASHBOARD,
  badgeKey: 'alarms',
}

const TAB_MONITOR = {
  pagePath: '/pages/monitor/index',
  text: '监控',
  iconPath: '/assets/tabbar/monitor.png',
  selectedIconPath: '/assets/tabbar/monitor-active.png',
}

const TAB_WORKORDERS = {
  pagePath: '/pages/workorders/index',
  text: '工单',
  iconPath: '/assets/tabbar/workorders.png',
  selectedIconPath: '/assets/tabbar/workorders-active.png',
  badgeKey: 'workorders',
}

const TAB_ALARMS = {
  pagePath: '/pages/alarms/index',
  text: '告警',
  iconPath: '/assets/tabbar/alarms.png',
  selectedIconPath: '/assets/tabbar/alarms-active.png',
  badgeKey: 'alarms',
}

const TAB_TASKS = {
  pagePath: '/pages/tasks/index',
  text: '任务',
  iconPath: '/assets/tabbar/schedule.png',
  selectedIconPath: '/assets/tabbar/schedule-active.png',
}

const TAB_USERS = {
  pagePath: '/pages/profile/users/index',
  text: '用户',
  iconPath: '/assets/tabbar/users.png',
  selectedIconPath: '/assets/tabbar/users-active.png',
}

const TAB_PROFILE = {
  pagePath: '/pages/profile/info/index',
  text: '我的',
  iconPath: '/assets/tabbar/profile.png',
  selectedIconPath: '/assets/tabbar/profile-active.png',
  badgeKey: 'profile',
}

/** 管理员/调度员告警中心（非底部 Tab，带仿底栏） */
const ADMIN_ALARMS_PAGE = '/pages/alarms/center/index'

const TAB_WORKORDERS_VIEWER = {
  pagePath: TAB_WORKORDERS.pagePath,
  text: TAB_WORKORDERS.text,
  iconPath: TAB_WORKORDERS.iconPath,
  selectedIconPath: TAB_WORKORDERS.selectedIconPath,
}

/** 观察员：监控 + 告警 + 工单（只读） */
const TAB_LIST_VIEWER = [
  TAB_DASHBOARD,
  TAB_MONITOR,
  TAB_ALARMS,
  TAB_WORKORDERS_VIEWER,
  TAB_PROFILE,
]

/** app.json tabBar.list — 仅这 5 个页面可 switchTab */
const NATIVE_TAB_PATHS = [
  TAB_DASHBOARD.pagePath,
  TAB_WORKORDERS.pagePath,
  TAB_ALARMS.pagePath,
  TAB_USERS.pagePath,
  TAB_PROFILE.pagePath,
]

const TAB_PAGE_PATHS = [
  ...NATIVE_TAB_PATHS,
  TAB_MONITOR.pagePath,
  TAB_TASKS.pagePath,
  ADMIN_ALARMS_PAGE,
]

function normalizePath(url) {
  const path = String(url || '').split('?')[0]
  if (!path) return ''
  return path.startsWith('/') ? path : `/${path}`
}

function isNativeTabPage(url) {
  const path = normalizePath(url)
  if (!path) return false
  return NATIVE_TAB_PATHS.some((p) => path === p || path.endsWith(p.replace(/^\//, '')))
}

function getTabList(permissions, role) {
  if (role === 'VIEWER') {
    return TAB_LIST_VIEWER
  }
  const tail = [TAB_PROFILE]
  if (role === 'ADMIN' && hasPermission(permissions, 'user:manage')) {
    return [TAB_DASHBOARD_ALARM_BADGE, TAB_WORKORDERS, TAB_MONITOR, TAB_USERS, ...tail]
  }
  return [TAB_DASHBOARD_ALARM_BADGE, TAB_WORKORDERS, TAB_MONITOR, TAB_TASKS, ...tail]
}

function isUserTabPage(url, permissions, role) {
  const path = normalizePath(url)
  return getTabList(permissions, role).some((t) => t.pagePath === path)
}

function canSwitchTab(url, permissions, role) {
  return isNativeTabPage(url) && isUserTabPage(url, permissions, role)
}

function currentPagePath() {
  const pages = getCurrentPages()
  if (!pages.length) return ''
  const route = pages[pages.length - 1].route || ''
  return normalizePath(route.startsWith('/') ? route : `/${route}`)
}

function resolveTarget(url, permissions, role) {
  const raw = String(url || '')
  const path = normalizePath(raw)
  const query = raw.includes('?') ? raw.slice(raw.indexOf('?')) : ''
  let targetPath = path
  if (path === TAB_ALARMS.pagePath && role === 'ADMIN' && hasPermission(permissions, 'workorder:create')) {
    targetPath = ADMIN_ALARMS_PAGE
  }
  return `${targetPath}${query}`
}

let navLockUntil = 0
let lastNavTarget = ''

function shouldSkipNav(target) {
  const targetPath = normalizePath(target)
  if (targetPath === currentPagePath()) return true
  const now = Date.now()
  if (target === lastNavTarget && now < navLockUntil) return true
  lastNavTarget = target
  navLockUntil = now + 700
  return false
}

/** 登录/自动登录进入主界面：reLaunch 清栈，避免 switchTab 闪白 */
function enterMainApp(url, permissions, role) {
  const target = resolveTarget(url, permissions, role)
  if (shouldSkipNav(target)) return
  wx.reLaunch({ url: target })
}

function openPage(url, permissions, role) {
  const target = resolveTarget(url, permissions, role)
  const targetPath = normalizePath(target)
  if (shouldSkipNav(target)) return
  if (canSwitchTab(targetPath, permissions, role)) {
    wx.switchTab({ url: target })
  } else if (isUserTabPage(targetPath, permissions, role)) {
    const current = currentPagePath()
    const fromNativeTab = isNativeTabPage(current)
    const fromVirtualTab = isUserTabPage(current, permissions, role) && !isNativeTabPage(current)
    const toVirtualTab = !isNativeTabPage(targetPath)
    if (fromVirtualTab && toVirtualTab) {
      wx.redirectTo({ url: target })
    } else if (fromNativeTab && toVirtualTab) {
      wx.navigateTo({ url: target })
    } else {
      wx.redirectTo({ url: target })
    }
  } else {
    wx.navigateTo({ url: target })
  }
}

function isTabPage(url) {
  const path = normalizePath(url)
  if (!path) return false
  return TAB_PAGE_PATHS.some((p) => path === p || path.endsWith(p.replace(/^\//, '')))
}

module.exports = {
  getTabList,
  isTabPage,
  isNativeTabPage,
  isUserTabPage,
  canSwitchTab,
  enterMainApp,
  openPage,
  NATIVE_TAB_PATHS,
  TAB_LIST_VIEWER,
  ADMIN_ALARMS_PAGE,
  TAB_ALARMS,
  TAB_USERS,
}
