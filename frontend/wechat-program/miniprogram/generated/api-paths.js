/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */
const agentActionsActionId = '/api/v1/agent-actions/{actionId}'
const agentActionsActionIdApprove = '/api/v1/agent-actions/{actionId}/approve'
const agentActionsActionIdReject = '/api/v1/agent-actions/{actionId}/reject'
const agentActionsActionIdRetry = '/api/v1/agent-actions/{actionId}/retry'
const agentCases = '/api/v1/agent-cases'
const agentCasesCaseId = '/api/v1/agent-cases/{caseId}'
const agentCasesCaseIdRuns = '/api/v1/agent-cases/{caseId}/runs'
const agentRunsRunId = '/api/v1/agent-runs/{runId}'
const agentRunsRunIdCancel = '/api/v1/agent-runs/{runId}/cancel'
const agentRunsRunIdEvidence = '/api/v1/agent-runs/{runId}/evidence'
const agentRunsRunIdHumanInputs = '/api/v1/agent-runs/{runId}/human-inputs'
const agentRunsRunIdQuestion = '/api/v1/agent-runs/{runId}/question'
const agentRunsRunIdToolCalls = '/api/v1/agent-runs/{runId}/tool-calls'
const agentRunsRunIdTrace = '/api/v1/agent-runs/{runId}/trace'
const agentsActionsIdConfirm = '/api/v1/agents/actions/{id}/confirm'
const agentsActionsIdReject = '/api/v1/agents/actions/{id}/reject'
const agentsSessions = '/api/v1/agents/sessions'
const agentsSessionsId = '/api/v1/agents/sessions/{id}'
const agentsSessionsIdRuns = '/api/v1/agents/sessions/{id}/runs'
const alarms = '/api/v1/alarms'
const alarmsAckAll = '/api/v1/alarms/ack-all'
const alarmsWorkOrderPolicy = '/api/v1/alarms/work-order-policy'
const alarmsId = '/api/v1/alarms/{id}'
const alarmsIdAck = '/api/v1/alarms/{id}/ack'
const alarmsIdRetryWorkOrder = '/api/v1/alarms/{id}/retry-work-order'
const authLogin = '/api/v1/auth/login'
const authLogout = '/api/v1/auth/logout'
const authMe = '/api/v1/auth/me'
const authPassword = '/api/v1/auth/password'
const authPasswordReset = '/api/v1/auth/password/reset'
const authReauth = '/api/v1/auth/reauth'
const authRefresh = '/api/v1/auth/refresh'
const authRegister = '/api/v1/auth/register'
const authSmsSend = '/api/v1/auth/sms/send'
const dashboardOverview = '/api/v1/dashboard/overview'
const detectionTemplates = '/api/v1/detection-templates'
const detectionTemplatesId = '/api/v1/detection-templates/{id}'
const detectionsManual = '/api/v1/detections/manual'
const detectionsManualRequestId = '/api/v1/detections/manual/{requestId}'
const detectionsRobotImage = '/api/v1/detections/robot-image'
const detectionsRuns = '/api/v1/detections/runs'
const detectionsRunsId = '/api/v1/detections/runs/{id}'
const health = '/api/v1/health'
const internalRobotInspectionImages = '/api/v1/internal/robot-inspection-images'
const internalRobotMapAssets = '/api/v1/internal/robot-map-assets'
const mapAssets = '/api/v1/map-assets'
const mapAssetsId = '/api/v1/map-assets/{id}'
const mapAssetsIdPgm = '/api/v1/map-assets/{id}/pgm'
const mapAssetsIdReview = '/api/v1/map-assets/{id}/review'
const mapAssetsIdYaml = '/api/v1/map-assets/{id}/yaml'
const notifications = '/api/v1/notifications'
const notificationsReadAll = '/api/v1/notifications/read-all'
const notificationsId = '/api/v1/notifications/{id}'
const notificationsIdRead = '/api/v1/notifications/{id}/read'
const records = '/api/v1/records'
const recordsExport = '/api/v1/records/export'
const robotInspectionImages = '/api/v1/robot-inspection-images'
const robotInspectionImagesImport = '/api/v1/robot-inspection-images/import'
const robotInspectionImagesId = '/api/v1/robot-inspection-images/{id}'
const robots = '/api/v1/robots'
const robotsLocations = '/api/v1/robots/locations'
const robotsStatus = '/api/v1/robots/status'
const robotsId = '/api/v1/robots/{id}'
const robotsIdTelemetry = '/api/v1/robots/{id}/telemetry'
const robotsRobotIdLocation = '/api/v1/robots/{robotId}/location'
const robotsRobotIdStatus = '/api/v1/robots/{robotId}/status'
const robotsRobotIdTrack = '/api/v1/robots/{robotId}/track'
const routeDeploymentsDeploymentId = '/api/v1/route-deployments/{deploymentId}'
const routeDeploymentsDeploymentIdReconcile = '/api/v1/route-deployments/{deploymentId}/reconcile'
const routeRevisionsRevisionId = '/api/v1/route-revisions/{revisionId}'
const routeRevisionsRevisionIdDeployments = '/api/v1/route-revisions/{revisionId}/deployments'
const routes = '/api/v1/routes'
const routesId = '/api/v1/routes/{id}'
const routesIdCheckpoints = '/api/v1/routes/{id}/checkpoints'
const routesRouteIdCheckpointsCheckpointId = '/api/v1/routes/{routeId}/checkpoints/{checkpointId}'
const routesRouteIdDraft = '/api/v1/routes/{routeId}/draft'
const routesRouteIdDraftCheck = '/api/v1/routes/{routeId}/draft:check'
const routesRouteIdDraftValidate = '/api/v1/routes/{routeId}/draft:validate'
const routesRouteIdRevisions = '/api/v1/routes/{routeId}/revisions'
const sites = '/api/v1/sites'
const sitesAreas = '/api/v1/sites/areas'
const sitesAreasAreaId = '/api/v1/sites/areas/{areaId}'
const sitesSlamMaps = '/api/v1/sites/slam-maps'
const sitesId = '/api/v1/sites/{id}'
const sitesIdAreas = '/api/v1/sites/{id}/areas'
const sitesIdSlamMap = '/api/v1/sites/{id}/slam-map'
const sitesSiteIdAreasAreaId = '/api/v1/sites/{siteId}/areas/{areaId}'
const tasks = '/api/v1/tasks'
const tasksActive = '/api/v1/tasks/active'
const tasksEventsEventId = '/api/v1/tasks/events/{eventId}'
const tasksId = '/api/v1/tasks/{id}'
const tasksIdCancel = '/api/v1/tasks/{id}/cancel'
const tasksIdDispatch = '/api/v1/tasks/{id}/dispatch'
const tasksIdEmergencyStop = '/api/v1/tasks/{id}/emergency-stop'
const tasksIdEvents = '/api/v1/tasks/{id}/events'
const tasksIdExecution = '/api/v1/tasks/{id}/execution'
const tasksIdPause = '/api/v1/tasks/{id}/pause'
const tasksIdResume = '/api/v1/tasks/{id}/resume'
const tasksIdStart = '/api/v1/tasks/{id}/start'
const tasksIdStartEligibility = '/api/v1/tasks/{id}/start-eligibility'
const tasksIdTakeover = '/api/v1/tasks/{id}/takeover'
const users = '/api/v1/users'
const usersMe = '/api/v1/users/me'
const usersMeActivities = '/api/v1/users/me/activities'
const usersMePreferences = '/api/v1/users/me/preferences'
const usersIdEnabled = '/api/v1/users/{id}/enabled'
const usersIdRole = '/api/v1/users/{id}/role'
const workOrders = '/api/v1/work-orders'
const workOrdersFromAlarmAlarmId = '/api/v1/work-orders/from-alarm/{alarmId}'
const workOrdersId = '/api/v1/work-orders/{id}'
const workOrdersIdClaim = '/api/v1/work-orders/{id}/claim'
const workOrdersIdPhotos = '/api/v1/work-orders/{id}/photos'
const workOrdersIdStatus = '/api/v1/work-orders/{id}/status'

function apiRel(path) {
  return path.replace(/^\/api\/v1/, '')
}

module.exports = {
  agentActionsActionId,
  agentActionsActionIdApprove,
  agentActionsActionIdReject,
  agentActionsActionIdRetry,
  agentCases,
  agentCasesCaseId,
  agentCasesCaseIdRuns,
  agentRunsRunId,
  agentRunsRunIdCancel,
  agentRunsRunIdEvidence,
  agentRunsRunIdHumanInputs,
  agentRunsRunIdQuestion,
  agentRunsRunIdToolCalls,
  agentRunsRunIdTrace,
  agentsActionsIdConfirm,
  agentsActionsIdReject,
  agentsSessions,
  agentsSessionsId,
  agentsSessionsIdRuns,
  alarms,
  alarmsAckAll,
  alarmsWorkOrderPolicy,
  alarmsId,
  alarmsIdAck,
  alarmsIdRetryWorkOrder,
  authLogin,
  authLogout,
  authMe,
  authPassword,
  authPasswordReset,
  authReauth,
  authRefresh,
  authRegister,
  authSmsSend,
  dashboardOverview,
  detectionTemplates,
  detectionTemplatesId,
  detectionsManual,
  detectionsManualRequestId,
  detectionsRobotImage,
  detectionsRuns,
  detectionsRunsId,
  health,
  internalRobotInspectionImages,
  internalRobotMapAssets,
  mapAssets,
  mapAssetsId,
  mapAssetsIdPgm,
  mapAssetsIdReview,
  mapAssetsIdYaml,
  notifications,
  notificationsReadAll,
  notificationsId,
  notificationsIdRead,
  records,
  recordsExport,
  robotInspectionImages,
  robotInspectionImagesImport,
  robotInspectionImagesId,
  robots,
  robotsLocations,
  robotsStatus,
  robotsId,
  robotsIdTelemetry,
  robotsRobotIdLocation,
  robotsRobotIdStatus,
  robotsRobotIdTrack,
  routeDeploymentsDeploymentId,
  routeDeploymentsDeploymentIdReconcile,
  routeRevisionsRevisionId,
  routeRevisionsRevisionIdDeployments,
  routes,
  routesId,
  routesIdCheckpoints,
  routesRouteIdCheckpointsCheckpointId,
  routesRouteIdDraft,
  routesRouteIdDraftCheck,
  routesRouteIdDraftValidate,
  routesRouteIdRevisions,
  sites,
  sitesAreas,
  sitesAreasAreaId,
  sitesSlamMaps,
  sitesId,
  sitesIdAreas,
  sitesIdSlamMap,
  sitesSiteIdAreasAreaId,
  tasks,
  tasksActive,
  tasksEventsEventId,
  tasksId,
  tasksIdCancel,
  tasksIdDispatch,
  tasksIdEmergencyStop,
  tasksIdEvents,
  tasksIdExecution,
  tasksIdPause,
  tasksIdResume,
  tasksIdStart,
  tasksIdStartEligibility,
  tasksIdTakeover,
  users,
  usersMe,
  usersMeActivities,
  usersMePreferences,
  usersIdEnabled,
  usersIdRole,
  workOrders,
  workOrdersFromAlarmAlarmId,
  workOrdersId,
  workOrdersIdClaim,
  workOrdersIdPhotos,
  workOrdersIdStatus,
  API_PATHS: {
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
    authPasswordReset: '/api/v1/auth/password/reset',
    authReauth: '/api/v1/auth/reauth',
    authRefresh: '/api/v1/auth/refresh',
    authRegister: '/api/v1/auth/register',
    authSmsSend: '/api/v1/auth/sms/send',
    dashboardOverview: '/api/v1/dashboard/overview',
    detectionTemplates: '/api/v1/detection-templates',
    detectionTemplatesId: '/api/v1/detection-templates/{id}',
    detectionsManual: '/api/v1/detections/manual',
    detectionsManualRequestId: '/api/v1/detections/manual/{requestId}',
    detectionsRobotImage: '/api/v1/detections/robot-image',
    detectionsRuns: '/api/v1/detections/runs',
    detectionsRunsId: '/api/v1/detections/runs/{id}',
    health: '/api/v1/health',
    internalRobotInspectionImages: '/api/v1/internal/robot-inspection-images',
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
    robotInspectionImages: '/api/v1/robot-inspection-images',
    robotInspectionImagesImport: '/api/v1/robot-inspection-images/import',
    robotInspectionImagesId: '/api/v1/robot-inspection-images/{id}',
    robots: '/api/v1/robots',
    robotsLocations: '/api/v1/robots/locations',
    robotsStatus: '/api/v1/robots/status',
    robotsId: '/api/v1/robots/{id}',
    robotsIdTelemetry: '/api/v1/robots/{id}/telemetry',
    robotsRobotIdLocation: '/api/v1/robots/{robotId}/location',
    robotsRobotIdStatus: '/api/v1/robots/{robotId}/status',
    robotsRobotIdTrack: '/api/v1/robots/{robotId}/track',
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
    tasksIdEmergencyStop: '/api/v1/tasks/{id}/emergency-stop',
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
    workOrdersIdPhotos: '/api/v1/work-orders/{id}/photos',
    workOrdersIdStatus: '/api/v1/work-orders/{id}/status',
  },
  apiRel,
}
