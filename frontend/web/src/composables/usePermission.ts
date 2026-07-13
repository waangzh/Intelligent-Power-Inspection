import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { hasPermission, hasAnyPermission, type Permission } from '@/utils/permission'

export function usePermission() {
  const authStore = useAuthStore()
  const role = computed(() => authStore.user?.role)

  function can(permission: Permission) {
    return hasPermission(role.value, permission)
  }

  function canAny(...permissions: Permission[]) {
    return hasAnyPermission(role.value, permissions)
  }

  return { role, can, canAny }
}
