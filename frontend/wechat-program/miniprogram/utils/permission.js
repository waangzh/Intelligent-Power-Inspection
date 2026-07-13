const ROLE_PERMISSIONS = {
  ADMIN: [
    'task:view', 'task:estop',
    'site:edit', 'route:edit', 'robot:manage', 'detection:manage',
    'user:manage', 'record:export',
    'workorder:view', 'workorder:create', 'workorder:assign', 'workorder:review',
    'alarm:policy',
  ],
  DISPATCHER: [
    'task:view', 'task:create', 'task:dispatch', 'task:control',
    'site:edit', 'route:edit', 'alarm:ack', 'record:export',
    'workorder:view', 'workorder:process',
  ],
  VIEWER: ['task:view'],
}

const ROLE_SUMMARIES = {
  ADMIN: { title: '系统治理者', scope: '用户与策略配置、告警转工单、指派与复核；可应急急停' },
  DISPATCHER: { title: '值班运维者', scope: '任务调度、告警处置、工单现场处理' },
  VIEWER: { title: '监督查阅者', scope: '只读查看监控与记录' },
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

module.exports = { hasPermission, canAccessByRole, canAccess, ROLE_PERMISSIONS, ROLE_SUMMARIES }
