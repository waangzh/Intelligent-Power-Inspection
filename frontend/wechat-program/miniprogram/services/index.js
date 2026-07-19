const { openapiClient, services } = require('../generated/api-client')
const {
  uid,
  validateUsername,
  validatePassword,
  generateDefaultAvatar,
} = require('../utils/storage')
const { ROUTE_DETECTIONS } = require('../utils/constants')
const { createEmptyRosRoute } = require('../utils/ros-route')
const { resolutionSummary, buildReviewFromResolveForm } = require('../utils/work-order')
const workOrderPerm = require('../utils/work-order-permission')

function currentUser() {
  const session = getSession()
  if (!session?.user) throw new Error('未登录')
  return session.user
}

function sessionPermissions() {
  return getSession()?.permissions || []
}

async function getWorkOrderById(id) {
  const orders = await getWorkOrders()
  const order = orders.find((o) => o.id === id)
  if (!order) throw new Error('工单不存在')
  return order
}

/**
 * 后端列表接口统一返回分页对象 { items, total, page, size, hasMore }，
 * 小程序界面按“一次性拿到全量列表”设计，未做分页 UI，
 * 因此这里循环翻页直到 hasMore=false 再拼接返回，
 * 避免真机对接后端时出现 TypeError: xxx.filter/map/slice is not a function，
 * 也避免数据量超过单页上限（后端 DomainStoreService 强制 size<=200）时被静默截断。
 */
async function fetchAllPages(listFn, extraParams) {
  const pageSize = 200
  let page = 1
  let items = []
  for (let guard = 0; guard < 50; guard += 1) {
    const res = await listFn({ ...extraParams, page, size: pageSize })
    if (Array.isArray(res)) {
      items = items.concat(res)
      break
    }
    const pageItems = Array.isArray(res?.items) ? res.items : []
    items = items.concat(pageItems)
    if (!res?.hasMore || pageItems.length < pageSize) break
    page += 1
  }
  return items
}

function delay(ms = 200) {
  return new Promise((r) => setTimeout(r, ms))
}

// ==================== Auth ====================
async function login(username, password, remember = true) {
  const data = await openapiClient.auth.login(username, password, remember)
  wx.setStorageSync('pi_session', data)
  return data
}

async function register(form) {
  return services.auth.register(form)
}

async function sendRegisterSms(phone) {
  return openapiClient.auth.sendSms({ phone: String(phone || '').trim(), purpose: 'REGISTER' })
}

function normalizeSession(session) {
  if (!session?.user) return null
  if (!Array.isArray(session.permissions) || !session.permissions.length) return null
  return session
}

function getSession() {
  return normalizeSession(wx.getStorageSync('pi_session') || null)
}

async function refreshMe() {
  const session = getSession()
  if (!session?.token) return null
  const data = await openapiClient.auth.me()
  const next = {
    ...session,
    user: data.user,
    permissions: data.permissions || [],
    scopes: data.scopes,
    features: data.features,
  }
  if (!next.permissions.length) return null
  wx.setStorageSync('pi_session', next)
  return next
}

function logout() {
  services.auth.logout().catch(() => {})
  wx.removeStorageSync('pi_session')
}

async function updateProfile(form) {
  const session = getSession()
  if (!session) throw new Error('未登录')
  const user = await services.users.updateMe(form)
  wx.setStorageSync('pi_session', { ...session, user })
  return user
}

async function changePassword(form) {
  const session = getSession()
  if (!session) throw new Error('未登录')
  await services.auth.changePassword(form)
}

async function listUsers() {
  return services.users.list()
}

async function uploadWorkOrderPhoto(filePath) {
  return filePath
}

async function updateUserRole(userId, role) {
  return services.users.updateRole(userId, { role })
}

async function toggleUserEnabled(userId, enabled) {
  return services.users.toggleEnabled(userId, { enabled })
}

async function getPreferences() {
  const session = getSession()
  if (!session) return null
  return services.users.getPreferences()
}

async function savePreferences(prefs) {
  const session = getSession()
  if (!session) throw new Error('未登录')
  return services.users.savePreferences(prefs)
}

async function getActivities() {
  const session = getSession()
  if (!session) return []
  return services.users.getActivities()
}

// ==================== Sites ====================
async function getSites() {
  return fetchAllPages(services.sites.list)
}

async function getAreas() {
  return fetchAllPages(services.sites.listAreas)
}

async function saveSite(site) {
  return site.id ? services.sites.update(site.id, site) : services.sites.create(site)
}

async function removeSite(id) {
  await services.sites.remove(id)
}

async function addArea(area) {
  return services.sites.createArea(area.siteId, area)
}

async function removeArea(id) {
  await services.sites.removeArea(id)
}

/** 站点 SLAM 地图资源（yaml + png/pgm，与标注工具 / 机器人共用） */
async function getSiteSlamMap(siteId) {
  return services.sites.getSlamMap(siteId)
}

async function saveSiteSlamMap(siteId, payload) {
  const { yamlText, pngBase64 } = payload
  if (!yamlText || !pngBase64) throw new Error('yaml 与 png 数据不能为空')
  const saved = await services.sites.saveSlamMap(siteId, { yamlText, pngBase64 })
  const { clearSlamMapCache } = require('../utils/slam-map')
  clearSlamMapCache(siteId)
  return saved
}

async function removeSiteSlamMap(siteId) {
  await services.sites.removeSlamMap(siteId)
  const { clearSlamMapCache } = require('../utils/slam-map')
  clearSlamMapCache(siteId)
}

async function listSiteSlamMaps() {
  return services.sites.listSlamMaps()
}

// ==================== Routes ====================
function defaultDetectionItems(types) {
  return types.map((type) => ({
    type, enabled: true, threshold: 0.75,
    prompt: type === 'SWITCH' ? '红色刀闸开关' : type === 'OIL_LEAK' ? '设备底部渗油区域' : undefined,
  }))
}

async function getRoutes() {
  return fetchAllPages(services.routes.list)
}

async function saveRoute(route) {
  return route.id ? services.routes.replace(route.id, route) : services.routes.create(route)
}

async function createRoute(siteId, name, description = '') {
  const id = uid('route')
  const route = {
    id,
    siteId,
    name,
    description,
    path: [],
    routeDetections: defaultDetectionItems(ROUTE_DETECTIONS),
    checkpoints: [],
    mapMode: 'ros2d',
    rosRoute: createEmptyRosRoute(id, name),
    createdAt: new Date().toISOString(),
  }
  return services.routes.create(route)
}

async function removeRoute(id) {
  await services.routes.remove(id)
}

// ==================== Tasks ====================
async function getTasks() {
  return fetchAllPages(services.tasks.list)
}

async function getRecords() {
  return fetchAllPages(services.records.list)
}

async function getTaskEvents(taskId) {
  return fetchAllPages((query) => services.tasks.listEvents(taskId, query))
}

async function createTask(name, routeId, robotId) {
  return services.tasks.create({ name, routeId, robotId })
}

async function dispatchTask(id) {
  await services.tasks.dispatch(id)
}

async function pauseTask(id) {
  await services.tasks.pause(id)
}

async function resumeTask(id) {
  await services.tasks.resume(id)
}

async function takeoverTask(id) {
  await services.tasks.takeover(id)
}

async function cancelTask(id) {
  await services.tasks.cancel(id)
}

// ==================== Alarms ====================
async function getAlarms() {
  return fetchAllPages(services.alarms.list)
}

async function acknowledgeAlarm(id) {
  await services.alarms.ack(id)
}

async function acknowledgeAllAlarms() {
  await services.alarms.ackAll()
}

// ==================== Alarm Work Order Policy ====================
async function getAlarmWorkOrderPolicy() {
  return openapiClient.alarms.getWorkOrderPolicy()
}

async function updateAlarmWorkOrderPolicy(rules) {
  return openapiClient.alarms.updateWorkOrderPolicy(rules)
}

// ==================== Work Orders ====================
async function getWorkOrders() {
  if (!sessionPermissions().includes('workorder:view')) {
    return []
  }
  return fetchAllPages(services.workOrders.list)
}

async function createWorkOrderFromAlarm(alarm, creator, options = {}) {
  const sessionUser = currentUser()
  if (creator?.id && creator.id !== sessionUser.id) throw new Error('用户身份不一致')
  workOrderPerm.assertCanCreateWorkOrder(sessionUser, sessionPermissions())
  return services.workOrders.createFromAlarm(alarm.id, {
    autoConverted: !!options.autoConverted,
  })
}

async function claimWorkOrder(id) {
  const user = currentUser()
  const order = await getWorkOrderById(id)
  workOrderPerm.assertCanClaimOrder(order, user, sessionPermissions())
  return services.workOrders.claim(id)
}

async function updateWorkOrderStatus(id, status, extra = {}) {
  const user = currentUser()
  const order = await getWorkOrderById(id)
  workOrderPerm.assertStatusTransition(order, status, user, sessionPermissions())
  return services.workOrders.updateStatus(id, { status, ...extra })
}

async function submitWorkOrderResolution(id, form) {
  const user = currentUser()
  const submitter = user.displayName || user.name || '调度员'
  const fullForm = {
    ...form,
    submittedAt: new Date().toISOString(),
    submittedBy: submitter,
  }
  return updateWorkOrderStatus(id, 'REVIEW', {
    resolution: resolutionSummary(fullForm),
    resolutionForm: fullForm,
    review: buildReviewFromResolveForm(fullForm),
  })
}

async function submitWorkOrderReview(id, form) {
  const user = currentUser()
  const reviewer = user.displayName || user.name || '管理员'
  const fullForm = {
    ...form,
    reviewedAt: new Date().toISOString(),
    reviewedBy: reviewer,
  }
  const status = form.result === 'PASS' ? 'CLOSED' : 'PROCESSING'
  return updateWorkOrderStatus(id, status, { reviewForm: fullForm })
}

// ==================== Robots ====================
async function getRobots() {
  return fetchAllPages(services.robots.list)
}

async function addRobot(robot) {
  return services.robots.create(robot)
}

async function removeRobot(id) {
  await services.robots.remove(id)
}

// ==================== Detection ====================
async function getDetectionTemplates() {
  return fetchAllPages(services.detectionTemplates.list)
}

async function addDetectionTemplate(tpl) {
  return services.detectionTemplates.create(tpl)
}

async function removeDetectionTemplate(id) {
  await services.detectionTemplates.remove(id)
}

// ==================== Notifications ====================
async function getNotifications(userId) {
  return fetchAllPages(services.notifications.list)
}

async function markNotificationRead(id) {
  await services.notifications.markRead(id)
}

async function markAllNotificationsRead(userId) {
  await services.notifications.markAllRead()
}

async function removeNotification(id) {
  await services.notifications.remove(id)
}

// ==================== Aggregates ====================
async function fetchDashboard() {
  await delay(100)
  const [sites, routes, tasks, alarms, robots, records] = await Promise.all([
    getSites(), getRoutes(), getTasks(), getAlarms(), getRobots(), getRecords(),
  ])
  const activeTasks = tasks.filter((t) => ['DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status))
  const unack = alarms.filter((a) => !a.acknowledged).length
  return { sites, routes, tasks, alarms, robots, records, activeTasks, unack }
}

module.exports = {
  login,
  register,
  sendRegisterSms,
  getSession,
  refreshMe,
  logout,
  updateProfile,
  changePassword,
  listUsers,
  updateUserRole,
  toggleUserEnabled,
  getPreferences,
  savePreferences,
  getActivities,
  validateUsername,
  validatePassword,
  generateDefaultAvatar,
  getSites,
  getAreas,
  saveSite,
  removeSite,
  addArea,
  removeArea,
  getSiteSlamMap,
  saveSiteSlamMap,
  removeSiteSlamMap,
  listSiteSlamMaps,
  getRoutes,
  saveRoute,
  createRoute,
  removeRoute,
  getTasks,
  getRecords,
  getTaskEvents,
  createTask,
  dispatchTask,
  pauseTask,
  resumeTask,
  takeoverTask,
  cancelTask,
  getAlarms,
  acknowledgeAlarm,
  acknowledgeAllAlarms,
  getAlarmWorkOrderPolicy,
  updateAlarmWorkOrderPolicy,
  getWorkOrders,
  createWorkOrderFromAlarm,
  claimWorkOrder,
  uploadWorkOrderPhoto,
  updateWorkOrderStatus,
  submitWorkOrderResolution,
  submitWorkOrderReview,
  getRobots,
  addRobot,
  removeRobot,
  getDetectionTemplates,
  addDetectionTemplate,
  removeDetectionTemplate,
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  removeNotification,
  fetchDashboard,
}
