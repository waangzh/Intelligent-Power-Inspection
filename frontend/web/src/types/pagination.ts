import type { Alarm, InspectionTask, Robot, Site } from '@/types'

export interface ListQuery {
  page?: number
  size?: number
  sort?: 'updatedAt' | 'createdAt' | 'id'
  direction?: 'asc' | 'desc'
  updatedAfter?: string
  q?: string
  siteId?: string
  routeId?: string
  robotId?: string
  status?: string
  severity?: string
  acknowledged?: boolean
  type?: string
  enabled?: boolean
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  hasMore: boolean
  nextCursor?: string
}

export interface DashboardOverview {
  counts: {
    sites: number; routes: number; robots: number; onlineRobots: number
    tasks: number; completedTasks: number; activeTasks: number
    alarms: number; unacknowledgedAlarms: number
  }
  rates: { robotOnline: number; taskCompletion: number; alarmHandled: number }
  alarmSeverity: Record<'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW', number>
  weeklyAlarmCounts: number[]
  recentAlarms: Alarm[]
  activeTaskItems: InspectionTask[]
  robotItems: Robot[]
  siteItems: Site[]
}

export interface ResourceChangeEvent {
  resource: 'task' | 'taskEvent' | 'robot' | 'alarm' | 'notification' | 'workOrder'
  resourceId: string
  operation: 'CREATED' | 'UPDATED' | 'DELETED'
  updatedAt: string
}

export function listQueryString(query: ListQuery = {}) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const encoded = params.toString()
  return encoded ? `?${encoded}` : ''
}
