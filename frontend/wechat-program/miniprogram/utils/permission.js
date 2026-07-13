const ROLE_PERMISSIONS = {
  ADMIN: [
    'task:view', 'task:create', 'task:dispatch', 'task:control',
    'site:edit', 'route:edit', 'alarm:ack', 'robot:manage',
    'detection:manage', 'user:manage', 'record:export',
    'workorder:view', 'workorder:create', 'workorder:review',
    'alarm:policy',
  ],
  DISPATCHER: [
    'task:view', 'task:create', 'task:dispatch', 'task:control',
    'site:edit', 'route:edit', 'alarm:ack', 'record:export',
    'workorder:view', 'workorder:process',
  ],
  VIEWER: ['task:view'],
}

function hasPermission(role, permission) {
  if (!role) return false
  return (ROLE_PERMISSIONS[role] || []).includes(permission)
}

function canAccessByRole(role, allowedRoles) {
  if (!allowedRoles || !allowedRoles.length) return true
  return !!role && allowedRoles.includes(role)
}

function canAccess(role, rule) {
  if (!rule) return true
  if (rule.roles && rule.roles.length && !canAccessByRole(role, rule.roles)) return false
  if (rule.permission && !hasPermission(role, rule.permission)) return false
  return true
}

module.exports = { hasPermission, canAccessByRole, canAccess, ROLE_PERMISSIONS }
