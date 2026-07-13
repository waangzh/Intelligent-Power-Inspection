const apiConfig = require('../config/api')
const http = require('../utils/request')
const mock = require('./mock/store')
const { uid, loadFromStorage } = require('../utils/storage')
const { ROUTE_DETECTIONS, CHECKPOINT_DETECTIONS } = require('../utils/constants')
const { createEmptyRosRoute } = require('../utils/ros-route')

function useMock() {
  return apiConfig.useMock
}

function delay(ms = 200) {
  return new Promise((r) => setTimeout(r, ms))
}

// ==================== Auth ====================
async function login(username, password, remember = true) {
  if (useMock()) return mock.login(username, password, remember)
  const data = await http.post('/auth/login', { username, password, remember })
  wx.setStorageSync('pi_session', data)
  return data
}

async function register(form) {
  if (useMock()) return mock.register(form)
  return http.post('/auth/register', form)
}

function getSession() {
  if (useMock()) return mock.getSession()
  return wx.getStorageSync('pi_session') || null
}

function logout() {
  if (!useMock()) {
    http.post('/auth/logout').catch(() => {})
  }
  mock.logout()
}

async function updateProfile(form) {
  const session = getSession()
  if (!session) throw new Error('未登录')
  if (useMock()) return mock.updateProfile(session.user.id, form)
  const user = await http.patch('/users/me', form)
  wx.setStorageSync('pi_session', { ...session, user })
  return user
}

async function changePassword(form) {
  const session = getSession()
  if (!session) throw new Error('未登录')
  if (useMock()) {
    mock.changePassword(session.user, form)
    return
  }
  await http.put('/auth/password', form)
}

async function listUsers() {
  if (useMock()) {
    mock.ensureUsers()
    return mock.getState().users
  }
  return http.get('/users')
}

async function updateUserRole(userId, role) {
  if (useMock()) {
    const users = mock.getState().users
    const idx = users.findIndex((u) => u.id === userId)
    if (idx < 0) throw new Error('用户不存在')
    users[idx].role = role
    mock.save(mock.KEYS.users, users)
    return users[idx]
  }
  return http.patch(`/users/${userId}/role`, { role })
}

async function getPreferences() {
  const session = getSession()
  if (!session) return null
  if (useMock()) return mock.getPreferences(session.user.id)
  return http.get('/users/me/preferences')
}

async function savePreferences(prefs) {
  const session = getSession()
  if (!session) throw new Error('未登录')
  if (useMock()) return mock.savePreferences(session.user.id, prefs)
  return http.put('/users/me/preferences', prefs)
}

async function getActivities() {
  const session = getSession()
  if (!session) return []
  if (useMock()) return mock.getActivities(session.user.id)
  return http.get('/users/me/activities')
}

// ==================== Sites ====================
async function getSites() {
  if (useMock()) return mock.getState().sites
  return http.get('/sites')
}

async function getAreas() {
  if (useMock()) return mock.getState().areas
  return http.get('/sites/areas')
}

async function saveSite(site) {
  if (useMock()) {
    const sites = mock.getState().sites
    if (site.id) {
      const idx = sites.findIndex((s) => s.id === site.id)
      if (idx >= 0) sites[idx] = { ...sites[idx], ...site }
    } else {
      site.id = uid('site')
      site.createdAt = new Date().toISOString()
      sites.push(site)
    }
    mock.save(mock.KEYS.sites, sites)
    return site
  }
  return site.id ? http.put(`/sites/${site.id}`, site) : http.post('/sites', site)
}

async function removeSite(id) {
  if (useMock()) {
    let sites = mock.getState().sites.filter((s) => s.id !== id)
    let areas = mock.getState().areas.filter((a) => a.siteId !== id)
    mock.save(mock.KEYS.sites, sites)
    mock.save(mock.KEYS.areas, areas)
    return
  }
  await http.del(`/sites/${id}`)
}

async function addArea(area) {
  if (useMock()) {
    const areas = mock.getState().areas
    area.id = uid('area')
    areas.push(area)
    mock.save(mock.KEYS.areas, areas)
    return area
  }
  return http.post(`/sites/${area.siteId}/areas`, area)
}

async function removeArea(id) {
  if (useMock()) {
    const areas = mock.getState().areas.filter((a) => a.id !== id)
    mock.save(mock.KEYS.areas, areas)
    return
  }
  await http.del(`/sites/areas/${id}`)
}

function getPersistedSlamMap(siteId) {
  const maps = loadFromStorage(mock.KEYS.slamMaps, {})
  const stored = maps[siteId]
  if (!stored) return null
  return {
    yamlText: stored.yamlText,
    pngBase64: stored.pngBase64,
    source: 'cloud',
    updatedAt: stored.updatedAt,
  }
}

/** 站点 SLAM 地图资源（yaml + png/pgm，与标注工具 / 机器人共用） */
async function getSiteSlamMap(siteId) {
  if (useMock()) {
    const persisted = getPersistedSlamMap(siteId)
    if (persisted) return persisted
    const seed = require('./mock/seed-data')
    const entry = seed.defaultSlamMapRegistry[siteId]
    if (entry) return entry
    throw new Error('该站点暂无 SLAM 地图')
  }
  return http.get(`/sites/${siteId}/slam-map`)
}

async function saveSiteSlamMap(siteId, payload) {
  const { yamlText, pngBase64 } = payload
  if (!yamlText || !pngBase64) throw new Error('yaml 与 png 数据不能为空')
  if (useMock()) {
    const maps = loadFromStorage(mock.KEYS.slamMaps, {})
    maps[siteId] = {
      yamlText,
      pngBase64,
      updatedAt: new Date().toISOString(),
    }
    mock.save(mock.KEYS.slamMaps, maps)
    const { clearSlamMapCache } = require('../utils/slam-map')
    clearSlamMapCache(siteId)
    return maps[siteId]
  }
  return http.put(`/sites/${siteId}/slam-map`, { yamlText, pngBase64 })
}

async function removeSiteSlamMap(siteId) {
  if (useMock()) {
    const maps = loadFromStorage(mock.KEYS.slamMaps, {})
    delete maps[siteId]
    mock.save(mock.KEYS.slamMaps, maps)
    const { clearSlamMapCache } = require('../utils/slam-map')
    clearSlamMapCache(siteId)
    return
  }
  await http.del(`/sites/${siteId}/slam-map`)
}

async function listSiteSlamMaps() {
  if (useMock()) {
    const seed = require('./mock/seed-data')
    const persisted = loadFromStorage(mock.KEYS.slamMaps, {})
    const siteIds = new Set([
      ...Object.keys(seed.defaultSlamMapRegistry),
      ...Object.keys(persisted),
    ])
    return [...siteIds].map((siteId) => ({
      siteId,
      source: persisted[siteId] ? 'cloud' : 'bundled',
      updatedAt: persisted[siteId] ? persisted[siteId].updatedAt : null,
    }))
  }
  return http.get('/sites/slam-maps')
}

// ==================== Routes ====================
function defaultDetectionItems(types) {
  return types.map((type) => ({
    type, enabled: true, threshold: 0.75,
    prompt: type === 'SWITCH' ? '红色刀闸开关' : type === 'OIL_LEAK' ? '设备底部渗油区域' : undefined,
  }))
}

async function getRoutes() {
  if (useMock()) return mock.getState().routes
  return http.get('/routes')
}

async function saveRoute(route) {
  if (useMock()) {
    const routes = mock.getState().routes
    const idx = routes.findIndex((r) => r.id === route.id)
    if (idx >= 0) routes[idx] = { ...routes[idx], ...route }
    else routes.push(route)
    mock.save(mock.KEYS.routes, routes)
    return route
  }
  return route.id ? http.put(`/routes/${route.id}`, route) : http.post('/routes', route)
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
  if (useMock()) {
    const routes = mock.getState().routes
    routes.push(route)
    mock.save(mock.KEYS.routes, routes)
    return route
  }
  return http.post('/routes', route)
}

async function removeRoute(id) {
  if (useMock()) {
    const routes = mock.getState().routes.filter((r) => r.id !== id)
    mock.save(mock.KEYS.routes, routes)
    return
  }
  await http.del(`/routes/${id}`)
}

// ==================== Tasks ====================
async function getTasks() {
  if (useMock()) return mock.getState().tasks
  return http.get('/tasks')
}

async function getRecords() {
  if (useMock()) return mock.getState().records
  return http.get('/records')
}

async function getTaskEvents(taskId) {
  if (useMock()) {
    return mock.getState().events.filter((e) => e.taskId === taskId)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  }
  return http.get(`/tasks/${taskId}/events`)
}

async function createTask(name, routeId, robotId) {
  if (useMock()) {
    const tasks = mock.getState().tasks
    const task = { id: uid('task'), name, routeId, robotId, status: 'CREATED', progress: 0, currentCheckpointSeq: 0, createdAt: new Date().toISOString() }
    tasks.unshift(task)
    mock.save(mock.KEYS.tasks, tasks)
    return task
  }
  return http.post('/tasks', { name, routeId, robotId })
}

async function dispatchTask(id) {
  if (useMock()) { mock.dispatchTask(id); return }
  await http.post(`/tasks/${id}/dispatch`)
}

async function pauseTask(id) {
  if (useMock()) { mock.setTaskStatus(id, 'PAUSED'); return }
  await http.post(`/tasks/${id}/pause`)
}

async function resumeTask(id) {
  if (useMock()) { mock.setTaskStatus(id, 'RUNNING'); return }
  await http.post(`/tasks/${id}/resume`)
}

async function takeoverTask(id) {
  if (useMock()) {
    const tasks = mock.getState().tasks
    const task = tasks.find((t) => t.id === id)
    if (task && task.status === 'RUNNING') mock.setTaskStatus(id, 'MANUAL_TAKEOVER')
    return
  }
  await http.post(`/tasks/${id}/takeover`)
}

async function cancelTask(id) {
  if (useMock()) { mock.setTaskStatus(id, 'CANCELLED'); return }
  await http.post(`/tasks/${id}/cancel`)
}

// ==================== Alarms ====================
async function getAlarms() {
  if (useMock()) return mock.getState().alarms
  return http.get('/alarms')
}

async function acknowledgeAlarm(id) {
  if (useMock()) {
    const alarms = mock.getState().alarms
    const idx = alarms.findIndex((a) => a.id === id)
    if (idx >= 0) { alarms[idx].acknowledged = true; mock.save(mock.KEYS.alarms, alarms) }
    return
  }
  await http.post(`/alarms/${id}/ack`)
}

async function acknowledgeAllAlarms() {
  if (useMock()) {
    const alarms = mock.getState().alarms.map((a) => ({ ...a, acknowledged: true }))
    mock.save(mock.KEYS.alarms, alarms)
    return
  }
  await http.post('/alarms/ack-all')
}

// ==================== Work Orders ====================
async function getWorkOrders() {
  if (useMock()) return mock.getState().workOrders
  return http.get('/work-orders')
}

async function createWorkOrderFromAlarm(alarm, creator) {
  if (useMock()) {
    const orders = mock.getState().workOrders
    if (orders.some((o) => o.alarmId === alarm.id)) throw new Error('该告警已有关联工单')
    const priority = alarm.severity === 'CRITICAL' ? 'URGENT' : alarm.severity === 'HIGH' ? 'HIGH' : 'MEDIUM'
    const now = new Date().toISOString()
    const order = {
      id: uid('wo'),
      title: `告警处置：${alarm.message.slice(0, 24)}`,
      description: alarm.message,
      alarmId: alarm.id,
      status: 'PENDING',
      priority,
      createdById: creator.id,
      createdByName: creator.name,
      createdAt: now,
      updatedAt: now,
    }
    orders.unshift(order)
    mock.save(mock.KEYS.workOrders, orders)
    const dispatchers = mock.getState().users.filter((u) => u.role === 'DISPATCHER' && u.enabled !== false)
    dispatchers.forEach((d) => {
      mock.pushNotification(d.id, 'WORKORDER', '新工单待接单', order.title, '/pages/workorders/index')
    })
    return order
  }
  return http.post(`/work-orders/from-alarm/${alarm.id}`, {})
}

async function claimWorkOrder(id) {
  if (useMock()) {
    const orders = mock.getState().workOrders
    const idx = orders.findIndex((o) => o.id === id)
    if (idx < 0) throw new Error('工单不存在')
    const order = orders[idx]
    if (order.status !== 'PENDING') throw new Error('仅待处理工单可接单')
    if (order.assigneeId || (order.assigneeName && order.assigneeName !== order.createdByName)) {
      throw new Error('工单已被其他调度员接单')
    }
    const app = getApp()
    const me = app.globalData.user
    orders[idx] = {
      ...order,
      assigneeId: me.id,
      assigneeName: me.displayName,
      status: 'PROCESSING',
      claimedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    mock.save(mock.KEYS.workOrders, orders)
    return orders[idx]
  }
  return http.post(`/work-orders/${id}/claim`)
}

async function updateWorkOrderStatus(id, status, extra = {}) {
  if (useMock()) {
    const orders = mock.getState().workOrders
    const idx = orders.findIndex((o) => o.id === id)
    if (idx < 0) return
    orders[idx] = { ...orders[idx], status, updatedAt: new Date().toISOString(), ...extra }
    if (status === 'CLOSED') orders[idx].closedAt = orders[idx].updatedAt
    mock.save(mock.KEYS.workOrders, orders)
    return orders[idx]
  }
  return http.patch(`/work-orders/${id}/status`, { status, ...extra })
}

// ==================== Robots ====================
async function getRobots() {
  if (useMock()) return mock.getState().robots
  return http.get('/robots')
}

async function addRobot(robot) {
  if (useMock()) {
    const robots = mock.getState().robots
    robot.id = uid('robot')
    robot.status = robot.status || 'ONLINE'
    robot.battery = robot.battery ?? 100
    robot.lastOnlineAt = new Date().toISOString()
    robots.push(robot)
    mock.save(mock.KEYS.robots, robots)
    return robot
  }
  return http.post('/robots', robot)
}

async function removeRobot(id) {
  if (useMock()) {
    const robots = mock.getState().robots.filter((r) => r.id !== id)
    mock.save(mock.KEYS.robots, robots)
    return
  }
  await http.del(`/robots/${id}`)
}

// ==================== Detection ====================
async function getDetectionTemplates() {
  if (useMock()) return mock.getState().detectionTemplates
  return http.get('/detection-templates')
}

async function addDetectionTemplate(tpl) {
  if (useMock()) {
    const list = mock.getState().detectionTemplates
    tpl.id = uid('tpl')
    tpl.createdAt = new Date().toISOString()
    list.push(tpl)
    mock.save(mock.KEYS.detection, list)
    return tpl
  }
  return http.post('/detection-templates', tpl)
}

async function removeDetectionTemplate(id) {
  if (useMock()) {
    const list = mock.getState().detectionTemplates.filter((t) => t.id !== id)
    mock.save(mock.KEYS.detection, list)
    return
  }
  await http.del(`/detection-templates/${id}`)
}

// ==================== Notifications ====================
async function getNotifications(userId) {
  if (useMock()) {
    const list = mock.getState().notifications
    return list.filter((n) => n.userId === userId || n.userId === '*')
  }
  return http.get('/notifications')
}

async function markNotificationRead(id) {
  if (useMock()) {
    const list = mock.getState().notifications
    const idx = list.findIndex((n) => n.id === id)
    if (idx >= 0) { list[idx].read = true; mock.save(mock.KEYS.notifications, list) }
    return
  }
  await http.patch(`/notifications/${id}/read`)
}

async function markAllNotificationsRead(userId) {
  if (useMock()) {
    const list = mock.getState().notifications.map((n) =>
      (n.userId === userId || n.userId === '*') ? { ...n, read: true } : n)
    mock.save(mock.KEYS.notifications, list)
    return
  }
  await http.post('/notifications/read-all')
}

async function removeNotification(id) {
  if (useMock()) {
    const list = mock.getState().notifications.filter((n) => n.id !== id)
    mock.save(mock.KEYS.notifications, list)
    return
  }
  await http.del(`/notifications/${id}`)
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
  useMock,
  login,
  register,
  getSession,
  logout,
  updateProfile,
  changePassword,
  listUsers,
  updateUserRole,
  getPreferences,
  savePreferences,
  getActivities,
  validateUsername: mock.validateUsername,
  validatePassword: mock.validatePassword,
  generateDefaultAvatar: mock.generateDefaultAvatar,
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
  getWorkOrders,
  createWorkOrderFromAlarm,
  claimWorkOrder,
  updateWorkOrderStatus,
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
