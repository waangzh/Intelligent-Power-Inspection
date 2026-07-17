import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { hasPermission, hasAnyPermission, type Permission } from '@/utils/permission'

export function usePermission() {
  const authStore = useAuthStore()
  const role = computed(() => authStore.user?.role)
  const permissions = computed(() => authStore.permissions)

  function can(permission: Permission) {
    return hasPermission(permissions.value, permission)
  }

  function canAny(...values: Permission[]) {
    return hasAnyPermission(permissions.value, values)
  }

  return { role, permissions, can, canAny }
}
