const { canAccess } = require('../utils/permission')

const menuGroups = [
  {
    title: '监控中心',
    items: [
      { path: '/pages/dashboard/index', label: '运行总览', icon: '📊' },
      { path: '/pages/monitor/index', label: '实时监控', icon: '📡' },
      { path: '/pages/alarms/index', label: '告警中心', icon: '🔔' },
    ],
  },
  {
    title: '运维中心',
    items: [
      { path: '/pages/workorders/index', label: '工单管理', icon: '📋', permission: 'workorder:view' },
      { path: '/pages/notifications/index', label: '消息中心', icon: '💬' },
    ],
  },
  {
    title: '巡检业务',
    items: [
      { path: '/pages/sites/index', label: '站点管理', icon: '🏭', permission: 'site:edit' },
      { path: '/pages/routes/index', label: '路线标注', icon: '🗺️', permission: 'route:edit' },
      { path: '/pages/tasks/index', label: '任务调度', icon: '▶️', permission: 'task:view' },
    ],
  },
  {
    title: '资产感知',
    items: [
      { path: '/pages/robots/index', label: '机器人管理', icon: '🤖', permission: 'robot:manage' },
      { path: '/pages/detection/index', label: '检测策略', icon: '👁️', permission: 'detection:manage' },
    ],
  },
  {
    title: '数据中心',
    items: [
      { path: '/pages/records/index', label: '巡检记录', icon: '📄' },
      { path: '/pages/statistics/index', label: '统计分析', icon: '📈' },
    ],
  },
  {
    title: '系统管理',
    items: [
      { path: '/pages/users/index', label: '用户管理', icon: '👥', roles: ['ADMIN'], permission: 'user:manage' },
    ],
  },
]

const profileMenuItems = [
  { path: '/pages/profile/info/index', label: '我的信息' },
  { path: '/pages/profile/avatar/index', label: '我的头像' },
  { path: '/pages/profile/security/index', label: '账号安全' },
  { path: '/pages/profile/activity/index', label: '我的记录' },
  { path: '/pages/profile/settings/index', label: '偏好设置' },
]

function getVisibleMenuGroups(role) {
  return menuGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((item) => canAccess(role, item)),
    }))
    .filter((g) => g.items.length > 0)
}

/** web 通知 link → 小程序 path */
const linkMap = {
  '/dashboard': '/pages/dashboard/index',
  '/alarms': '/pages/alarms/index',
  '/workorders': '/pages/workorders/index',
  '/tasks': '/pages/tasks/index',
  '/notifications': '/pages/notifications/index',
}

function mapNotificationLink(link) {
  if (!link) return ''
  return linkMap[link] || link.replace(/^\//, '/pages/') + '/index'
}

module.exports = { menuGroups, profileMenuItems, getVisibleMenuGroups, mapNotificationLink }
