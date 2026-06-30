import type { AuthSession } from '@/types/auth'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const SESSION_KEY = 'pi_session'

function authToken(): string | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as AuthSession
    return session.token || null
  } catch {
    return null
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const token = authToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  })

  if (response.status === 401) {
    localStorage.removeItem(SESSION_KEY)
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    if (!response.ok) throw new Error('请求失败')
    return (await response.blob()) as T
  }

  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  postForm: <T>(path: string, body: FormData) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
