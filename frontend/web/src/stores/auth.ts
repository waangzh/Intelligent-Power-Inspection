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

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)
  const permissions = ref<Permission[]>([])
  const scopes = ref<AuthSession['scopes']>()
  const features = ref<AuthSession['features']>()

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  function persistSession(session: AuthSession | null) {
    if (session) {
      saveToStorage(SESSION_KEY, session)
    } else {
      localStorage.removeItem(SESSION_KEY)
    }
  }

  function applySession(session: AuthSession) {
    token.value = session.token
    user.value = session.user
    permissions.value = Array.isArray(session.permissions) ? (session.permissions as Permission[]) : []
    scopes.value = session.scopes
    features.value = session.features
    persistSession({
      ...session,
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
      return
    }
    if (session.expiresAt && Date.now() > session.expiresAt) {
      clearSession()
      return
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
    clearSession()
    void http.post('/auth/logout').catch(() => undefined)
  }

  function patchUser(patch: Partial<User>) {
    if (!user.value) return
    user.value = { ...user.value, ...patch }
    if (token.value) {
      persistSession({
        token: token.value,
        user: user.value,
        permissions: permissions.value,
        scopes: scopes.value,
        features: features.value,
      })
    }
    if (patch.id || user.value.id) {
      useUserStore().syncUser(user.value)
    }
  }

  async function updateProfile(form: ProfileForm) {
    if (!user.value) throw new Error('未登录')
    const updated = await updateProfileApi(user.value.id, form)
    user.value = { ...updated }
    if (token.value) {
      persistSession({
        token: token.value,
        user: user.value,
        permissions: permissions.value,
        scopes: scopes.value,
        features: features.value,
      })
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
      if (token.value) {
        persistSession({
          token: token.value,
          user: fresh.user,
          permissions: permissions.value,
          scopes: scopes.value,
          features: features.value,
        })
      }
      await loadAppData()
    } catch {
      clearSession()
    }
  }

  return {
    token,
    user,
    permissions,
    scopes,
    features,
    isLoggedIn,
    restoreSession,
    login,
    register,
    logout,
    patchUser,
    updateProfile,
    refreshMe,
  }
})
