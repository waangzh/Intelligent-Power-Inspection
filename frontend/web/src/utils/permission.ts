import type { UserRole } from '@/types/auth'

export type { Permission } from '@/generated/permissions'
export { PERMISSION_VALUES } from '@/generated/permissions'

import type { Permission } from '@/generated/permissions'

export interface AccessRule {
  permission?: Permission
  roles?: UserRole[]
}

export function canAccessByRole(role: UserRole | undefined, allowedRoles?: UserRole[]): boolean {
  if (!allowedRoles?.length) return true
  return !!role && allowedRoles.includes(role)
}

export function hasPermission(permissions: Permission[] | undefined, permission: Permission): boolean {
  if (!permissions?.length) return false
  return permissions.includes(permission)
}

export function hasAnyPermission(permissions: Permission[] | undefined, values: Permission[]): boolean {
  return values.some((p) => hasPermission(permissions, p))
}

export function canAccess(
  permissions: Permission[] | undefined,
  role: UserRole | undefined,
  rule?: AccessRule,
): boolean {
  if (!rule) return true
  if (rule.roles?.length && !canAccessByRole(role, rule.roles)) {
    return false
  }
  if (rule.permission && !hasPermission(permissions, rule.permission)) {
    return false
  }
  return true
}

export const ROLE_SUMMARIES: Record<UserRole, { title: string; scope: string }> = {
  ADMIN: {
    title: '系统治理者',
    scope: '用户与策略配置、告警转工单与复核、Agent 审批；可应急急停，不执行日常巡检调度',
  },
  DISPATCHER: {
    title: '值班运维者',
    scope: '任务创建下发、告警确认处置、接单处理并提交复核、Agent 执行；不可改角色与复核关闭',
  },
  VIEWER: {
    title: '监督查阅者',
    scope: '查看监控、告警、任务与记录；不可操作任务、机器人与工单',
  },
}
