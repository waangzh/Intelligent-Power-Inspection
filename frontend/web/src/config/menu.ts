import type { Permission } from '@/utils/permission'
import type { UserRole } from '@/types/auth'

export interface MenuItem {
  path: string
  label: string
  icon: string
  permission?: Permission
  roles?: UserRole[]
}

export interface MenuGroup {
  title: string
  items: MenuItem[]
}

export const menuGroups: MenuGroup[] = [
  {
    title: '监控中心',
    items: [
      { path: '/dashboard', label: '运行总览', icon: 'Odometer' },
      { path: '/monitor', label: '实时监控', icon: 'Monitor' },
      { path: '/alarms', label: '告警中心', icon: 'Bell' },
    ],
  },
  {
    title: '运维中心',
    items: [
      { path: '/workorders', label: '工单管理', icon: 'Tickets', permission: 'workorder:view' },
      { path: '/notifications', label: '消息中心', icon: 'Message' },
      { path: '/agents', label: '巡检研判助手', icon: 'Connection', permission: 'agent:view' },
    ],
  },
  {
    title: '巡检业务',
    items: [
      { path: '/sites', label: '站点管理', icon: 'OfficeBuilding', permission: 'site:edit' },
      { path: '/routes', label: '巡检规划', icon: 'MapLocation', permission: 'route:edit' },
      { path: '/tasks', label: '任务调度', icon: 'VideoPlay', permission: 'task:view' },
    ],
  },
  {
    title: '资产感知',
    items: [
      { path: '/robots', label: '机器人管理', icon: 'Cpu', permission: 'robot:manage' },
      { path: '/detection', label: '检测策略', icon: 'View', permission: 'detection:manage' },
    ],
  },
  {
    title: '数据中心',
    items: [
      { path: '/records', label: '巡检记录', icon: 'Document' },
      { path: '/statistics', label: '统计分析', icon: 'DataAnalysis' },
    ],
  },
  {
    title: '系统管理',
    items: [
      { path: '/users', label: '用户管理', icon: 'User', roles: ['ADMIN'] },
    ],
  },
]
