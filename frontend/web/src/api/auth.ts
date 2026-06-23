import { http } from '@/api/http'
import type { AuthSession, ProfileForm, RegisterForm, User, UserRole } from '@/types/auth'

export function validateUsername(username: string): string | null {
  if (!username || username.length < 4 || username.length > 20) {
    return '用户名长度为 4～20 位'
  }
  if (!/^[a-zA-Z0-9_]+$/.test(username)) {
    return '用户名只能包含字母、数字和下划线'
  }
  return null
}

export function validatePassword(password: string): string | null {
  if (!password || password.length < 8) {
    return '密码至少 8 位'
  }
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) {
    return '密码须同时包含字母和数字'
  }
  return null
}

export async function loginApi(username: string, password: string, remember = false): Promise<AuthSession> {
  return http.post<AuthSession>('/auth/login', { username, password, remember })
}

export async function registerApi(form: RegisterForm): Promise<User> {
  return http.post<User>('/auth/register', form)
}

export function listUsersApi(): Promise<User[]> {
  return http.get<User[]>('/users')
}

export async function updateUserRoleApi(userId: string, role: UserRole): Promise<User> {
  return http.patch<User>(`/users/${userId}/role`, { role })
}

export async function updateProfileApi(userId: string, form: ProfileForm): Promise<User> {
  void userId
  return http.patch<User>('/users/me', form)
}

export async function toggleUserEnabledApi(userId: string, enabled: boolean): Promise<void> {
  await http.patch<User>(`/users/${userId}/enabled`, { enabled })
}
