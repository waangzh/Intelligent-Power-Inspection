import type { UserRole } from '@/types/auth'

export type Permission =
  | 'task:view'
  | 'task:create'
  | 'task:dispatch'
  | 'task:control'
  | 'task:estop'
  | 'site:edit'
  | 'route:edit'
  | 'alarm:ack'
  | 'robot:manage'
  | 'detection:manage'
  | 'user:manage'
  | 'record:export'
  | 'workorder:view'
  | 'workorder:create'
  | 'workorder:process'
  | 'workorder:review'
  | 'alarm:policy'

export interface AccessRule {
  permission?: Permission
  roles?: UserRole[]
}

/** 管理员：系统治理与流程审批，不执行一线巡检调度 */
const ROLE_PERMISSIONS: Record<UserRole, Permission[]> = {
  ADMIN: [
    'task:view',
    'task:estop',
    'site:edit',
    'route:edit',
    'robot:manage',
    'detection:manage',
    'user:manage',
    'record:export',
    'workorder:view',
    'workorder:create',
    'workorder:review',
    'alarm:policy',
  ],
  /** 调度员：值班运维，负责任务执行与告警现场处置 */
  DISPATCHER: [
    'task:view',
    'task:create',
    'task:dispatch',
    'task:control',
    'site:edit',
    'route:edit',
    'alarm:ack',
    'record:export',
    'workorder:view',
    'workorder:process',
  ],
  /** 观察员：只读监督，无写操作 */
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

export const ROLE_SUMMARIES: Record<UserRole, { title: string; scope: string }> = {
  ADMIN: {
    title: '系统治理者',
    scope: '用户与策略配置、告警转工单与复核；可应急急停，不执行日常巡检调度',
  },
  DISPATCHER: {
    title: '值班运维者',
    scope: '任务创建下发、告警确认处置、接单处理并提交复核；不可改角色与复核关闭',
  },
  VIEWER: {
    title: '监督查阅者',
    scope: '查看监控、告警、任务与记录；不可操作任务、机器人与工单',
  },
}
