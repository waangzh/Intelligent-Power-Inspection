import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  changePasswordApi,
  getUserActivitiesApi,
  getUserPreferencesApi,
  saveUserPreferencesApi,
} from '@/api/profile'
import type { ChangePasswordForm, UserActivity, UserPreferences } from '@/types/auth'

export const useProfileStore = defineStore('profile', () => {
  const activities = ref<UserActivity[]>([])
  const preferences = ref<UserPreferences | null>(null)

  function loadActivities(userId: string) {
    void getUserActivitiesApi(userId).then((data) => {
      activities.value = data
    })
  }

  function loadPreferences(userId: string) {
    void getUserPreferencesApi(userId).then((data) => {
      preferences.value = data
    })
    preferences.value ??= {
      notifyAlarm: true,
      notifyTask: true,
      notifySystem: true,
      sidebarCollapsed: false,
    }
    return preferences.value
  }

  async function savePreferences(userId: string, prefs: UserPreferences) {
    preferences.value = await saveUserPreferencesApi(userId, prefs)
    return preferences.value
  }

  async function changePassword(user: import('@/types/auth').User, form: ChangePasswordForm) {
    await changePasswordApi(user, form)
  }

  return {
    activities,
    preferences,
    loadActivities,
    loadPreferences,
    savePreferences,
    changePassword,
  }
})
