import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '@/types/auth'
import { http, setSessionExpiredHandler } from '@/api/http'

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
  token: 'stale-access',
  user: {
    id: 'user-viewer',
    username: 'viewer',
    displayName: '观察员',
    role: 'VIEWER',
    createdAt: '2026-07-18T00:00:00Z',
  },
  permissions: ['task:view'],
  expiresAt: Date.now() + 60_000,
}

describe('会话失效处理', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', memoryStorage())
    localStorage.setItem('pi_session', JSON.stringify(session))
    setSessionExpiredHandler(null)
  })

  afterEach(() => {
    setSessionExpiredHandler(null)
    vi.unstubAllGlobals()
  })

  it('Access Token 失效且 Refresh 失败时清理会话并通知一次', async () => {
    const onExpired = vi.fn()
    setSessionExpiredHandler(onExpired)

    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ code: 401, message: '登录状态已失效，请重新登录', data: null }),
        { status: 401, headers: { 'Content-Type': 'application/json' } },
      ))
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ code: 401, message: '刷新凭证无效', data: null }),
        { status: 401, headers: { 'Content-Type': 'application/json' } },
      ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(http.get('/sites')).rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain('/auth/refresh')
    expect(localStorage.getItem('pi_session')).toBeNull()
    expect(onExpired).toHaveBeenCalledTimes(1)
  })
})
