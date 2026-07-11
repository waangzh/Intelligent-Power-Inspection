import type { UserRole } from '@/types/auth'

export type Permission =
  | 'task:view'
  | 'task:create'
  | 'task:dispatch'
  | 'task:control'
  | 'site:edit'
  | 'route:edit'
  | 'alarm:ack'
  | 'robot:manage'
  | 'detection:manage'
  | 'user:manage'
  | 'record:export'
  | 'agent:view'
  | 'agent:run'
  | 'agent:approve'
  | 'agent:admin'

export interface AccessRule {
  permission?: Permission
  roles?: UserRole[]
}

const ROLE_PERMISSIONS: Record<UserRole, Permission[]> = {
  ADMIN: [
    'task:view',
    'task:create',
    'task:dispatch',
    'task:control',
    'site:edit',
    'route:edit',
    'alarm:ack',
    'robot:manage',
    'detection:manage',
    'user:manage',
    'record:export',
    'agent:view',
    'agent:run',
    'agent:approve',
    'agent:admin',
  ],
  DISPATCHER: [
    'task:view',
    'task:create',
    'task:dispatch',
    'task:control',
    'site:edit',
    'route:edit',
    'alarm:ack',
    'record:export',
    'agent:view',
    'agent:run',
    'agent:approve',
  ],
  VIEWER: ['task:view'],
}

export function canAccessByRole(role: UserRole | undefined, allowedRoles?: UserRole[]): boolean {
  if (!allowedRoles?.length) return true
  return !!role && allowedRoles.includes(role)
}

export function canAccess(role: UserRole | undefined, rule?: AccessRule): boolean {
  if (!rule) return true
  if (rule.roles?.length && !canAccessByRole(role, rule.roles)) {
    return false
  }
  if (rule.permission && !hasPermission(role, rule.permission)) {
    return false
  }
  return true
}

export function hasPermission(role: UserRole | undefined, permission: Permission): boolean {
  if (!role) return false
  return ROLE_PERMISSIONS[role].includes(permission)
}

export function hasAnyPermission(role: UserRole | undefined, permissions: Permission[]): boolean {
  return permissions.some((p) => hasPermission(role, p))
}
