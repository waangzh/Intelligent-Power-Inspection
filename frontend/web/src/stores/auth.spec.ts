import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AuthSession } from '@/types/auth'

vi.mock('@/api/auth', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
  updateProfileApi: vi.fn(),
}))

vi.mock('@/stores/bootstrap', () => ({
  loadAppData: vi.fn().mockResolvedValue(undefined),
  stopRealtime: vi.fn(),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ syncUser: vi.fn() }),
}))

import { loginApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

function memoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() { return values.size },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => { values.delete(key) },
    setItem: (key, value) => { values.set(key, value) },
  }
}

const session: AuthSession = {
  token: 'access-token-before-logout',
  user: {
    id: 'user-admin',
    username: 'admin',
    displayName: '管理员',
    role: 'ADMIN',
    createdAt: '2026-07-18T00:00:00Z',
  },
  permissions: ['task:view'],
  expiresAt: Date.now() + 60_000,
}

describe('认证登出', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.stubGlobal('localStorage', memoryStorage())
    vi.mocked(loginApi).mockResolvedValue(session)
  })

  it('携带旧 Access Token 登出，401 时不刷新并最终清理本地会话', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 401, message: '未登录', data: null }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)
    const store = useAuthStore()
    await store.login('admin', 'Admin@123', true)

    await store.logout()
    store.restoreSession()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/logout')
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer access-token-before-logout')
    expect(localStorage.getItem('pi_session')).toBeNull()
    expect(store.isLoggedIn).toBe(false)
  })
})
