/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */
const { get, post, put, patch, del } = require('../utils/request')
const { API_PATHS, apiRel } = require('./api-paths')

function buildPath(apiPath, params = {}) {
  let relPath = apiRel(apiPath)
  for (const [key, value] of Object.entries(params)) {
    relPath = relPath.replace(`{${key}}`, encodeURIComponent(String(value)))
  }
  return relPath
}

/** OpenAPI 自动生成 — 路径与 backend openapi.json 一致；业务层优先用 services 别名 */
const openapiClient = {
  agentActions: {
    action(actionId, query) { return get(buildPath(API_PATHS.agentActionsActionId, { actionId }), query) },
    approve(actionId, body) { return post(buildPath(API_PATHS.agentActionsActionIdApprove, { actionId }), body) },
    reject(actionId, body) { return post(buildPath(API_PATHS.agentActionsActionIdReject, { actionId }), body) },
    retry(actionId, body) { return post(buildPath(API_PATHS.agentActionsActionIdRetry, { actionId }), body) },
  },
  agentCases: {
    getAgentCases(query) { return get(buildPath(API_PATHS.agentCases), query) },
    create3(body) { return post(buildPath(API_PATHS.agentCases), body) },
    detail1(caseId, query) { return get(buildPath(API_PATHS.agentCasesCaseId, { caseId }), query) },
    runs(caseId, query) { return get(buildPath(API_PATHS.agentCasesCaseIdRuns, { caseId }), query) },
    startRun(caseId, body) { return post(buildPath(API_PATHS.agentCasesCaseIdRuns, { caseId }), body) },
  },
  agentRuns: {
    run(runId, query) { return get(buildPath(API_PATHS.agentRunsRunId, { runId }), query) },
    cancel1(runId, body) { return post(buildPath(API_PATHS.agentRunsRunIdCancel, { runId }), body) },
    evidence(runId, query) { return get(buildPath(API_PATHS.agentRunsRunIdEvidence, { runId }), query) },
    submitHumanInput(runId, body) { return post(buildPath(API_PATHS.agentRunsRunIdHumanInputs, { runId }), body) },
    question(runId, query) { return get(buildPath(API_PATHS.agentRunsRunIdQuestion, { runId }), query) },
    toolCalls(runId, query) { return get(buildPath(API_PATHS.agentRunsRunIdToolCalls, { runId }), query) },
    trace(runId, query) { return get(buildPath(API_PATHS.agentRunsRunIdTrace, { runId }), query) },
  },
  agents: {
    confirmAction(id, body) { return post(buildPath(API_PATHS.agentsActionsIdConfirm, { id }), body) },
    rejectAction(id, body) { return post(buildPath(API_PATHS.agentsActionsIdReject, { id }), body) },
    sessions(query) { return get(buildPath(API_PATHS.agentsSessions), query) },
    createSession(body) { return post(buildPath(API_PATHS.agentsSessions), body) },
    session(id, query) { return get(buildPath(API_PATHS.agentsSessionsId, { id }), query) },
    rerun(id, body) { return post(buildPath(API_PATHS.agentsSessionsIdRuns, { id }), body) },
  },
  alarms: {
    getAlarms(query) { return get(buildPath(API_PATHS.alarms), query) },
    alarm(id, query) { return get(buildPath(API_PATHS.alarmsId, { id }), query) },
    acknowledge(id, body) { return post(buildPath(API_PATHS.alarmsIdAck, { id }), body) },
    retryWorkOrder(id, body) { return post(buildPath(API_PATHS.alarmsIdRetryWorkOrder, { id }), body) },
    acknowledgeAll(body) { return post(buildPath(API_PATHS.alarmsAckAll), body) },
    workOrderPolicy(query) { return get(buildPath(API_PATHS.alarmsWorkOrderPolicy), query) },
    updateWorkOrderPolicy(body) { return put(buildPath(API_PATHS.alarmsWorkOrderPolicy), body) },
  },
  auth: {
    login(body) { return post(buildPath(API_PATHS.authLogin), body) },
    logout(body) { return post(buildPath(API_PATHS.authLogout), body) },
    me(query) { return get(buildPath(API_PATHS.authMe), query) },
    updateMe1(body) { return patch(buildPath(API_PATHS.authMe), body) },
    changePassword(body) { return put(buildPath(API_PATHS.authPassword), body) },
    reauth(body) { return post(buildPath(API_PATHS.authReauth), body) },
    refresh(body) { return post(buildPath(API_PATHS.authRefresh), body) },
    register(body) { return post(buildPath(API_PATHS.authRegister), body) },
  },
  dashboard: {
    overview(query) { return get(buildPath(API_PATHS.dashboardOverview), query) },
  },
  detections: {
    detect(body) { return post(buildPath(API_PATHS.detectionsManual), body) },
    get2(requestId, query) { return get(buildPath(API_PATHS.detectionsManualRequestId, { requestId }), query) },
  },
  detectionTemplates: {
    templates(query) { return get(buildPath(API_PATHS.detectionTemplates), query) },
    addTemplate(body) { return post(buildPath(API_PATHS.detectionTemplates), body) },
    removeTemplate(id) { return del(buildPath(API_PATHS.detectionTemplatesId, { id })) },
    template(id, query) { return get(buildPath(API_PATHS.detectionTemplatesId, { id }), query) },
    updateTemplate(id, body) { return patch(buildPath(API_PATHS.detectionTemplatesId, { id }), body) },
  },
  health: {
    health(query) { return get(buildPath(API_PATHS.health), query) },
  },
  internal: {
    upload1(body) { return post(buildPath(API_PATHS.internalRobotMapAssets), body) },
  },
  mapAssets: {
    getMapAssets(query) { return get(buildPath(API_PATHS.mapAssets), query) },
    upload(body) { return post(buildPath(API_PATHS.mapAssets), body) },
    delete1(id) { return del(buildPath(API_PATHS.mapAssetsId, { id })) },
    metadata(id, query) { return get(buildPath(API_PATHS.mapAssetsId, { id }), query) },
    pgm(id, query) { return get(buildPath(API_PATHS.mapAssetsIdPgm, { id }), query) },
    review(id, body) { return post(buildPath(API_PATHS.mapAssetsIdReview, { id }), body) },
    yaml(id, query) { return get(buildPath(API_PATHS.mapAssetsIdYaml, { id }), query) },
  },
  notifications: {
    getNotifications(query) { return get(buildPath(API_PATHS.notifications), query) },
    remove(id) { return del(buildPath(API_PATHS.notificationsId, { id })) },
    notification(id, query) { return get(buildPath(API_PATHS.notificationsId, { id }), query) },
    markRead(id, body) { return patch(buildPath(API_PATHS.notificationsIdRead, { id }), body) },
    markAllRead(body) { return patch(buildPath(API_PATHS.notificationsReadAll), body) },
  },
  records: {
    getRecords(query) { return get(buildPath(API_PATHS.records), query) },
    exportCsv(body) { return post(buildPath(API_PATHS.recordsExport), body) },
  },
  robots: {
    listRobots(query) { return get(buildPath(API_PATHS.robots), query) },
    createRobot(body) { return post(buildPath(API_PATHS.robots), body) },
    deleteRobot(id) { return del(buildPath(API_PATHS.robotsId, { id })) },
    robot(id, query) { return get(buildPath(API_PATHS.robotsId, { id }), query) },
    updateRobot(id, body) { return patch(buildPath(API_PATHS.robotsId, { id }), body) },
    telemetry(id, query) { return get(buildPath(API_PATHS.robotsIdTelemetry, { id }), query) },
    detail(robotId, query) { return get(buildPath(API_PATHS.robotsRobotIdStatus, { robotId }), query) },
    getStatus(query) { return get(buildPath(API_PATHS.robotsStatus), query) },
  },
  routeDeployments: {
    get1(deploymentId, query) { return get(buildPath(API_PATHS.routeDeploymentsDeploymentId, { deploymentId }), query) },
    reconcile(deploymentId, body) { return post(buildPath(API_PATHS.routeDeploymentsDeploymentIdReconcile, { deploymentId }), body) },
  },
  routeRevisions: {
    getRouteRevisions(revisionId, query) { return get(buildPath(API_PATHS.routeRevisionsRevisionId, { revisionId }), query) },
    getDeployments(revisionId, query) { return get(buildPath(API_PATHS.routeRevisionsRevisionIdDeployments, { revisionId }), query) },
    create2(revisionId, body) { return post(buildPath(API_PATHS.routeRevisionsRevisionIdDeployments, { revisionId }), body) },
  },
  routes: {
    getRoutes(query) { return get(buildPath(API_PATHS.routes), query) },
    createRoute(body) { return post(buildPath(API_PATHS.routes), body) },
    deleteRoute(id) { return del(buildPath(API_PATHS.routesId, { id })) },
    getRoutes2(id, query) { return get(buildPath(API_PATHS.routesId, { id }), query) },
    updateRoute(id, body) { return patch(buildPath(API_PATHS.routesId, { id }), body) },
    replaceRoute(id, body) { return put(buildPath(API_PATHS.routesId, { id }), body) },
    routeCheckpoints(id, query) { return get(buildPath(API_PATHS.routesIdCheckpoints, { id }), query) },
    addCheckpoint(id, body) { return post(buildPath(API_PATHS.routesIdCheckpoints, { id }), body) },
    deleteCheckpoint(routeId, checkpointId) { return del(buildPath(API_PATHS.routesRouteIdCheckpointsCheckpointId, { routeId, checkpointId })) },
    updateCheckpoint(routeId, checkpointId, body) { return patch(buildPath(API_PATHS.routesRouteIdCheckpointsCheckpointId, { routeId, checkpointId }), body) },
    getDraft(routeId, query) { return get(buildPath(API_PATHS.routesRouteIdDraft, { routeId }), query) },
    saveDraft(routeId, body) { return put(buildPath(API_PATHS.routesRouteIdDraft, { routeId }), body) },
    getDraftCheck(routeId, query) { return get(buildPath(API_PATHS.routesRouteIdDraftCheck, { routeId }), query) },
    validateDraft(routeId, body) { return post(buildPath(API_PATHS.routesRouteIdDraftValidate, { routeId }), body) },
    getRevisions(routeId, query) { return get(buildPath(API_PATHS.routesRouteIdRevisions, { routeId }), query) },
    create1(routeId, body) { return post(buildPath(API_PATHS.routesRouteIdRevisions, { routeId }), body) },
  },
  sites: {
    getSites(query) { return get(buildPath(API_PATHS.sites), query) },
    createSite(body) { return post(buildPath(API_PATHS.sites), body) },
    deleteSite(id) { return del(buildPath(API_PATHS.sitesId, { id })) },
    site(id, query) { return get(buildPath(API_PATHS.sitesId, { id }), query) },
    updateSite(id, body) { return patch(buildPath(API_PATHS.sitesId, { id }), body) },
    areasBySite(id, query) { return get(buildPath(API_PATHS.sitesIdAreas, { id }), query) },
    createArea(id, body) { return post(buildPath(API_PATHS.sitesIdAreas, { id }), body) },
    deleteSlamMap(id) { return del(buildPath(API_PATHS.sitesIdSlamMap, { id })) },
    slamMap(id, query) { return get(buildPath(API_PATHS.sitesIdSlamMap, { id }), query) },
    saveSlamMap(id, body) { return put(buildPath(API_PATHS.sitesIdSlamMap, { id }), body) },
    deleteAreaInSite(siteId, areaId) { return del(buildPath(API_PATHS.sitesSiteIdAreasAreaId, { siteId, areaId })) },
    updateArea(siteId, areaId, body) { return patch(buildPath(API_PATHS.sitesSiteIdAreasAreaId, { siteId, areaId }), body) },
    areas(query) { return get(buildPath(API_PATHS.sitesAreas), query) },
    deleteArea(areaId) { return del(buildPath(API_PATHS.sitesAreasAreaId, { areaId })) },
    slamMaps(query) { return get(buildPath(API_PATHS.sitesSlamMaps), query) },
  },
  tasks: {
    getTasks(query) { return get(buildPath(API_PATHS.tasks), query) },
    createTask(body) { return post(buildPath(API_PATHS.tasks), body) },
    deleteTask(id) { return del(buildPath(API_PATHS.tasksId, { id })) },
    task(id, query) { return get(buildPath(API_PATHS.tasksId, { id }), query) },
    updateTask(id, body) { return patch(buildPath(API_PATHS.tasksId, { id }), body) },
    cancel(id, body) { return post(buildPath(API_PATHS.tasksIdCancel, { id }), body) },
    dispatch(id, body) { return post(buildPath(API_PATHS.tasksIdDispatch, { id }), body) },
    events(id, query) { return get(buildPath(API_PATHS.tasksIdEvents, { id }), query) },
    execution(id, query) { return get(buildPath(API_PATHS.tasksIdExecution, { id }), query) },
    pause(id, body) { return post(buildPath(API_PATHS.tasksIdPause, { id }), body) },
    resume(id, body) { return post(buildPath(API_PATHS.tasksIdResume, { id }), body) },
    start(id, body) { return post(buildPath(API_PATHS.tasksIdStart, { id }), body) },
    startEligibility(id, query) { return get(buildPath(API_PATHS.tasksIdStartEligibility, { id }), query) },
    takeover(id, body) { return post(buildPath(API_PATHS.tasksIdTakeover, { id }), body) },
    active(query) { return get(buildPath(API_PATHS.tasksActive), query) },
    event(eventId, query) { return get(buildPath(API_PATHS.tasksEventsEventId, { eventId }), query) },
  },
  users: {
    getUsers(query) { return get(buildPath(API_PATHS.users), query) },
    patchEnabled(id, body) { return patch(buildPath(API_PATHS.usersIdEnabled, { id }), body) },
    patchRole(id, body) { return patch(buildPath(API_PATHS.usersIdRole, { id }), body) },
    updateMe(body) { return patch(buildPath(API_PATHS.usersMe), body) },
    activities(query) { return get(buildPath(API_PATHS.usersMeActivities), query) },
    preferences(query) { return get(buildPath(API_PATHS.usersMePreferences), query) },
    savePreferences(body) { return put(buildPath(API_PATHS.usersMePreferences), body) },
  },
  workOrders: {
    getWorkOrders(query) { return get(buildPath(API_PATHS.workOrders), query) },
    postWorkOrders(body) { return post(buildPath(API_PATHS.workOrders), body) },
    deleteWorkOrders(id) { return del(buildPath(API_PATHS.workOrdersId, { id })) },
    order(id, query) { return get(buildPath(API_PATHS.workOrdersId, { id }), query) },
    patchWorkOrders(id, body) { return patch(buildPath(API_PATHS.workOrdersId, { id }), body) },
    claim(id, body) { return post(buildPath(API_PATHS.workOrdersIdClaim, { id }), body) },
    updateStatus(id, body) { return patch(buildPath(API_PATHS.workOrdersIdStatus, { id }), body) },
    createFromAlarm(alarmId, body) { return post(buildPath(API_PATHS.workOrdersFromAlarmAlarmId, { alarmId }), body) },
  },
}

openapiClient.auth.login = (username, password, remember = false) =>
  post(buildPath(API_PATHS.authLogin), { username, password, remember })
openapiClient.alarms.getWorkOrderPolicy = () => get(buildPath(API_PATHS.alarmsWorkOrderPolicy))
openapiClient.alarms.updateWorkOrderPolicy = (rules) => put(buildPath(API_PATHS.alarmsWorkOrderPolicy), { rules })

/** 小程序 services 层稳定别名（避免 operationId 变动影响业务代码） */
const services = {
  alarms: {
    list: (...args) => openapiClient.alarms.getAlarms(...args),
    ack: (...args) => openapiClient.alarms.acknowledge(...args),
    ackAll: (...args) => openapiClient.alarms.acknowledgeAll(...args),
  },
  auth: {
    register: (...args) => openapiClient.auth.register(...args),
    logout: (...args) => openapiClient.auth.logout(...args),
    changePassword: (...args) => openapiClient.auth.changePassword(...args),
  },
  detectionTemplates: {
    list: (...args) => openapiClient.detectionTemplates.templates(...args),
    create: (...args) => openapiClient.detectionTemplates.addTemplate(...args),
    remove: (...args) => openapiClient.detectionTemplates.removeTemplate(...args),
  },
  notifications: {
    list: (...args) => openapiClient.notifications.getNotifications(...args),
    markRead: (...args) => openapiClient.notifications.markRead(...args),
    markAllRead: (...args) => openapiClient.notifications.markAllRead(...args),
    remove: (...args) => openapiClient.notifications.remove(...args),
  },
  records: {
    list: (...args) => openapiClient.records.getRecords(...args),
  },
  robots: {
    list: (...args) => openapiClient.robots.listRobots(...args),
    create: (...args) => openapiClient.robots.createRobot(...args),
    remove: (...args) => openapiClient.robots.deleteRobot(...args),
  },
  routes: {
    list: (...args) => openapiClient.routes.getRoutes(...args),
    create: (...args) => openapiClient.routes.createRoute(...args),
    replace: (...args) => openapiClient.routes.replaceRoute(...args),
    remove: (...args) => openapiClient.routes.deleteRoute(...args),
  },
  sites: {
    list: (...args) => openapiClient.sites.getSites(...args),
    listAreas: (...args) => openapiClient.sites.areas(...args),
    create: (...args) => openapiClient.sites.createSite(...args),
    update: (...args) => openapiClient.sites.updateSite(...args),
    remove: (...args) => openapiClient.sites.deleteSite(...args),
    createArea: (...args) => openapiClient.sites.createArea(...args),
    removeArea: (...args) => openapiClient.sites.deleteArea(...args),
    getSlamMap: (...args) => openapiClient.sites.slamMap(...args),
    saveSlamMap: (...args) => openapiClient.sites.saveSlamMap(...args),
    removeSlamMap: (...args) => openapiClient.sites.deleteSlamMap(...args),
    listSlamMaps: (...args) => openapiClient.sites.slamMaps(...args),
  },
  tasks: {
    list: (...args) => openapiClient.tasks.getTasks(...args),
    create: (...args) => openapiClient.tasks.createTask(...args),
    dispatch: (...args) => openapiClient.tasks.dispatch(...args),
    pause: (...args) => openapiClient.tasks.pause(...args),
    resume: (...args) => openapiClient.tasks.resume(...args),
    takeover: (...args) => openapiClient.tasks.takeover(...args),
    cancel: (...args) => openapiClient.tasks.cancel(...args),
    listEvents: (...args) => openapiClient.tasks.events(...args),
  },
  users: {
    list: (...args) => openapiClient.users.getUsers(...args),
    updateMe: (...args) => openapiClient.users.updateMe(...args),
    updateRole: (...args) => openapiClient.users.patchRole(...args),
    toggleEnabled: (...args) => openapiClient.users.patchEnabled(...args),
    getPreferences: (...args) => openapiClient.users.preferences(...args),
    savePreferences: (...args) => openapiClient.users.savePreferences(...args),
    getActivities: (...args) => openapiClient.users.activities(...args),
  },
  workOrders: {
    list: (...args) => openapiClient.workOrders.getWorkOrders(...args),
    patch: (...args) => openapiClient.workOrders.patchWorkOrders(...args),
    createFromAlarm: (...args) => openapiClient.workOrders.createFromAlarm(...args),
    claim: (...args) => openapiClient.workOrders.claim(...args),
    updateStatus: (...args) => openapiClient.workOrders.updateStatus(...args),
  },
}

module.exports = { openapiClient, services, API_PATHS, apiRel }
