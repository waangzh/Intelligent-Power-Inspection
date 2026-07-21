import { http } from '@/api/http'
import { openapiClient } from '@/generated/api-client'
import type {
  Alarm,
  AlarmWorkOrderPolicy,
  Area,
  Checkpoint,
  DetectionTemplate,
  DetectionItem,
  DetectionRun,
  InspectionRecord,
  InspectionTask,
  TaskExecution,
  TaskStartEligibility,
  TaskStartMode,
  ManualDetectionResponse,
  MapAsset,
  MapAssetQuery,
  MapAssetReviewInput,
  Robot,
  RobotInspectionImage,
  Route,
  RouteRevision,
  TaskEvent,
} from '@/types'
import type { RouteDeployment } from '@/types/routeDeployment'
import type { AppNotification, NotificationType } from '@/types/notification'
import type { PersistedRouteDraftReport, RouteDraftValidationReport, RouteExecutorDocument } from '@/types/routeExecutor'
import type {
  AgentAction,
  AgentActionDecisionRequest,
  AgentHumanInputRequest,
  AgentCaseDetail,
  AgentCaseSummary,
  AgentRunDetail,
  AgentRunSummary,
  AgentSession,
  AuditedAgentAction,
  AuditedAgentEvidence,
  CreateAgentCaseRequest,
  CreateAgentSessionRequest,
  StartAgentRunRequest,
} from '@/types/agent'
import type { WorkOrder, WorkOrderReviewInput, WorkOrderStatus } from '@/types/workOrder'
import type { Site } from '@/types'
import type { RobotHeartbeatStatus, RobotHeartbeatStatusPage, RobotHeartbeatStatusQuery } from '@/types/robotHeartbeat'
import type {
  RobotLocation,
  RobotLocationQuery,
  RobotTrackQuery,
  RobotTrackResponse,
} from '@/types/robotLocation'
import { buildMapAssetQuery } from '@/utils/mapAssetReview'
import type { DashboardOverview, ListQuery, PageResult } from '@/types/pagination'
import { listQueryString } from '@/types/pagination'

export const resourcesApi = {
  listSites: (query: ListQuery = {}) => http.get<PageResult<Site>>(`/sites${listQueryString(query)}`),
  getSite: (id: string) => http.get<Site>(`/sites/${encodeURIComponent(id)}`),
  createSite: (site: Site) => http.post<Site>('/sites', site),
  updateSite: (id: string, patch: Partial<Site>) => http.patch<Site>(`/sites/${id}`, patch),
  removeSite: (id: string) => http.delete<void>(`/sites/${id}`),
  listAreas: (query: ListQuery = {}) => http.get<PageResult<Area>>(`/sites/areas${listQueryString(query)}`),
  createArea: (area: Area) => http.post<Area>(`/sites/${area.siteId}/areas`, area),
  removeArea: (id: string) => http.delete<void>(`/sites/areas/${id}`),

  listRoutes: (query: ListQuery = {}) => http.get<PageResult<Route>>(`/routes${listQueryString(query)}`),
  getRoute: (id: string) => http.get<Route>(`/routes/${encodeURIComponent(id)}`),
  createRoute: (route: Route) => http.post<Route>('/routes', route),
  updateRoute: (id: string, patch: Partial<Route>) => http.patch<Route>(`/routes/${id}`, patch),
  removeRoute: (id: string) => http.delete<void>(`/routes/${id}`),
  listRouteRevisions: (routeId: string) => http.get<RouteRevision[]>(`/routes/${routeId}/revisions`),
  createRouteRevision: (routeId: string) => http.post<RouteRevision>(`/routes/${routeId}/revisions`),
  listRouteDeployments: (revisionId: string) => http.get<RouteDeployment[]>(`/route-revisions/${encodeURIComponent(revisionId)}/deployments`),
  createRouteDeployment: (revisionId: string, robotId: string, idempotencyKey: string) =>
    http.post<RouteDeployment>(`/route-revisions/${encodeURIComponent(revisionId)}/deployments`, { robotId }, { 'Idempotency-Key': idempotencyKey }),
  getRouteDeployment: (deploymentId: string) => http.get<RouteDeployment>(`/route-deployments/${encodeURIComponent(deploymentId)}`),
  reconcileRouteDeployment: (deploymentId: string) => http.post<RouteDeployment>(`/route-deployments/${encodeURIComponent(deploymentId)}/reconcile`),
  validateRouteDraft: (routeId: string, executorJson: RouteExecutorDocument, mapAssetId?: string) =>
    http.post<RouteDraftValidationReport>(`/routes/${routeId}/draft:validate`, { executorJson, mapAssetId }),
  getRouteDraft: (routeId: string) => http.get<PersistedRouteDraftReport>(`/routes/${routeId}/draft`),
  getRouteDraftCheck: (routeId: string) => http.get<PersistedRouteDraftReport>(`/routes/${routeId}/draft:check`),
  saveRouteDraft: (routeId: string, executorJson: RouteExecutorDocument, expectedVersion?: number, mapAssetId?: string) =>
    http.put<PersistedRouteDraftReport>(`/routes/${routeId}/draft`, { executorJson, expectedVersion, mapAssetId }),
  getRouteRevision: (revisionId: string) => http.get<RouteRevision>(`/route-revisions/${revisionId}`),
  addCheckpoint: (routeId: string, checkpoint: Checkpoint) =>
    http.post<Checkpoint>(`/routes/${routeId}/checkpoints`, checkpoint),
  updateCheckpoint: (routeId: string, checkpointId: string, patch: Partial<Checkpoint>) =>
    http.patch<Checkpoint>(`/routes/${routeId}/checkpoints/${checkpointId}`, patch),
  removeCheckpoint: (routeId: string, checkpointId: string) =>
    http.delete<void>(`/routes/${routeId}/checkpoints/${checkpointId}`),

  uploadMapAsset: (form: FormData) => http.postForm<MapAsset>('/map-assets', form),
  listMapAssets: (query: MapAssetQuery = {}) => http.get<MapAsset[]>(`/map-assets${buildMapAssetQuery(query)}`),
  getMapAsset: (id: string) => http.get<MapAsset>(`/map-assets/${id}`),
  getMapAssetYaml: (id: string) => http.get<Blob>(`/map-assets/${id}/yaml`),
  getMapAssetPgm: (id: string) => http.get<Blob>(`/map-assets/${id}/pgm`),
  removeMapAsset: (id: string) => http.delete<void>(`/map-assets/${id}`),
  reviewMapAsset: (id: string, input: MapAssetReviewInput) =>
    http.post<MapAsset>(`/map-assets/${encodeURIComponent(id)}/review`, input),

  listTasks: (query: ListQuery = {}) => http.get<PageResult<InspectionTask>>(`/tasks${listQueryString(query)}`),
  getTask: (id: string) => http.get<InspectionTask>(`/tasks/${encodeURIComponent(id)}`),
  createTask: (task: InspectionTask) => http.post<InspectionTask>('/tasks', task),
  dispatchTask: (id: string) => http.post<InspectionTask>(`/tasks/${id}/dispatch`),
  getTaskExecution: (id: string) => http.get<TaskExecution>(`/tasks/${encodeURIComponent(id)}/execution`),
  getTaskStartEligibility: (id: string) => http.get<TaskStartEligibility>(`/tasks/${encodeURIComponent(id)}/start-eligibility`),
  startTask: (id: string, startMode: TaskStartMode, idempotencyKey: string) =>
    http.post<TaskExecution>(`/tasks/${encodeURIComponent(id)}/start`, { startMode }, { 'Idempotency-Key': idempotencyKey }),
  pauseTask: (id: string, idempotencyKey: string) =>
    http.post<TaskExecution>(`/tasks/${encodeURIComponent(id)}/pause`, undefined, { 'Idempotency-Key': idempotencyKey }),
  resumeTask: (id: string, idempotencyKey: string) =>
    http.post<TaskExecution>(`/tasks/${encodeURIComponent(id)}/resume`, undefined, { 'Idempotency-Key': idempotencyKey }),
  takeoverTask: (id: string, reason: string, idempotencyKey: string) =>
    http.post<TaskExecution>(`/tasks/${encodeURIComponent(id)}/takeover`, { reason }, { 'Idempotency-Key': idempotencyKey }),
  cancelTask: (id: string, idempotencyKey: string) =>
    http.post<TaskExecution>(`/tasks/${encodeURIComponent(id)}/cancel`, undefined, { 'Idempotency-Key': idempotencyKey }),
  emergencyStopTask: (id: string, reason: string, idempotencyKey: string) =>
    http.post<TaskExecution>(`/tasks/${encodeURIComponent(id)}/emergency-stop`, { reason }, { 'Idempotency-Key': idempotencyKey }),
  taskEvents: (id: string, query: ListQuery = {}) => http.get<PageResult<TaskEvent>>(`/tasks/${id}/events${listQueryString(query)}`),
  getTaskEvent: (id: string) => http.get<TaskEvent>(`/tasks/events/${id}`),
  listRecords: (query: ListQuery = {}) => http.get<PageResult<InspectionRecord>>(`/records${listQueryString(query)}`),
  exportRecords: () => http.post<Blob>('/records/export'),

  listAlarms: (query: ListQuery = {}) => http.get<PageResult<Alarm>>(`/alarms${listQueryString(query)}`),
  getAlarm: (id: string) => http.get<Alarm>(`/alarms/${encodeURIComponent(id)}`),
  acknowledgeAlarm: (id: string) => http.post<Alarm>(`/alarms/${id}/ack`),
  acknowledgeAllAlarms: () => http.post<Alarm[]>('/alarms/ack-all'),
  getAlarmWorkOrderPolicy: () => openapiClient.alarms.getWorkOrderPolicy(),
  updateAlarmWorkOrderPolicy: (policy: Pick<AlarmWorkOrderPolicy, 'rules'>) =>
    openapiClient.alarms.updateWorkOrderPolicy(policy.rules),
  retryAlarmWorkOrder: (id: string) => http.post<Alarm>(`/alarms/${id}/retry-work-order`),

  listWorkOrders: (query: ListQuery = {}) => http.get<PageResult<WorkOrder>>(`/work-orders${listQueryString(query)}`),
  getWorkOrder: (id: string) => http.get<WorkOrder>(`/work-orders/${encodeURIComponent(id)}`),
  createWorkOrderFromAlarm: (alarmId: string) =>
    http.post<WorkOrder>(`/work-orders/from-alarm/${alarmId}`, {}),
  claimWorkOrder: (id: string) => http.post<WorkOrder>(`/work-orders/${id}/claim`),
  updateWorkOrderStatus: (
    id: string,
    status: WorkOrderStatus,
    extra?: { resolution?: string; review?: WorkOrderReviewInput },
  ) => http.patch<WorkOrder>(`/work-orders/${id}/status`, { status, ...extra }),

  listRobots: (query: ListQuery = {}) => http.get<PageResult<Robot>>(`/robots${listQueryString(query)}`),
  getRobot: (id: string) => http.get<Robot>(`/robots/${encodeURIComponent(id)}`),
  createRobot: (robot: Robot) => http.post<Robot>('/robots', robot),
  updateRobot: (id: string, patch: Partial<Robot>) => http.patch<Robot>(`/robots/${id}`, patch),
  removeRobot: (id: string) => http.delete<void>(`/robots/${id}`),
  listRobotHeartbeatStatus: (query: RobotHeartbeatStatusQuery = {}) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined) params.set(key, String(value))
    })
    const suffix = params.size ? `?${params.toString()}` : ''
    return http.get<RobotHeartbeatStatusPage>(`/robots/status${suffix}`)
  },
  getRobotHeartbeatStatus: (robotId: string) => http.get<RobotHeartbeatStatus>(`/robots/${encodeURIComponent(robotId)}/status`),

  getRobotLocation: (robotId: string) => http.get<RobotLocation>(`/robots/${encodeURIComponent(robotId)}/location`),
  listRobotLocations: (query: RobotLocationQuery = {}) => {
    const params = new URLSearchParams()
    if (query.siteId) params.set('siteId', query.siteId)
    if (query.online !== undefined) params.set('online', String(query.online))
    const suffix = params.size ? `?${params.toString()}` : ''
    return http.get<RobotLocation[]>(`/robots/locations${suffix}`)
  },
  getRobotTrack: (robotId: string, query: RobotTrackQuery = {}) => {
    const params = new URLSearchParams()
    if (query.start) params.set('start', query.start)
    if (query.end) params.set('end', query.end)
    if (query.executionId) params.set('executionId', query.executionId)
    if (query.limit !== undefined) params.set('limit', String(query.limit))
    const suffix = params.size ? `?${params.toString()}` : ''
    return http.get<RobotTrackResponse>(`/robots/${encodeURIComponent(robotId)}/track${suffix}`)
  },

  listDetectionTemplates: (query: ListQuery = {}) => http.get<PageResult<DetectionTemplate>>(`/detection-templates${listQueryString(query)}`),
  getDetectionTemplate: (id: string) => http.get<DetectionTemplate>(`/detection-templates/${encodeURIComponent(id)}`),
  createDetectionTemplate: (template: Omit<DetectionTemplate, 'id' | 'createdAt'>) =>
    http.post<DetectionTemplate>('/detection-templates', template),
  updateDetectionTemplate: (id: string, patch: Partial<DetectionTemplate>) =>
    http.patch<DetectionTemplate>(`/detection-templates/${id}`, patch),
  removeDetectionTemplate: (id: string) => http.delete<void>(`/detection-templates/${id}`),
  manualLocateDetection: (form: FormData) => http.postForm<ManualDetectionResponse>(`/detections/manual`, form),
  getManualLocateDetection: (requestId: string) => http.get<ManualDetectionResponse>(`/detections/manual/${requestId}`),
  listRobotInspectionImages: (query: ListQuery = {}) =>
    http.get<PageResult<RobotInspectionImage>>(`/robot-inspection-images${listQueryString(query)}`),
  importRobotInspectionImage: (form: FormData) =>
    http.postForm<RobotInspectionImage>('/robot-inspection-images/import', form),
  detectRobotInspectionImage: (imageId: string, detections: DetectionItem[]) =>
    http.post<DetectionRun>('/detections/robot-image', { imageId, detections }),
  listDetectionRuns: (query: ListQuery = {}) =>
    http.get<PageResult<DetectionRun>>(`/detections/runs${listQueryString(query)}`),
  getDetectionRun: (runId: string) => http.get<DetectionRun>(`/detections/runs/${encodeURIComponent(runId)}`),

  listNotifications: (query: ListQuery = {}) => http.get<PageResult<AppNotification>>(`/notifications${listQueryString(query)}`),
  getNotification: (id: string) => http.get<AppNotification>(`/notifications/${encodeURIComponent(id)}`),
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

  getDashboardOverview: () => http.get<DashboardOverview>('/dashboard/overview'),

  listAgentSessions: () => http.get<AgentSession[]>('/agents/sessions'),
  createAgentSession: (body: CreateAgentSessionRequest) => http.post<AgentSession>('/agents/sessions', body),
  getAgentSession: (id: string) => http.get<AgentSession>(`/agents/sessions/${id}`),
  rerunAgentSession: (id: string) => http.post<AgentSession>(`/agents/sessions/${id}/runs`),
  confirmAgentAction: (id: string) => http.post<AgentAction>(`/agents/actions/${id}/confirm`),
  rejectAgentAction: (id: string) => http.post<AgentAction>(`/agents/actions/${id}/reject`),

  listAgentCases: () => http.get<AgentCaseSummary[]>('/agent-cases'),
  createAgentCase: (body: CreateAgentCaseRequest) => http.post<AgentCaseSummary>('/agent-cases', body),
  getAgentCase: (id: string) => http.get<AgentCaseDetail>(`/agent-cases/${id}`),
  startAgentRun: (caseId: string, body: StartAgentRunRequest) => http.post<AgentRunSummary>(`/agent-cases/${caseId}/runs`, body),
  getAgentRun: (runId: string) => http.get<AgentRunDetail>(`/agent-runs/${runId}`),
  getAgentRunEvidence: (runId: string) => http.get<AuditedAgentEvidence[]>(`/agent-runs/${runId}/evidence`),
  submitAgentHumanInput: (runId: string, body: AgentHumanInputRequest) => http.post(`/agent-runs/${runId}/human-inputs`, body),
  cancelAuditedAgentRun: (runId: string) => http.post<AgentRunSummary>(`/agent-runs/${runId}/cancel`),
  approveAuditedAgentAction: (id: string, body: AgentActionDecisionRequest) => http.post<AuditedAgentAction>(`/agent-actions/${id}/approve`, body),
  rejectAuditedAgentAction: (id: string, body: AgentActionDecisionRequest) => http.post<AuditedAgentAction>(`/agent-actions/${id}/reject`, body),
  retryAuditedAgentAction: (id: string, body: AgentActionDecisionRequest) => http.post<AuditedAgentAction>(`/agent-actions/${id}/retry`, body),
}
