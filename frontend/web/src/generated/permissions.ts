/** AUTO-GENERATED — 请勿手工编辑。运行: npm run permissions:generate */
export const PERMISSION_VALUES = [
  'agent:admin',
  'agent:approve',
  'agent:run',
  'agent:view',
  'alarm:ack',
  'alarm:policy',
  'detection:manage',
  'record:export',
  'robot:manage',
  'route:edit',
  'site:edit',
  'task:control',
  'task:create',
  'task:dispatch',
  'task:estop',
  'task:takeover',
  'task:view',
  'user:manage',
  'workorder:create',
  'workorder:process',
  'workorder:review',
  'workorder:view',
] as const

export type Permission = (typeof PERMISSION_VALUES)[number]

/** Mock 演示登录专用 — 联调时权限必须来自 /auth/login 或 /auth/me */
export const PERMISSIONS_BY_ROLE: Record<'ADMIN' | 'DISPATCHER' | 'VIEWER', Permission[]> = {
  ADMIN: [
    'agent:admin',
    'agent:approve',
    'agent:run',
    'agent:view',
    'alarm:policy',
    'detection:manage',
    'record:export',
    'robot:manage',
    'route:edit',
    'site:edit',
    'task:estop',
    'task:view',
    'user:manage',
    'workorder:create',
    'workorder:review',
    'workorder:view',
  ],
  DISPATCHER: [
    'agent:approve',
    'agent:run',
    'agent:view',
    'alarm:ack',
    'record:export',
    'route:edit',
    'site:edit',
    'task:control',
    'task:create',
    'task:dispatch',
    'task:takeover',
    'task:view',
    'workorder:process',
    'workorder:view',
  ],
  VIEWER: [
    'task:view',
  ],
}

/** @deprecated 仅 Mock 登录使用；运行时勿调用 */
export function permissionsForRole(role: 'ADMIN' | 'DISPATCHER' | 'VIEWER' | undefined): Permission[] {
  if (!role) return []
  return [...(PERMISSIONS_BY_ROLE[role] || [])]
}
