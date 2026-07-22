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
    title: '运维处置',
    items: [
      { path: '/workorders', label: '工单管理', icon: 'Tickets', permission: 'workorder:view' },
      { path: '/agents', label: '智能研判', icon: 'Connection', permission: 'agent:view' },
      { path: '/notifications', label: '通知中心', icon: 'Message' },
    ],
  },
  {
    title: '巡检作业',
    items: [
      { path: '/tasks', label: '任务调度', icon: 'VideoPlay', permission: 'task:view' },
      { path: '/routes', label: '路线规划', icon: 'MapLocation', permission: 'route:edit' },
      { path: '/robot-map-review', label: '建图审核', icon: 'Checked' },
      { path: '/robot-scene-review', label: '三维建图审核', icon: 'DataAnalysis' },
    ],
  },
  {
    title: '基础资源',
    items: [
      { path: '/sites', label: '站点管理', icon: 'OfficeBuilding', permission: 'site:edit' },
      { path: '/robots', label: '机器人管理', icon: 'Cpu', permission: 'robot:manage' },
      { path: '/detection', label: 'AI 检测', icon: 'View', permission: 'detection:manage' },
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
