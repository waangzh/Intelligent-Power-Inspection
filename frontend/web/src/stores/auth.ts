import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loginApi, registerApi, updateProfileApi } from '@/api/auth'
import { http } from '@/api/http'
import { loadAppData, stopRealtime } from '@/stores/bootstrap'
import { useUserStore } from '@/stores/user'
import type { AuthSession, MeResponse, ProfileForm, RegisterForm, User } from '@/types/auth'
import type { Permission } from '@/utils/permission'
import { loadFromStorage, saveToStorage } from '@/utils/storage'

const SESSION_KEY = 'pi_session'

function withExpires(session: AuthSession, previousExpiresAt?: number): AuthSession {
  return {
    ...session,
    expiresAt: session.expiresAt ?? previousExpiresAt,
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)
  const permissions = ref<Permission[]>([])
  const scopes = ref<AuthSession['scopes']>()
  const features = ref<AuthSession['features']>()
  const expiresAt = ref<number | undefined>()

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  function persistSession(session: AuthSession | null) {
    if (session) {
      saveToStorage(SESSION_KEY, session)
    } else {
      localStorage.removeItem(SESSION_KEY)
    }
  }

  function applySession(session: AuthSession) {
    const next = withExpires(session, expiresAt.value)
    token.value = next.token
    user.value = next.user
    permissions.value = Array.isArray(next.permissions) ? (next.permissions as Permission[]) : []
    scopes.value = next.scopes
    features.value = next.features
    expiresAt.value = next.expiresAt
    persistSession({
      ...next,
      permissions: permissions.value,
    })
  }

  function restoreSession() {
    const session = loadFromStorage<AuthSession | null>(SESSION_KEY, null)
    if (!session?.token || !session.user) {
      token.value = null
      user.value = null
      permissions.value = []
      scopes.value = undefined
      features.value = undefined
      expiresAt.value = undefined
      return
    }
    if (session.expiresAt && Date.now() > session.expiresAt) {
      // Access token expired: keep session shell and let http refresh via cookie.
    }
    applySession(session)
    void refreshMe()
  }

  function clearSession() {
    token.value = null
    user.value = null
    permissions.value = []
    scopes.value = undefined
    features.value = undefined
    expiresAt.value = undefined
    stopRealtime()
    persistSession(null)
  }

  async function login(username: string, password: string, remember = false) {
    const session = await loginApi(username, password, remember)
    applySession(session)
    if (!permissions.value.length) {
      await refreshMe()
    }
    await loadAppData()
    return session.user
  }

  async function register(form: RegisterForm) {
    const newUser = await registerApi(form)
    return newUser
  }

  function logout() {
    const previous = token.value
    clearSession()
    void http.post('/auth/logout').catch(() => undefined)
    void previous
  }

  function currentSessionBase(): AuthSession | null {
    if (!token.value || !user.value) return null
    return {
      token: token.value,
      user: user.value,
      permissions: permissions.value,
      scopes: scopes.value,
      features: features.value,
      expiresAt: expiresAt.value,
    }
  }

  function patchUser(patch: Partial<User>) {
    if (!user.value) return
    user.value = { ...user.value, ...patch }
    const base = currentSessionBase()
    if (base) {
      persistSession(base)
    }
    if (patch.id || user.value.id) {
      useUserStore().syncUser(user.value)
    }
  }

  async function updateProfile(form: ProfileForm) {
    if (!user.value) throw new Error('未登录')
    const updated = await updateProfileApi(user.value.id, form)
    user.value = { ...updated }
    const base = currentSessionBase()
    if (base) {
      persistSession(base)
    }
    useUserStore().syncUser(updated)
    return updated
  }

  async function refreshMe() {
    try {
      const fresh = await http.get<MeResponse>('/auth/me')
      user.value = fresh.user
      permissions.value = (fresh.permissions ?? []) as Permission[]
      scopes.value = fresh.scopes
      features.value = fresh.features
      const base = currentSessionBase()
      if (base) {
        // Prefer token possibly refreshed by http interceptor.
        const stored = loadFromStorage<AuthSession | null>(SESSION_KEY, null)
        persistSession({
          ...base,
          token: stored?.token || base.token,
          expiresAt: stored?.expiresAt ?? base.expiresAt,
          user: fresh.user,
          permissions: permissions.value,
          scopes: scopes.value,
          features: features.value,
        })
        if (stored?.token) {
          token.value = stored.token
        }
        if (stored?.expiresAt) {
          expiresAt.value = stored.expiresAt
        }
      }
      await loadAppData()
    } catch {
      clearSession()
    }
  }

  async function reauth(password: string) {
    const session = await http.post<AuthSession>('/auth/reauth', { password })
    applySession(withExpires(session, expiresAt.value))
    return session
  }

  return {
    token,
    user,
    permissions,
    scopes,
    features,
    expiresAt,
    isLoggedIn,
    restoreSession,
    login,
    register,
    logout,
    patchUser,
    updateProfile,
    refreshMe,
    reauth,
  }
})
