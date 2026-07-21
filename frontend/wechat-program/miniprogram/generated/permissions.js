/** AUTO-GENERATED — 请勿手工编辑。运行: npm run permissions:generate */
const PERMISSION_VALUES = [
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
  'task:start-local',
  'task:start-remote',
  'task:takeover',
  'task:view',
  'user:manage',
  'workorder:create',
  'workorder:process',
  'workorder:review',
  'workorder:view',
]

/** Mock 演示登录专用 — 联调时权限必须来自 /auth/login 或 /auth/me */
const PERMISSIONS_BY_ROLE = {
  ADMIN: [
    'agent:admin',
    'agent:approve',
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
    'agent:run',
    'agent:view',
    'alarm:ack',
    'record:export',
    'route:edit',
    'site:edit',
    'task:control',
    'task:create',
    'task:dispatch',
    'task:start-local',
    'task:start-remote',
    'task:takeover',
    'task:view',
    'workorder:process',
    'workorder:view',
  ],
  VIEWER: [
    'task:view',
    'workorder:view',
  ],
}

function permissionsForRole(role) {
  if (!role) return []
  return [...(PERMISSIONS_BY_ROLE[role] || [])]
}

module.exports = {
  PERMISSION_VALUES,
  PERMISSIONS_BY_ROLE,
  permissionsForRole,
}
