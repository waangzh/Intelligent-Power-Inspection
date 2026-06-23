import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loginApi, registerApi, updateProfileApi } from '@/api/auth'
import { http } from '@/api/http'
import { loadAppData, stopRealtime } from '@/stores/bootstrap'
import { useUserStore } from '@/stores/user'
import type { AuthSession, ProfileForm, RegisterForm, User } from '@/types/auth'
import { loadFromStorage, saveToStorage } from '@/utils/storage'

const SESSION_KEY = 'pi_session'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  function persistSession(session: AuthSession | null) {
    if (session) {
      saveToStorage(SESSION_KEY, session)
    } else {
      localStorage.removeItem(SESSION_KEY)
    }
  }

  function restoreSession() {
    const session = loadFromStorage<AuthSession | null>(SESSION_KEY, null)
    if (!session?.token || !session.user) {
      token.value = null
      user.value = null
      return
    }
    if (session.expiresAt && Date.now() > session.expiresAt) {
      clearSession()
      return
    }
    token.value = session.token
    user.value = session.user
    void refreshMe()
  }

  function applySession(session: AuthSession) {
    token.value = session.token
    user.value = session.user
    persistSession(session)
  }

  function clearSession() {
    token.value = null
    user.value = null
    stopRealtime()
    persistSession(null)
  }

  async function login(username: string, password: string, remember = false) {
    const session = await loginApi(username, password, remember)
    applySession(session)
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
      persistSession({ token: token.value, user: user.value })
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
      persistSession({ token: token.value, user: user.value })
    }
    useUserStore().syncUser(updated)
    return updated
  }

  async function refreshMe() {
    try {
      const fresh = await http.get<User>('/auth/me')
      user.value = fresh
      if (token.value) {
        persistSession({ token: token.value, user: fresh })
      }
      await loadAppData()
    } catch {
      clearSession()
    }
  }

  return {
    token,
    user,
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
