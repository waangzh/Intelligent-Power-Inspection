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
  iconPath: '/assets/tabbar/tasks.png',
  selectedIconPath: '/assets/tabbar/tasks-active.png',
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
  iconPath: '/assets/tabbar/tasks.png',
  selectedIconPath: '/assets/tabbar/tasks-active.png',
}

const TAB_PROFILE = {
  pagePath: '/pages/profile/info/index',
  text: '我的',
  iconPath: '/assets/tabbar/profile.png',
  selectedIconPath: '/assets/tabbar/profile-active.png',
  badgeKey: 'profile',
}

/** 管理员 / 调度员：工单 + 监控，告警角标在总览 */
const TAB_LIST_WORKORDER = [
  TAB_DASHBOARD_ALARM_BADGE,
  TAB_WORKORDERS,
  TAB_MONITOR,
  TAB_TASKS,
  TAB_PROFILE,
]

/** 观察员：无工单 Tab */
const TAB_LIST_VIEWER = [
  TAB_DASHBOARD,
  TAB_MONITOR,
  TAB_ALARMS,
  TAB_TASKS,
  TAB_PROFILE,
]

/** app.json tabBar.list 中的页面，可用 switchTab */
const NATIVE_TAB_PATHS = [
  TAB_DASHBOARD.pagePath,
  TAB_WORKORDERS.pagePath,
  TAB_MONITOR.pagePath,
  TAB_TASKS.pagePath,
  TAB_PROFILE.pagePath,
]

const TAB_PAGE_PATHS = [
  ...NATIVE_TAB_PATHS,
  TAB_ALARMS.pagePath,
]

function isNativeTabPage(url) {
  if (!url) return false
  const path = url.split('?')[0]
  return NATIVE_TAB_PATHS.some((p) => path === p || path.endsWith(p.replace(/^\//, '')))
}

function getTabList(role) {
  if (role && hasPermission(role, 'workorder:view')) return TAB_LIST_WORKORDER
  return TAB_LIST_VIEWER
}

function isTabPage(url) {
  if (!url) return false
  const path = url.split('?')[0]
  return TAB_PAGE_PATHS.some((p) => path === p || path.endsWith(p.replace(/^\//, '')))
}

module.exports = {
  getTabList,
  isTabPage,
  isNativeTabPage,
  NATIVE_TAB_PATHS,
  TAB_LIST_WORKORDER,
  TAB_LIST_VIEWER,
}
