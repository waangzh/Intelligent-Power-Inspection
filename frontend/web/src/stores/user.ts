import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listUsersApi, updateUserRoleApi } from '@/api/auth'
import type { User, UserRole } from '@/types/auth'

export const useUserStore = defineStore('user', () => {
  const users = ref<User[]>([])

  async function loadUsers() {
    users.value = await listUsersApi()
  }

  async function updateRole(userId: string, role: UserRole) {
    const updated = await updateUserRoleApi(userId, role)
    syncUser(updated)
    return updated
  }

  function syncUser(updated: User) {
    const idx = users.value.findIndex((u) => u.id === updated.id)
    if (idx >= 0) {
      users.value[idx] = { ...updated }
    } else if (users.value.length > 0) {
      void loadUsers()
    }
  }

  return { users, loadUsers, updateRole, syncUser }
})
