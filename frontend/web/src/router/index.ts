import { createRouter, createWebHistory } from 'vue-router'
import type { Permission } from '@/utils/permission'
import type { UserRole } from '@/types/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    requiresAuth?: boolean
    permission?: Permission
    roles?: UserRole[]
    breadcrumbs?: { label: string; to?: string }[]
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      component: () => import('@/layouts/AuthLayout.vue'),
      meta: { public: true, title: '登录' },
      children: [{ path: '', name: 'Login', component: () => import('@/views/auth/Login.vue') }],
    },
    {
      path: '/register',
      component: () => import('@/layouts/AuthLayout.vue'),
      meta: { public: true, title: '注册' },
      children: [{ path: '', name: 'Register', component: () => import('@/views/auth/Register.vue') }],
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '运行总览', requiresAuth: true, breadcrumbs: [{ label: '监控中心' }, { label: '运行总览' }] },
        },
        {
          path: 'monitor',
          name: 'Monitor',
          component: () => import('@/views/Monitor.vue'),
          meta: { title: '实时监控', requiresAuth: true, breadcrumbs: [{ label: '监控中心' }, { label: '实时监控' }] },
        },
        {
          path: 'alarms',
          name: 'Alarms',
          component: () => import('@/views/AlarmCenter.vue'),
          meta: { title: '告警中心', requiresAuth: true, breadcrumbs: [{ label: '监控中心' }, { label: '告警中心' }] },
        },
        {
          path: 'workorders',
          name: 'WorkOrders',
          component: () => import('@/views/WorkOrderManage.vue'),
          meta: { title: '工单管理', requiresAuth: true, permission: 'workorder:view', breadcrumbs: [{ label: '运维中心' }, { label: '工单管理' }] },
        },
        {
          path: 'notifications',
          name: 'Notifications',
          component: () => import('@/views/NotificationCenter.vue'),
          meta: { title: '消息中心', requiresAuth: true, breadcrumbs: [{ label: '运维中心' }, { label: '消息中心' }] },
        },
        {
          path: 'agents',
          name: 'Agents',
          component: () => import('@/views/AgentDisposition.vue'),
          meta: { title: '巡检处置 Agent', requiresAuth: true, permission: 'agent:view', breadcrumbs: [{ label: '运维中心' }, { label: '巡检处置 Agent' }] },
        },
        {
          path: 'sites',
          name: 'Sites',
          component: () => import('@/views/SiteManage.vue'),
          meta: { title: '站点管理', requiresAuth: true, permission: 'site:edit', breadcrumbs: [{ label: '巡检业务' }, { label: '站点管理' }] },
        },
        {
          path: 'routes',
          name: 'Routes',
          component: () => import('@/views/RoutePlan.vue'),
          meta: { title: '巡检规划', requiresAuth: true, permission: 'route:edit', breadcrumbs: [{ label: '巡检业务' }, { label: 'ROS 地图路线标注' }] },
        },
        {
          path: 'tasks',
          name: 'Tasks',
          component: () => import('@/views/TaskManage.vue'),
          meta: { title: '任务调度', requiresAuth: true, permission: 'task:view', breadcrumbs: [{ label: '巡检业务' }, { label: '任务调度' }] },
        },
        {
          path: 'tasks/:id',
          name: 'TaskDetail',
          component: () => import('@/views/TaskDetail.vue'),
          meta: { title: '任务详情', requiresAuth: true, permission: 'task:view', breadcrumbs: [{ label: '巡检业务' }, { label: '任务调度', to: '/tasks' }, { label: '任务详情' }] },
        },
        {
          path: 'robots',
          name: 'Robots',
          component: () => import('@/views/RobotManage.vue'),
          meta: { title: '机器人管理', requiresAuth: true, permission: 'robot:manage', breadcrumbs: [{ label: '资产感知' }, { label: '机器人管理' }] },
        },
        {
          path: 'robots/status',
          name: 'RobotStatus',
          component: () => import('@/views/RobotStatus.vue'),
          meta: { title: '机器人在线状态', requiresAuth: true, permission: 'robot:manage', breadcrumbs: [{ label: '资产感知' }, { label: '机器人在线状态' }] },
        },
        {
          path: 'detection',
          name: 'Detection',
          component: () => import('@/views/DetectionStrategy.vue'),
          meta: { title: '检测策略', requiresAuth: true, permission: 'detection:manage', breadcrumbs: [{ label: '资产感知' }, { label: '检测策略' }] },
        },
        {
          path: 'records',
          name: 'Records',
          component: () => import('@/views/RecordList.vue'),
          meta: { title: '巡检记录', requiresAuth: true, breadcrumbs: [{ label: '数据中心' }, { label: '巡检记录' }] },
        },
        {
          path: 'statistics',
          name: 'Statistics',
          component: () => import('@/views/Statistics.vue'),
          meta: { title: '统计分析', requiresAuth: true, breadcrumbs: [{ label: '数据中心' }, { label: '统计分析' }] },
        },
        {
          path: 'profile',
          component: () => import('@/layouts/ProfileLayout.vue'),
          redirect: '/profile/info',
          meta: { title: '个人中心', requiresAuth: true, breadcrumbs: [{ label: '系统管理' }, { label: '个人中心' }] },
          children: [
            {
              path: 'info',
              name: 'ProfileInfo',
              component: () => import('@/views/profile/ProfileInfo.vue'),
              meta: { title: '我的信息', requiresAuth: true },
            },
            {
              path: 'avatar',
              name: 'ProfileAvatar',
              component: () => import('@/views/profile/ProfileAvatar.vue'),
              meta: { title: '我的头像', requiresAuth: true },
            },
            {
              path: 'security',
              name: 'ProfileSecurity',
              component: () => import('@/views/profile/ProfileSecurity.vue'),
              meta: { title: '账号安全', requiresAuth: true },
            },
            {
              path: 'activity',
              name: 'ProfileActivity',
              component: () => import('@/views/profile/ProfileActivity.vue'),
              meta: { title: '我的记录', requiresAuth: true },
            },
            {
              path: 'settings',
              name: 'ProfileSettings',
              component: () => import('@/views/profile/ProfileSettings.vue'),
              meta: { title: '偏好设置', requiresAuth: true },
            },
          ],
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/UserManage.vue'),
          meta: { title: '用户管理', requiresAuth: true, roles: ['ADMIN'], permission: 'user:manage', breadcrumbs: [{ label: '系统管理' }, { label: '用户管理' }] },
        },
        {
          path: '403',
          name: 'Forbidden',
          component: () => import('@/views/Forbidden.vue'),
          meta: { title: '无权限', requiresAuth: true },
        },
      ],
    },
    {
      path: '/bigscreen',
      name: 'BigScreen',
      component: () => import('@/views/BigScreen.vue'),
      meta: { title: '集控大屏', requiresAuth: true },
    },
  ],
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || '电力智能巡检平台'
  document.title = `${title} - 电力智能巡检平台`
})

export default router
