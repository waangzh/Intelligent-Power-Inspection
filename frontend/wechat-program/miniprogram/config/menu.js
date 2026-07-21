const profileMenuItems = [
  { path: '/pages/profile/security/index', label: '账号安全' },
  { path: '/pages/profile/activity/index', label: '我的记录' },
  { path: '/pages/profile/settings/index', label: '偏好设置' },
]

/** web 通知 link → 小程序 path（调度员无告警中心，告警通知进工单页） */
const linkMap = {
  '/dashboard': '/pages/dashboard/index',
  '/alarms': '/pages/alarms/index',
  '/workorders': '/pages/workorders/index',
  '/tasks': '/pages/tasks/index',
  '/notifications': '/pages/notifications/index',
}

function mapNotificationLink(link, role) {
  if (!link) return ''
  const normalizedRole = String(role || '').trim().toUpperCase()
  if (normalizedRole === 'DISPATCHER' && (link === '/alarms' || link.startsWith('/alarms'))) {
    return linkMap['/workorders']
  }
  return linkMap[link] || link.replace(/^\//, '/pages/') + '/index'
}

module.exports = { profileMenuItems, mapNotificationLink }
