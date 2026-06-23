import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { hasPermission, type Permission } from '@/utils/permission'

export function usePermission() {
  const authStore = useAuthStore()
  const role = computed(() => authStore.user?.role)

  function can(permission: Permission) {
    return hasPermission(role.value, permission)
  }

  return { role, can }
}
