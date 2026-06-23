export interface ProfileMenuItem {
  path: string
  label: string
  icon: string
}

export const profileMenuItems: ProfileMenuItem[] = [
  { path: '/profile/info', label: '我的信息', icon: 'User' },
  { path: '/profile/avatar', label: '我的头像', icon: 'Picture' },
  { path: '/profile/security', label: '账号安全', icon: 'Lock' },
  { path: '/profile/activity', label: '我的记录', icon: 'Document' },
  { path: '/profile/settings', label: '偏好设置', icon: 'Setting' },
]
