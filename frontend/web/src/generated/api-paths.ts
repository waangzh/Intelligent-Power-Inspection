/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */
export const agentActionsActionId = '/api/v1/agent-actions/{actionId}' as const
export const agentActionsActionIdApprove = '/api/v1/agent-actions/{actionId}/approve' as const
export const agentActionsActionIdReject = '/api/v1/agent-actions/{actionId}/reject' as const
export const agentActionsActionIdRetry = '/api/v1/agent-actions/{actionId}/retry' as const
export const agentCases = '/api/v1/agent-cases' as const
export const agentCasesCaseId = '/api/v1/agent-cases/{caseId}' as const
export const agentCasesCaseIdRuns = '/api/v1/agent-cases/{caseId}/runs' as const
export const agentRunsRunId = '/api/v1/agent-runs/{runId}' as const
export const agentRunsRunIdCancel = '/api/v1/agent-runs/{runId}/cancel' as const
export const agentRunsRunIdEvidence = '/api/v1/agent-runs/{runId}/evidence' as const
export const agentRunsRunIdHumanInputs = '/api/v1/agent-runs/{runId}/human-inputs' as const
export const agentRunsRunIdQuestion = '/api/v1/agent-runs/{runId}/question' as const
export const agentRunsRunIdToolCalls = '/api/v1/agent-runs/{runId}/tool-calls' as const
export const agentRunsRunIdTrace = '/api/v1/agent-runs/{runId}/trace' as const
export const agentsActionsIdConfirm = '/api/v1/agents/actions/{id}/confirm' as const
export const agentsActionsIdReject = '/api/v1/agents/actions/{id}/reject' as const
export const agentsSessions = '/api/v1/agents/sessions' as const
export const agentsSessionsId = '/api/v1/agents/sessions/{id}' as const
export const agentsSessionsIdRuns = '/api/v1/agents/sessions/{id}/runs' as const
export const alarms = '/api/v1/alarms' as const
export const alarmsAckAll = '/api/v1/alarms/ack-all' as const
export const alarmsWorkOrderPolicy = '/api/v1/alarms/work-order-policy' as const
export const alarmsId = '/api/v1/alarms/{id}' as const
export const alarmsIdAck = '/api/v1/alarms/{id}/ack' as const
export const alarmsIdRetryWorkOrder = '/api/v1/alarms/{id}/retry-work-order' as const
export const authLogin = '/api/v1/auth/login' as const
export const authLogout = '/api/v1/auth/logout' as const
export const authMe = '/api/v1/auth/me' as const
export const authPassword = '/api/v1/auth/password' as const
export const authReauth = '/api/v1/auth/reauth' as const
export const authRefresh = '/api/v1/auth/refresh' as const
export const authRegister = '/api/v1/auth/register' as const
export const dashboardOverview = '/api/v1/dashboard/overview' as const
export const detectionTemplates = '/api/v1/detection-templates' as const
export const detectionTemplatesId = '/api/v1/detection-templates/{id}' as const
export const detectionsManual = '/api/v1/detections/manual' as const
export const detectionsManualRequestId = '/api/v1/detections/manual/{requestId}' as const
export const health = '/api/v1/health' as const
export const internalRobotMapAssets = '/api/v1/internal/robot-map-assets' as const
export const mapAssets = '/api/v1/map-assets' as const
export const mapAssetsId = '/api/v1/map-assets/{id}' as const
export const mapAssetsIdPgm = '/api/v1/map-assets/{id}/pgm' as const
export const mapAssetsIdReview = '/api/v1/map-assets/{id}/review' as const
export const mapAssetsIdYaml = '/api/v1/map-assets/{id}/yaml' as const
export const notifications = '/api/v1/notifications' as const
export const notificationsReadAll = '/api/v1/notifications/read-all' as const
export const notificationsId = '/api/v1/notifications/{id}' as const
export const notificationsIdRead = '/api/v1/notifications/{id}/read' as const
export const records = '/api/v1/records' as const
export const recordsExport = '/api/v1/records/export' as const
export const robots = '/api/v1/robots' as const
export const robotsStatus = '/api/v1/robots/status' as const
export const robotsId = '/api/v1/robots/{id}' as const
export const robotsIdTelemetry = '/api/v1/robots/{id}/telemetry' as const
export const robotsRobotIdStatus = '/api/v1/robots/{robotId}/status' as const
export const routeDeploymentsDeploymentId = '/api/v1/route-deployments/{deploymentId}' as const
export const routeDeploymentsDeploymentIdReconcile = '/api/v1/route-deployments/{deploymentId}/reconcile' as const
export const routeRevisionsRevisionId = '/api/v1/route-revisions/{revisionId}' as const
export const routeRevisionsRevisionIdDeployments = '/api/v1/route-revisions/{revisionId}/deployments' as const
export const routes = '/api/v1/routes' as const
export const routesId = '/api/v1/routes/{id}' as const
export const routesIdCheckpoints = '/api/v1/routes/{id}/checkpoints' as const
export const routesRouteIdCheckpointsCheckpointId = '/api/v1/routes/{routeId}/checkpoints/{checkpointId}' as const
export const routesRouteIdDraft = '/api/v1/routes/{routeId}/draft' as const
export const routesRouteIdDraftCheck = '/api/v1/routes/{routeId}/draft:check' as const
export const routesRouteIdDraftValidate = '/api/v1/routes/{routeId}/draft:validate' as const
export const routesRouteIdRevisions = '/api/v1/routes/{routeId}/revisions' as const
export const sites = '/api/v1/sites' as const
export const sitesAreas = '/api/v1/sites/areas' as const
export const sitesAreasAreaId = '/api/v1/sites/areas/{areaId}' as const
export const sitesSlamMaps = '/api/v1/sites/slam-maps' as const
export const sitesId = '/api/v1/sites/{id}' as const
export const sitesIdAreas = '/api/v1/sites/{id}/areas' as const
export const sitesIdSlamMap = '/api/v1/sites/{id}/slam-map' as const
export const sitesSiteIdAreasAreaId = '/api/v1/sites/{siteId}/areas/{areaId}' as const
export const tasks = '/api/v1/tasks' as const
export const tasksActive = '/api/v1/tasks/active' as const
export const tasksEventsEventId = '/api/v1/tasks/events/{eventId}' as const
export const tasksId = '/api/v1/tasks/{id}' as const
export const tasksIdCancel = '/api/v1/tasks/{id}/cancel' as const
export const tasksIdDispatch = '/api/v1/tasks/{id}/dispatch' as const
export const tasksIdEvents = '/api/v1/tasks/{id}/events' as const
export const tasksIdExecution = '/api/v1/tasks/{id}/execution' as const
export const tasksIdPause = '/api/v1/tasks/{id}/pause' as const
export const tasksIdResume = '/api/v1/tasks/{id}/resume' as const
export const tasksIdStart = '/api/v1/tasks/{id}/start' as const
export const tasksIdStartEligibility = '/api/v1/tasks/{id}/start-eligibility' as const
export const tasksIdTakeover = '/api/v1/tasks/{id}/takeover' as const
export const users = '/api/v1/users' as const
export const usersMe = '/api/v1/users/me' as const
export const usersMeActivities = '/api/v1/users/me/activities' as const
export const usersMePreferences = '/api/v1/users/me/preferences' as const
export const usersIdEnabled = '/api/v1/users/{id}/enabled' as const
export const usersIdRole = '/api/v1/users/{id}/role' as const
export const workOrders = '/api/v1/work-orders' as const
export const workOrdersFromAlarmAlarmId = '/api/v1/work-orders/from-alarm/{alarmId}' as const
export const workOrdersId = '/api/v1/work-orders/{id}' as const
export const workOrdersIdClaim = '/api/v1/work-orders/{id}/claim' as const
export const workOrdersIdStatus = '/api/v1/work-orders/{id}/status' as const

export const API_PATHS = {
  agentActionsActionId: '/api/v1/agent-actions/{actionId}',
  agentActionsActionIdApprove: '/api/v1/agent-actions/{actionId}/approve',
  agentActionsActionIdReject: '/api/v1/agent-actions/{actionId}/reject',
  agentActionsActionIdRetry: '/api/v1/agent-actions/{actionId}/retry',
  agentCases: '/api/v1/agent-cases',
  agentCasesCaseId: '/api/v1/agent-cases/{caseId}',
  agentCasesCaseIdRuns: '/api/v1/agent-cases/{caseId}/runs',
  agentRunsRunId: '/api/v1/agent-runs/{runId}',
  agentRunsRunIdCancel: '/api/v1/agent-runs/{runId}/cancel',
  agentRunsRunIdEvidence: '/api/v1/agent-runs/{runId}/evidence',
  agentRunsRunIdHumanInputs: '/api/v1/agent-runs/{runId}/human-inputs',
  agentRunsRunIdQuestion: '/api/v1/agent-runs/{runId}/question',
  agentRunsRunIdToolCalls: '/api/v1/agent-runs/{runId}/tool-calls',
  agentRunsRunIdTrace: '/api/v1/agent-runs/{runId}/trace',
  agentsActionsIdConfirm: '/api/v1/agents/actions/{id}/confirm',
  agentsActionsIdReject: '/api/v1/agents/actions/{id}/reject',
  agentsSessions: '/api/v1/agents/sessions',
  agentsSessionsId: '/api/v1/agents/sessions/{id}',
  agentsSessionsIdRuns: '/api/v1/agents/sessions/{id}/runs',
  alarms: '/api/v1/alarms',
  alarmsAckAll: '/api/v1/alarms/ack-all',
  alarmsWorkOrderPolicy: '/api/v1/alarms/work-order-policy',
  alarmsId: '/api/v1/alarms/{id}',
  alarmsIdAck: '/api/v1/alarms/{id}/ack',
  alarmsIdRetryWorkOrder: '/api/v1/alarms/{id}/retry-work-order',
  authLogin: '/api/v1/auth/login',
  authLogout: '/api/v1/auth/logout',
  authMe: '/api/v1/auth/me',
  authPassword: '/api/v1/auth/password',
  authReauth: '/api/v1/auth/reauth',
  authRefresh: '/api/v1/auth/refresh',
  authRegister: '/api/v1/auth/register',
  dashboardOverview: '/api/v1/dashboard/overview',
  detectionTemplates: '/api/v1/detection-templates',
  detectionTemplatesId: '/api/v1/detection-templates/{id}',
  detectionsManual: '/api/v1/detections/manual',
  detectionsManualRequestId: '/api/v1/detections/manual/{requestId}',
  health: '/api/v1/health',
  internalRobotMapAssets: '/api/v1/internal/robot-map-assets',
  mapAssets: '/api/v1/map-assets',
  mapAssetsId: '/api/v1/map-assets/{id}',
  mapAssetsIdPgm: '/api/v1/map-assets/{id}/pgm',
  mapAssetsIdReview: '/api/v1/map-assets/{id}/review',
  mapAssetsIdYaml: '/api/v1/map-assets/{id}/yaml',
  notifications: '/api/v1/notifications',
  notificationsReadAll: '/api/v1/notifications/read-all',
  notificationsId: '/api/v1/notifications/{id}',
  notificationsIdRead: '/api/v1/notifications/{id}/read',
  records: '/api/v1/records',
  recordsExport: '/api/v1/records/export',
  robots: '/api/v1/robots',
  robotsStatus: '/api/v1/robots/status',
  robotsId: '/api/v1/robots/{id}',
  robotsIdTelemetry: '/api/v1/robots/{id}/telemetry',
  robotsRobotIdStatus: '/api/v1/robots/{robotId}/status',
  routeDeploymentsDeploymentId: '/api/v1/route-deployments/{deploymentId}',
  routeDeploymentsDeploymentIdReconcile: '/api/v1/route-deployments/{deploymentId}/reconcile',
  routeRevisionsRevisionId: '/api/v1/route-revisions/{revisionId}',
  routeRevisionsRevisionIdDeployments: '/api/v1/route-revisions/{revisionId}/deployments',
  routes: '/api/v1/routes',
  routesId: '/api/v1/routes/{id}',
  routesIdCheckpoints: '/api/v1/routes/{id}/checkpoints',
  routesRouteIdCheckpointsCheckpointId: '/api/v1/routes/{routeId}/checkpoints/{checkpointId}',
  routesRouteIdDraft: '/api/v1/routes/{routeId}/draft',
  routesRouteIdDraftCheck: '/api/v1/routes/{routeId}/draft:check',
  routesRouteIdDraftValidate: '/api/v1/routes/{routeId}/draft:validate',
  routesRouteIdRevisions: '/api/v1/routes/{routeId}/revisions',
  sites: '/api/v1/sites',
  sitesAreas: '/api/v1/sites/areas',
  sitesAreasAreaId: '/api/v1/sites/areas/{areaId}',
  sitesSlamMaps: '/api/v1/sites/slam-maps',
  sitesId: '/api/v1/sites/{id}',
  sitesIdAreas: '/api/v1/sites/{id}/areas',
  sitesIdSlamMap: '/api/v1/sites/{id}/slam-map',
  sitesSiteIdAreasAreaId: '/api/v1/sites/{siteId}/areas/{areaId}',
  tasks: '/api/v1/tasks',
  tasksActive: '/api/v1/tasks/active',
  tasksEventsEventId: '/api/v1/tasks/events/{eventId}',
  tasksId: '/api/v1/tasks/{id}',
  tasksIdCancel: '/api/v1/tasks/{id}/cancel',
  tasksIdDispatch: '/api/v1/tasks/{id}/dispatch',
  tasksIdEvents: '/api/v1/tasks/{id}/events',
  tasksIdExecution: '/api/v1/tasks/{id}/execution',
  tasksIdPause: '/api/v1/tasks/{id}/pause',
  tasksIdResume: '/api/v1/tasks/{id}/resume',
  tasksIdStart: '/api/v1/tasks/{id}/start',
  tasksIdStartEligibility: '/api/v1/tasks/{id}/start-eligibility',
  tasksIdTakeover: '/api/v1/tasks/{id}/takeover',
  users: '/api/v1/users',
  usersMe: '/api/v1/users/me',
  usersMeActivities: '/api/v1/users/me/activities',
  usersMePreferences: '/api/v1/users/me/preferences',
  usersIdEnabled: '/api/v1/users/{id}/enabled',
  usersIdRole: '/api/v1/users/{id}/role',
  workOrders: '/api/v1/work-orders',
  workOrdersFromAlarmAlarmId: '/api/v1/work-orders/from-alarm/{alarmId}',
  workOrdersId: '/api/v1/work-orders/{id}',
  workOrdersIdClaim: '/api/v1/work-orders/{id}/claim',
  workOrdersIdStatus: '/api/v1/work-orders/{id}/status',
} as const

export function apiRel(name: keyof typeof API_PATHS): string {
  return API_PATHS[name].replace(/^\/api\/v1/, '')
}
