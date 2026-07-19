const profileMenuItems = [
  { path: '/pages/profile/info/index', label: '我的信息' },
  { path: '/pages/profile/avatar/index', label: '我的头像' },
  { path: '/pages/profile/security/index', label: '账号安全' },
  { path: '/pages/profile/activity/index', label: '我的记录' },
  { path: '/pages/profile/settings/index', label: '偏好设置' },
  // 仅管理员可见：与网页端 /users 对齐的用户管理入口
  { path: '/pages/profile/users/index', label: '用户管理', permission: 'user:manage' },
]

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

module.exports = { profileMenuItems, mapNotificationLink }
