import type { ChangePasswordForm, User, UserActivity, UserActivityType, UserPreferences } from '@/types/auth'
import { http } from '@/api/http'
import { validatePassword } from '@/api/auth'

const DEFAULT_PREFS: UserPreferences = {
  notifyAlarm: true,
  notifyTask: true,
  notifySystem: true,
  sidebarCollapsed: false,
}

export function logUserActivity(userId: string, type: UserActivityType, message: string) {
  void userId
  void type
  void message
}

export function getUserActivitiesApi(userId: string): Promise<UserActivity[]> {
  void userId
  return http.get<UserActivity[]>('/users/me/activities')
}

export async function getUserPreferencesApi(userId: string): Promise<UserPreferences> {
  void userId
  return { ...DEFAULT_PREFS, ...(await http.get<UserPreferences>('/users/me/preferences')) }
}

export async function saveUserPreferencesApi(userId: string, prefs: UserPreferences): Promise<UserPreferences> {
  void userId
  return http.put<UserPreferences>('/users/me/preferences', prefs)
}

export async function changePasswordApi(user: User, form: ChangePasswordForm): Promise<void> {
  if (form.newPassword !== form.confirmPassword) {
    throw new Error('两次输入的新密码不一致')
  }

  const pwdErr = validatePassword(form.newPassword)
  if (pwdErr) throw new Error(pwdErr)

  void user
  await http.put<void>('/auth/password', form)
}
