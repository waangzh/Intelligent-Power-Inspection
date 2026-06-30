import { http } from '@/api/http'
import type {
  Alarm,
  Area,
  Checkpoint,
  DetectionTemplate,
  InspectionRecord,
  InspectionTask,
  LingBotMapJob,
  ManualDetectionResponse,
  Robot,
  Route,
  TaskEvent,
} from '@/types'
import type { AppNotification, NotificationType } from '@/types/notification'
import type { WorkOrder, WorkOrderStatus } from '@/types/workOrder'
import type { Site } from '@/types'

export const resourcesApi = {
  listSites: () => http.get<Site[]>('/sites'),
  createSite: (site: Site) => http.post<Site>('/sites', site),
  updateSite: (id: string, patch: Partial<Site>) => http.patch<Site>(`/sites/${id}`, patch),
  removeSite: (id: string) => http.delete<void>(`/sites/${id}`),
  listAreas: () => http.get<Area[]>('/sites/areas'),
  createArea: (area: Area) => http.post<Area>(`/sites/${area.siteId}/areas`, area),
  removeArea: (id: string) => http.delete<void>(`/sites/areas/${id}`),

  listRoutes: () => http.get<Route[]>('/routes'),
  createRoute: (route: Route) => http.post<Route>('/routes', route),
  updateRoute: (id: string, patch: Partial<Route>) => http.patch<Route>(`/routes/${id}`, patch),
  removeRoute: (id: string) => http.delete<void>(`/routes/${id}`),
  addCheckpoint: (routeId: string, checkpoint: Checkpoint) =>
    http.post<Checkpoint>(`/routes/${routeId}/checkpoints`, checkpoint),
  updateCheckpoint: (routeId: string, checkpointId: string, patch: Partial<Checkpoint>) =>
    http.patch<Checkpoint>(`/routes/${routeId}/checkpoints/${checkpointId}`, patch),
  removeCheckpoint: (routeId: string, checkpointId: string) =>
    http.delete<void>(`/routes/${routeId}/checkpoints/${checkpointId}`),

  listTasks: () => http.get<InspectionTask[]>('/tasks'),
  createTask: (task: InspectionTask) => http.post<InspectionTask>('/tasks', task),
  dispatchTask: (id: string) => http.post<InspectionTask>(`/tasks/${id}/dispatch`),
  pauseTask: (id: string) => http.post<InspectionTask>(`/tasks/${id}/pause`),
  resumeTask: (id: string) => http.post<InspectionTask>(`/tasks/${id}/resume`),
  takeoverTask: (id: string) => http.post<InspectionTask>(`/tasks/${id}/takeover`),
  cancelTask: (id: string) => http.post<InspectionTask>(`/tasks/${id}/cancel`),
  taskEvents: (id: string) => http.get<TaskEvent[]>(`/tasks/${id}/events`),
  listRecords: () => http.get<InspectionRecord[]>('/records'),
  exportRecords: () => http.post<Blob>('/records/export'),

  listAlarms: () => http.get<Alarm[]>('/alarms'),
  acknowledgeAlarm: (id: string) => http.post<Alarm>(`/alarms/${id}/ack`),
  acknowledgeAllAlarms: () => http.post<Alarm[]>('/alarms/ack-all'),

  listWorkOrders: () => http.get<WorkOrder[]>('/work-orders'),
  createWorkOrderFromAlarm: (alarmId: string, assigneeName?: string) =>
    http.post<WorkOrder>(`/work-orders/from-alarm/${alarmId}`, { assigneeName }),
  updateWorkOrderStatus: (id: string, status: WorkOrderStatus, extra?: { resolution?: string }) =>
    http.patch<WorkOrder>(`/work-orders/${id}/status`, { status, ...extra }),
  assignWorkOrder: (id: string, assigneeName: string) =>
    http.patch<WorkOrder>(`/work-orders/${id}/assign`, { assigneeName }),

  listRobots: () => http.get<Robot[]>('/robots'),
  createRobot: (robot: Robot) => http.post<Robot>('/robots', robot),
  updateRobot: (id: string, patch: Partial<Robot>) => http.patch<Robot>(`/robots/${id}`, patch),
  removeRobot: (id: string) => http.delete<void>(`/robots/${id}`),

  listDetectionTemplates: () => http.get<DetectionTemplate[]>('/detection-templates'),
  createDetectionTemplate: (template: DetectionTemplate) =>
    http.post<DetectionTemplate>('/detection-templates', template),
  removeDetectionTemplate: (id: string) => http.delete<void>(`/detection-templates/${id}`),
  manualLocateDetection: (form: FormData) => http.postForm<ManualDetectionResponse>(`/detections/manual`, form),

  listLingBotJobs: () => http.get<LingBotMapJob[]>('/lingbot/jobs'),
  createLingBotJob: (job: LingBotMapJob) => http.post<LingBotMapJob>('/lingbot/jobs', job),
  simulateLingBotJob: (id: string) => http.post<LingBotMapJob>(`/lingbot/jobs/${id}/simulate`),

  listNotifications: () => http.get<AppNotification[]>('/notifications'),
  markNotificationRead: (id: string) => http.patch<AppNotification>(`/notifications/${id}/read`),
  markAllNotificationsRead: () => http.patch<AppNotification[]>('/notifications/read-all'),
  removeNotification: (id: string) => http.delete<void>(`/notifications/${id}`),
  pushLocalOnly: (
    userId: string,
    type: NotificationType,
    title: string,
    content: string,
    link?: string,
  ): AppNotification => ({
    id: `ntf_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
    userId,
    type,
    title,
    content,
    link,
    read: false,
    createdAt: new Date().toISOString(),
  }),
}
