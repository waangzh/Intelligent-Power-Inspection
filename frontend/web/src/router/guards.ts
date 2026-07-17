import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasPermission, canAccessByRole } from '@/utils/permission'

export function setupRouterGuards(router: Router) {
  router.beforeEach((to) => {
    const authStore = useAuthStore()
    const role = authStore.user?.role
    const permissions = authStore.permissions
    const isPublic = to.meta.public === true
    const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

    if (isPublic && authStore.isLoggedIn) {
      return { path: '/dashboard' }
    }

    if (requiresAuth && !authStore.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    for (let i = to.matched.length - 1; i >= 0; i--) {
      const record = to.matched[i].meta
      if (record.roles?.length && !canAccessByRole(role, record.roles)) {
        return { path: '/403' }
      }
      if (record.permission && !hasPermission(permissions, record.permission)) {
        return { path: '/403' }
      }
    }

    return true
  })
}
