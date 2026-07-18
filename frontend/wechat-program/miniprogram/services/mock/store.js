const { loadFromStorage, saveToStorage, uid } = require('../../utils/storage')
/**
 * Mock 演示数据层 — 仅 useMock=true 时使用。
 * 权限与状态机以 utils/permission.js 及后端集成测试为准，勿在此扩展独有业务规则。
 */
const { DETECTION_LABELS } = require('../../utils/constants')
const { permissionsForRole } = require('../../generated/permissions')
const seed = require('./seed-data')

const KEYS = {
  session: 'pi_session',
  users: 'pi_users',
  credentials: 'pi_credentials',
  prefs: 'pi_user_prefs',
  activities: 'pi_user_activities',
  sites: 'pi_sites',
  areas: 'pi_areas',
  routes: 'pi_routes',
  tasks: 'pi_tasks',
  records: 'pi_records',
  events: 'pi_task_events',
  alarms: 'pi_alarms',
  workOrders: 'pi_work_orders',
  robots: 'pi_robots',
  detection: 'pi_detection_templates',
  notifications: 'pi_notifications',
  slamMaps: 'pi_slam_maps',
}

const AVATAR_COLORS = ['#1768f2', '#12b76a', '#ff8a00', '#f04438', '#6f8099', '#626aef']

function getAvatarColor(s) {
  let hash = 0
  for (let i = 0; i < s.length; i++) hash = s.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

function getInitials(name) {
  const t = (name || '').trim()
  if (!t) return '?'
  if (/[\u4e00-\u9fff]/.test(t)) return t.slice(0, 1)
  return t.slice(0, 2).toUpperCase()
}

function generateDefaultAvatar(displayName, seedStr) {
  return { color: getAvatarColor(seedStr), initials: getInitials(displayName) }
}

const SEED_USERS = [
  { user: { id: 'user_admin', username: 'admin', displayName: '系统管理员', role: 'ADMIN', phone: '13800000001', bio: '负责平台运维与权限管理', createdAt: '2026-01-01T00:00:00Z' }, password: 'Admin@123' },
  { user: { id: 'user_dispatcher', username: 'dispatcher', displayName: '张调度', role: 'DISPATCHER', phone: '13800000002', bio: '日常巡检任务调度与告警处置', createdAt: '2026-01-01T00:00:00Z' }, password: 'Disp@123' },
  { user: { id: 'user_viewer', username: 'viewer', displayName: '李观察', role: 'VIEWER', phone: '13800000003', bio: '只读查看监控与巡检记录', createdAt: '2026-01-01T00:00:00Z' }, password: 'View@123' },
]

let simulationTimer = null

function ensureUsers() {
  let users = loadFromStorage(KEYS.users, [])
  let creds = loadFromStorage(KEYS.credentials, {})
  if (!users.length) {
    users = SEED_USERS.map((s) => s.user)
    SEED_USERS.forEach((s) => { creds[s.user.username] = { userId: s.user.id, password: s.password } })
    saveToStorage(KEYS.users, users)
    saveToStorage(KEYS.credentials, creds)
  }
  return users
}

function initList(key, fallback, seededKey) {
  const existing = loadFromStorage(key, [])
  if (seededKey) {
    const seeded = loadFromStorage(seededKey, false)
    if (!seeded && !existing.length) {
      saveToStorage(key, fallback)
      saveToStorage(seededKey, true)
      return fallback
    }
  } else if (!existing.length) {
    saveToStorage(key, fallback)
    return fallback
  }
  return existing.length ? existing : fallback
}

function getState() {
  ensureUsers()
  return {
    users: loadFromStorage(KEYS.users, []),
    sites: initList(KEYS.sites, seed.defaultSites),
    areas: initList(KEYS.areas, seed.defaultAreas),
    routes: initList(KEYS.routes, seed.createDemoRoutes()),
    tasks: loadFromStorage(KEYS.tasks, []),
    records: initList(KEYS.records, seed.seedRecords, 'pi_records_seeded'),
    events: loadFromStorage(KEYS.events, []),
    alarms: initList(KEYS.alarms, seed.seedAlarms, 'pi_alarms_seeded'),
    workOrders: initList(KEYS.workOrders, seed.seedWorkOrders),
    robots: initList(KEYS.robots, seed.defaultRobots),
    detectionTemplates: initList(KEYS.detection, seed.defaultDetectionTemplates),
    notifications: loadFromStorage(KEYS.notifications, []),
  }
}

function save(key, data) {
  saveToStorage(key, data)
}

function logActivity(userId, type, message) {
  const list = loadFromStorage(KEYS.activities, [])
  list.unshift({ id: uid('act'), userId, type, message, createdAt: new Date().toISOString() })
  if (list.length > 200) list.length = 200
  save(KEYS.activities, list)
}

function pushNotification(userId, type, title, content, link) {
  const list = loadFromStorage(KEYS.notifications, [])
  list.unshift({ id: uid('ntf'), userId, type, title, content, link, read: false, createdAt: new Date().toISOString() })
  save(KEYS.notifications, list)
}

function pushToAll(type, title, content, link) {
  pushNotification('*', type, title, content, link)
}

// --- Auth ---
function validateUsername(username) {
  if (!username || username.length < 4 || username.length > 20) return '用户名长度为 4～20 位'
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return '用户名只能包含字母、数字和下划线'
  return null
}

function validatePassword(password) {
  if (!password || password.length < 8) return '密码至少 8 位'
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) return '密码需包含字母和数字'
  return null
}

function login(username, password, remember) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      ensureUsers()
      const creds = loadFromStorage(KEYS.credentials, {})
      const c = creds[username]
      if (!c || c.password !== password) {
        reject(new Error('用户名或密码错误'))
        return
      }
      const user = loadFromStorage(KEYS.users, []).find((u) => u.id === c.userId)
      if (!user) {
        reject(new Error('用户不存在'))
        return
      }
      const session = {
        token: `mock_token_${user.id}_${Date.now()}`,
        user,
        permissions: permissionsForRole(user.role),
        expiresAt: remember ? Date.now() + 7 * 24 * 60 * 60 * 1000 : undefined,
      }
      save(KEYS.session, session)
      logActivity(user.id, 'LOGIN', '登录系统')
      const ntf = loadFromStorage(KEYS.notifications, [])
      if (!ntf.some((n) => n.userId === user.id && n.title === '欢迎使用')) {
        pushNotification(user.id, 'SYSTEM', '欢迎使用', '电力智能巡检平台已就绪，祝您工作顺利！', '/pages/dashboard/index')
      }
      resolve(session)
    }, 300)
  })
}

function register(form) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const uErr = validateUsername(form.username)
      if (uErr) { reject(new Error(uErr)); return }
      const pErr = validatePassword(form.password)
      if (pErr) { reject(new Error(pErr)); return }
      if (form.password !== form.confirmPassword) { reject(new Error('两次输入的密码不一致')); return }
      if (!form.agreed) { reject(new Error('请同意服务条款')); return }
      ensureUsers()
      const users = loadFromStorage(KEYS.users, [])
      if (users.some((u) => u.username === form.username)) { reject(new Error('用户名已存在')); return }
      const user = {
        id: uid('user'),
        username: form.username,
        displayName: form.displayName,
        role: 'VIEWER',
        phone: form.phone,
        createdAt: new Date().toISOString(),
      }
      users.push(user)
      const creds = loadFromStorage(KEYS.credentials, {})
      creds[form.username] = { userId: user.id, password: form.password }
      save(KEYS.users, users)
      save(KEYS.credentials, creds)
      resolve(user)
    }, 300)
  })
}

function getSession() {
  const session = loadFromStorage(KEYS.session, null)
  if (!session) return null
  if (session.expiresAt && Date.now() > session.expiresAt) {
    save(KEYS.session, null)
    return null
  }
  const users = loadFromStorage(KEYS.users, [])
  const fresh = users.find((u) => u.id === session.user.id)
  if (fresh) session.user = fresh
  if (!Array.isArray(session.permissions) || !session.permissions.length) {
    save(KEYS.session, null)
    return null
  }
  return session
}

function logout() {
  save(KEYS.session, null)
}

function updateProfile(userId, form) {
  const users = loadFromStorage(KEYS.users, [])
  const idx = users.findIndex((u) => u.id === userId)
  if (idx < 0) throw new Error('用户不存在')
  users[idx] = { ...users[idx], ...form, updatedAt: new Date().toISOString() }
  save(KEYS.users, users)
  logActivity(userId, 'PROFILE', '更新了个人资料')
  const session = getSession()
  if (session && session.user.id === userId) {
    session.user = users[idx]
    save(KEYS.session, session)
  }
  return users[idx]
}

function changePassword(user, form) {
  const creds = loadFromStorage(KEYS.credentials, {})
  const c = creds[user.username]
  if (!c || c.password !== form.oldPassword) throw new Error('原密码不正确')
  const err = validatePassword(form.newPassword)
  if (err) throw new Error(err)
  if (form.newPassword !== form.confirmPassword) throw new Error('两次输入的新密码不一致')
  creds[user.username].password = form.newPassword
  save(KEYS.credentials, creds)
  logActivity(user.id, 'PASSWORD', '修改了登录密码')
}

function getPreferences(userId) {
  const all = loadFromStorage(KEYS.prefs, {})
  return all[userId] || { notifyAlarm: true, notifyTask: true, notifySystem: true, sidebarCollapsed: false }
}

function savePreferences(userId, prefs) {
  const all = loadFromStorage(KEYS.prefs, {})
  all[userId] = prefs
  save(KEYS.prefs, all)
  logActivity(userId, 'SETTINGS', '更新了偏好设置')
  return prefs
}

function getActivities(userId) {
  return loadFromStorage(KEYS.activities, []).filter((a) => a.userId === userId)
}

// --- Alarms ---
const SEVERITY_MAP = { PERSON: 'MEDIUM', HELMET: 'HIGH', OBSTACLE: 'MEDIUM', FIRE: 'CRITICAL', SWITCH: 'HIGH', METER: 'LOW', OIL_LEAK: 'HIGH', FOREIGN_OBJECT: 'MEDIUM' }
const ROUTE_ALARM_TYPES = ['PERSON', 'HELMET', 'FIRE', 'OBSTACLE']

function addAlarm(partial) {
  const alarms = loadFromStorage(KEYS.alarms, [])
  const alarm = { ...partial, id: uid('alarm'), acknowledged: false, createdAt: new Date().toISOString() }
  alarms.unshift(alarm)
  save(KEYS.alarms, alarms)
  pushToAll('ALARM', '新告警', alarm.message, '/pages/alarms/index')
  return alarm
}

function maybeGenerateRouteAlarm(task, routeName) {
  const type = ROUTE_ALARM_TYPES[Math.floor(Math.random() * ROUTE_ALARM_TYPES.length)]
  addAlarm({ taskId: task.id, routeName, type, severity: SEVERITY_MAP[type], message: `路线行进中检测到${DETECTION_LABELS[type]}异常`, imageUrl: `https://picsum.photos/seed/${uid('a')}/400/240` })
}

function maybeGenerateCheckpointAlarm(task, routeName, cp) {
  const enabled = (cp.detections || []).filter((d) => d.enabled)
  if (!enabled.length || Math.random() > 0.35) return
  const det = enabled[Math.floor(Math.random() * enabled.length)]
  addAlarm({ taskId: task.id, routeName, checkpointName: cp.name, type: det.type, severity: SEVERITY_MAP[det.type], message: `检查点「${cp.name}」${DETECTION_LABELS[det.type]}异常`, imageUrl: `https://picsum.photos/seed/${cp.id}/400/240` })
}

// --- Tasks ---
function stopSimulation() {
  if (simulationTimer) {
    clearInterval(simulationTimer)
    simulationTimer = null
  }
}

function addEvent(taskId, type, message, extra) {
  const events = loadFromStorage(KEYS.events, [])
  events.unshift({ id: uid('evt'), taskId, type, message, ...extra, createdAt: new Date().toISOString() })
  save(KEYS.events, events)
}

function updateRobot(id, patch) {
  const robots = loadFromStorage(KEYS.robots, [])
  const idx = robots.findIndex((r) => r.id === id)
  if (idx >= 0) {
    robots[idx] = { ...robots[idx], ...patch }
    save(KEYS.robots, robots)
  }
}

function setRobotPosition(id, position) {
  updateRobot(id, { position })
}

function setTaskStatus(id, status) {
  const tasks = loadFromStorage(KEYS.tasks, [])
  const task = tasks.find((t) => t.id === id)
  if (!task) return
  const now = new Date().toISOString()
  const idx = tasks.findIndex((t) => t.id === id)

  if (status === 'DISPATCHED') {
    tasks[idx] = { ...task, status, startedAt: task.startedAt || now }
    updateRobot(task.robotId, { status: 'BUSY', currentTaskId: id })
    addEvent(id, 'DISPATCH', '任务已下发至机器人')
  } else if (status === 'RUNNING') {
    tasks[idx] = { ...task, status, startedAt: task.startedAt || now }
    updateRobot(task.robotId, { status: 'BUSY', currentTaskId: id })
    addEvent(id, 'RESUME', '任务开始执行，路线级检测已启动')
    startSimulation(id)
  } else if (status === 'PAUSED') {
    tasks[idx] = { ...task, status }
    stopSimulation()
    addEvent(id, 'PAUSE', '任务已暂停')
  } else if (status === 'MANUAL_TAKEOVER') {
    tasks[idx] = { ...task, status }
    stopSimulation()
    addEvent(id, 'PAUSE', '调度员已人工接管机器人')
  } else if (status === 'CANCELLED' || status === 'COMPLETED' || status === 'ESTOPPED') {
    stopSimulation()
    tasks[idx] = { ...task, status, completedAt: now, progress: status === 'COMPLETED' ? 100 : task.progress }
    updateRobot(task.robotId, { status: 'ONLINE', currentTaskId: undefined })
    if (status === 'COMPLETED') {
      addEvent(id, 'COMPLETE', '巡检任务已全部完成')
      finishRecord(id)
    } else if (status === 'ESTOPPED') {
      addEvent(id, 'ESTOP', '远程急停已执行')
    }
  } else {
    tasks[idx] = { ...task, status }
  }
  save(KEYS.tasks, tasks)
}

function finishRecord(taskId) {
  const tasks = loadFromStorage(KEYS.tasks, [])
  const task = tasks.find((t) => t.id === taskId)
  if (!task) return
  const routes = loadFromStorage(KEYS.routes, [])
  const robots = loadFromStorage(KEYS.robots, [])
  const sites = loadFromStorage(KEYS.sites, [])
  const alarms = loadFromStorage(KEYS.alarms, [])
  const route = routes.find((r) => r.id === task.routeId)
  const robot = robots.find((r) => r.id === task.robotId)
  const site = route ? sites.find((s) => s.id === route.siteId) : null
  const taskAlarms = alarms.filter((a) => a.taskId === taskId)
  const start = task.startedAt ? new Date(task.startedAt).getTime() : Date.now()
  const end = task.completedAt ? new Date(task.completedAt).getTime() : Date.now()
  const mins = Math.max(1, Math.round((end - start) / 60000))
  const records = loadFromStorage(KEYS.records, [])
  records.unshift({
    id: uid('record'),
    taskId,
    taskName: task.name,
    routeName: route ? route.name : '-',
    robotName: robot ? robot.name : '-',
    alarmCount: taskAlarms.length,
    checkpointCount: route ? route.checkpoints.length : 0,
    duration: `${mins} 分钟`,
    summary: `完成 ${site ? site.name : '未知站点'} 巡检，共 ${route ? route.checkpoints.length : 0} 个检查点，触发 ${taskAlarms.length} 条告警`,
    completedAt: task.completedAt || new Date().toISOString(),
  })
  save(KEYS.records, records)
}

function startSimulation(taskId) {
  stopSimulation()
  simulationTimer = setInterval(() => {
    const tasks = loadFromStorage(KEYS.tasks, [])
    const task = tasks.find((t) => t.id === taskId)
    if (!task || task.status !== 'RUNNING') { stopSimulation(); return }
    const routes = loadFromStorage(KEYS.routes, [])
    const route = routes.find((r) => r.id === task.routeId)
    if (!route || !route.path.length) return

    const nextProgress = Math.min(100, task.progress + 4)
    const pathIndex = Math.min(route.path.length - 1, Math.floor((nextProgress / 100) * (route.path.length - 1)))
    setRobotPosition(task.robotId, route.path[pathIndex])

    const checkpointTotal = route.checkpoints.length
    const newSeq = Math.min(checkpointTotal, Math.ceil((nextProgress / 100) * checkpointTotal))

    if (newSeq > task.currentCheckpointSeq && newSeq <= checkpointTotal) {
      const cp = route.checkpoints[newSeq - 1]
      addEvent(taskId, 'VOICE', '已到达指定位置，开始检查', { checkpointName: cp.name })
      addEvent(taskId, 'ARRIVE', `到达检查点「${cp.name}」`, { checkpointName: cp.name })
      addEvent(taskId, 'INSPECT', `云台调整 P${cp.pan}° T${cp.tilt}°，采集图像中`, { checkpointName: cp.name, imageUrl: `https://picsum.photos/seed/${cp.id}/400/240` })
      addEvent(taskId, 'DETECT', '调用 LocateAnything 执行检查点级检测', { checkpointName: cp.name, imageUrl: `https://picsum.photos/seed/${cp.id}_det/400/240` })
      maybeGenerateCheckpointAlarm(task, route.name, cp)
    }

    if (nextProgress >= 100) {
      const idx = tasks.findIndex((t) => t.id === taskId)
      tasks[idx] = { ...task, progress: 100, currentCheckpointSeq: checkpointTotal }
      save(KEYS.tasks, tasks)
      setTaskStatus(taskId, 'COMPLETED')
      return
    }

    if (Math.random() < 0.12) maybeGenerateRouteAlarm(task, route.name)

    const idx = tasks.findIndex((t) => t.id === taskId)
    tasks[idx] = { ...task, progress: nextProgress, currentCheckpointSeq: newSeq }
    save(KEYS.tasks, tasks)
  }, 1500)
}

function dispatchTask(id) {
  setTaskStatus(id, 'DISPATCHED')
  setTimeout(() => setTaskStatus(id, 'RUNNING'), 800)
}

module.exports = {
  KEYS,
  getState,
  save,
  getSession,
  login,
  register,
  logout,
  validateUsername,
  validatePassword,
  updateProfile,
  changePassword,
  getPreferences,
  savePreferences,
  getActivities,
  logActivity,
  pushNotification,
  pushToAll,
  generateDefaultAvatar,
  ensureUsers,
  addAlarm,
  setTaskStatus,
  dispatchTask,
  stopSimulation,
  addEvent,
  updateRobot,
  setRobotPosition,
  finishRecord,
  startSimulation,
  maybeGenerateRouteAlarm,
  maybeGenerateCheckpointAlarm,
}
