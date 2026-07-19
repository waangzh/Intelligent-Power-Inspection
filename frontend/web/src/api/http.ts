import type { AuthSession } from '@/types/auth'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const SESSION_KEY = 'pi_session'

let refreshPromise: Promise<boolean> | null = null
let sessionExpiredHandler: (() => void) | null = null
let sessionExpiryNotified = false

export function setSessionExpiredHandler(handler: (() => void) | null) {
  sessionExpiredHandler = handler
}

function notifySessionExpired() {
  writeSession(null)
  if (sessionExpiryNotified) return
  sessionExpiryNotified = true
  try {
    sessionExpiredHandler?.()
  } finally {
    queueMicrotask(() => {
      sessionExpiryNotified = false
    })
  }
}

function readSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    return JSON.parse(raw) as AuthSession
  } catch {
    return null
  }
}

function writeSession(session: AuthSession | null) {
  if (!session) {
    localStorage.removeItem(SESSION_KEY)
    return
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

function authToken(): string | null {
  return readSession()?.token || null
}

async function tryRefreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
        })
        if (!response.ok) {
          return false
        }
        const payload = (await response.json()) as ApiResponse<AuthSession>
        if (payload.code !== 0 || !payload.data?.token) {
          return false
        }
        const previous = readSession()
        writeSession({
          ...payload.data,
          expiresAt: payload.data.expiresAt ?? previous?.expiresAt,
        })
        return true
      } catch {
        return false
      } finally {
        refreshPromise = null
      }
    })()
  }
  return refreshPromise
}

async function request<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const isPublicAuth =
    path.startsWith('/auth/login') ||
    path.startsWith('/auth/register') ||
    path.startsWith('/auth/sms/') ||
    path.startsWith('/auth/password/reset') ||
    path.startsWith('/auth/refresh')
  const allowsAutomaticRefresh = !path.startsWith('/auth/logout')
  const token = isPublicAuth ? null : authToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })

  if (response.status === 401 && !isPublicAuth && allowsAutomaticRefresh && !retried) {
    const refreshed = await tryRefreshSession()
    if (refreshed) {
      return request<T>(path, init, true)
    }
    notifySessionExpired()
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    if (!response.ok) throw new ApiError(`请求失败 (${response.status})`, response.status)
    return (await response.blob()) as T
  }

  const payload = (await response.json()) as ApiResponse<T> & { error?: string }
  if (!response.ok || payload.code !== 0) {
    const message = payload.message || payload.error || `请求失败 (${response.status})`
    throw new ApiError(message, response.status)
  }
  return payload.data
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown, headers?: HeadersInit) =>
    request<T>(path, { method: 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) }),
  postForm: <T>(path: string, body: FormData) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
