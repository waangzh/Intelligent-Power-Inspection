export type UserRole = 'ADMIN' | 'DISPATCHER' | 'VIEWER'

export const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: '管理员',
  DISPATCHER: '调度员',
  VIEWER: '观察员',
}

export interface User {
  id: string
  username: string
  displayName: string
  role: UserRole
  phone?: string
  avatarUrl?: string
  bio?: string
  enabled?: boolean
  createdAt: string
  updatedAt?: string
}

export interface ProfileForm {
  displayName?: string
  phone?: string
  bio?: string
  avatarUrl?: string
}

export interface ChangePasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface UserPreferences {
  notifyAlarm: boolean
  notifyTask: boolean
  notifySystem: boolean
  defaultSiteId?: string
  sidebarCollapsed: boolean
}

export type UserActivityType = 'LOGIN' | 'PROFILE' | 'AVATAR' | 'PASSWORD' | 'TASK' | 'ALARM' | 'SETTINGS'

export interface UserActivity {
  id: string
  userId: string
  type: UserActivityType
  message: string
  createdAt: string
}

export interface LoginForm {
  username: string
  password: string
  remember: boolean
}

export interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  displayName: string
  phone?: string
  smsCode?: string
  agreed: boolean
}

export interface AuthSession {
  token: string
  user: User
  permissions: string[]
  scopes?: AuthScopes
  features?: AuthFeatures
  expiresAt?: number
}

export interface AuthScopes {
  siteIds: string[]
}

export interface AuthFeatures {
  robotRegistration: boolean
  agentEnabled: boolean
}

export interface MeResponse {
  user: User
  permissions: string[]
  scopes?: AuthScopes
  features?: AuthFeatures
}
