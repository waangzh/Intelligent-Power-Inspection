const api = require('../../../services/index')
const { openPage } = require('../../../utils/tab-page')
const {
  hasPermission,
  canControlTask,
  canTakeoverTask,
  canCancelTask,
  canEstopTask,
  cancelTaskLabel,
} = require('../../../utils/permission')
const {
  canLaunchTask,
  launchButtonLabel,
  resolveTaskStatus,
} = require('../../../utils/task-bridge')
const {
  buildStartModeOptions,
  formatStartBlockMessage,
  showTaskError,
} = require('../../../utils/task-start')
const { formatDateTime } = require('../../../utils/date-time')
const { formatBusinessMessage } = require('../../../utils/display-text')
const { resolvePhotoSrc } = require('../../../utils/work-order-photo')
const { DEFAULT_CENTER, isValidGeoPoint, normalizeGeoPoint, cloneCenter } = require('../../../utils/geo-coord')
const {
  locationLatLng,
  defaultTrackQuery,
  buildLocationSummary,
  isGpsApiCachedUnavailable,
} = require('../../../utils/robot-location')
const { getUiPreferences, saveUiPreferences } = require('../../../utils/ui-preferences')

const EVENT_LABELS = {
  DISPATCH: '下发', ARRIVE: '到点', INSPECT: '采集', DETECT: '检测',
  ALARM: '告警', PAUSE: '暂停', RESUME: '执行', COMPLETE: '完成', VOICE: '语音',
}

const EVENT_TYPES = {
  ALARM: 'danger', COMPLETE: 'success', DISPATCH: 'primary', ARRIVE: 'primary',
  INSPECT: 'primary', DETECT: 'primary', PAUSE: 'warning', RESUME: 'primary', VOICE: 'info',
}

Page({
  data: {
    taskId: '',
    task: null,
    route: null,
    routeName: '-',
    robotName: '-',
    mapCenter: cloneCenter(DEFAULT_CENTER),
    robotPos: null,
    trackPoints: [],
    showTrack: true,
    canViewLocation: false,
    canViewTrack: false,
    gpsApiUnavailable: false,
    locationSummary: { modeLabel: '无 GPS 位置', fixLabel: '-', meta: '' },
    checkpointTotal: 0,
    createdLabel: '-',
    startedLabel: '-',
    events: [],
    taskAlarms: [],
    canLaunch: false,
    canDispatch: false,
    canStartRemote: false,
    canStartLocal: false,
    canControl: false,
    canTakeover: false,
    canCancel: false,
    canEstop: false,
    showLaunch: false,
    launchLabel: '下发',
    cancelLabel: '取消',
    displayStatus: 'CREATED',
    execution: null,
  },

  onLoad(options) {
    this.setData({ taskId: options.id || '' })
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth()) return
    if (!app.requirePermission('task:view')) return
    const user = app.globalData.user
    const perms = app.globalData.permissions
    const canViewLocation = hasPermission(perms, 'robot:location:view')
    const canViewTrack = hasPermission(perms, 'robot:track:view')
    this.setData({
      canLaunch: hasPermission(perms, 'task:dispatch')
        || hasPermission(perms, 'task:start-remote')
        || hasPermission(perms, 'task:start-local'),
      canDispatch: hasPermission(perms, 'task:dispatch'),
      canStartRemote: hasPermission(perms, 'task:start-remote'),
      canStartLocal: hasPermission(perms, 'task:start-local'),
      canControl: canControlTask(perms),
      canTakeover: canTakeoverTask(perms),
      cancelLabel: cancelTaskLabel(perms),
      canViewLocation,
      canViewTrack,
    })
    if (this.data.taskId) {
      this.setData({ showTrack: getUiPreferences().showGpsTrack })
      this.load({ canViewLocation, canViewTrack })
    }
  },

  async load(options = {}) {
    const canViewLocation = options.canViewLocation ?? this.data.canViewLocation
    const canViewTrack = options.canViewTrack ?? this.data.canViewTrack
    const [tasks, routes, robots, alarms, events] = await Promise.all([
      api.getTasks(),
      api.getRoutes(),
      api.getRobots(),
      api.getAlarms(),
      api.getTaskEvents(this.data.taskId),
    ])
    const task = tasks.find((t) => t.id === this.data.taskId)
    const execution = task?.executionId
      ? await api.getTaskExecution(this.data.taskId).catch(() => null)
      : null
    const displayStatus = task ? resolveTaskStatus(task, execution) : 'CREATED'
    const decoratedTask = task
      ? {
        ...task,
        status: displayStatus,
        progress: execution?.progress ?? task.progress,
      }
      : null
    const route = task ? routes.find((r) => r.id === task.routeId) : null
    const robot = task ? robots.find((r) => r.id === task.robotId) : null
    const fmt = (iso) => formatDateTime(iso)
    const timeline = events.map((e) => ({
      ...e,
      imageUrl: resolvePhotoSrc(e.imageUrl),
      typeLabel: EVENT_LABELS[e.type] || e.type,
      time: fmt(e.createdAt),
      evType: EVENT_TYPES[e.type] || 'primary',
      message: formatBusinessMessage(e.message),
    }))
    const fallbackCenter = normalizeGeoPoint(null, DEFAULT_CENTER)
    const routeStart = route?.path?.[0]
    let robotLocation = null
    let track = null
    const apiConfig = require('../../../config/api')
    let gpsApiUnavailable = isGpsApiCachedUnavailable(apiConfig.baseUrl) || this.data.gpsApiUnavailable
    if (task?.robotId && !gpsApiUnavailable) {
      const gpsData = await api.fetchRobotGpsData(task.robotId, {
        canViewLocation,
        canViewTrack,
        trackQuery: defaultTrackQuery({ start: task.startedAt || undefined }),
      })
      robotLocation = gpsData.robotLocation
      track = gpsData.track
      gpsApiUnavailable = gpsData.gpsApiUnavailable
    }
    const gpsPos = locationLatLng(robotLocation)
    const legacyPos = isValidGeoPoint(robot?.position)
      ? normalizeGeoPoint(robot.position, fallbackCenter)
      : null
    const robotPos = gpsPos || legacyPos
    const mapCenter = robotPos
      ? cloneCenter(robotPos)
      : (isValidGeoPoint(routeStart) ? normalizeGeoPoint(routeStart, fallbackCenter) : fallbackCenter)
    const trackPoints = track?.points || []
    const perms = getApp().globalData.permissions
    const taskForPerm = task
      ? { ...task, displayStatus, executionId: task.executionId }
      : null
    this.setData({
      task: decoratedTask,
      execution,
      displayStatus,
      showLaunch: task ? canLaunchTask(task, execution) : false,
      launchLabel: task ? launchButtonLabel(task) : '下发',
      canCancel: taskForPerm ? canCancelTask(taskForPerm, perms) : false,
      canEstop: taskForPerm ? canEstopTask(taskForPerm, perms) : false,
      route,
      routeName: route?.name || '-',
      robotName: robot?.name || '-',
      robotPos,
      trackPoints,
      gpsApiUnavailable,
      locationSummary: buildLocationSummary(robotLocation, {
        gpsApiUnavailable,
        legacyPos: !!legacyPos,
        hasRoute: !!route,
      }),
      checkpointTotal: route?.checkpoints?.length || 0,
      createdLabel: task ? fmt(task.createdAt) : '-',
      startedLabel: task ? fmt(task.startedAt) : '-',
      mapCenter,
      events: timeline,
      taskAlarms: alarms
        .filter((a) => a.taskId === this.data.taskId)
        .map((a) => ({
          ...a,
          imageUrl: resolvePhotoSrc(a.imageUrl),
          message: formatBusinessMessage(a.message),
        })),
    })
    if (task) wx.setNavigationBarTitle({ title: task.name })
  },

  goBack() {
    wx.navigateBack({ fail: () => openPage('/pages/tasks/index') })
  },

  toggleTrack() {
    const showTrack = !this.data.showTrack
    this.setData({ showTrack })
    saveUiPreferences({ showGpsTrack: showTrack })
  },

  async launch() {
    const task = this.data.task
    if (!task) return
    if (task.executionId || task.routeRevisionId) {
      this.openStartOptions()
      return
    }
    try {
      await api.launchTask(this.data.taskId, { task })
      wx.showToast({ title: '已下发', icon: 'success' })
      this.load()
    } catch (err) {
      showTaskError(err.message || '操作失败')
    }
  },

  async openStartOptions() {
    wx.showLoading({ title: '校验启动条件…', mask: true })
    let eligibility = null
    try {
      eligibility = await api.getTaskStartEligibility(this.data.taskId)
    } catch (err) {
      wx.hideLoading()
      showTaskError(err.message || '启动条件校验失败', '无法启动')
      return
    }
    wx.hideLoading()

    const options = buildStartModeOptions(eligibility, {
      canStartRemote: this.data.canStartRemote,
      canStartLocal: this.data.canStartLocal,
    })
    const eligibleOptions = options.filter((item) => item.eligible)
    if (!eligibleOptions.length) {
      wx.showModal({
        title: '暂时无法启动',
        content: formatStartBlockMessage(eligibility, options),
        showCancel: false,
      })
      return
    }

    const itemList = eligibleOptions.map((item) => item.label)
    const modes = eligibleOptions.map((item) => item.mode)
    wx.showActionSheet({
      itemList,
      success: async (res) => {
        const startMode = modes[res.tapIndex]
        if (!startMode) return
        if (startMode === 'REMOTE_IMMEDIATE') {
          wx.showModal({
            title: '确认远程立即启动',
            content: '机器人校验通过后将立即开始移动，请确认现场安全。',
            success: async (modal) => {
              if (!modal.confirm) return
              await this.startTask(startMode, eligibility)
            },
          })
          return
        }
        await this.startTask(startMode, eligibility)
      },
    })
  },

  async startTask(startMode, eligibility) {
    try {
      wx.showLoading({ title: '启动中…', mask: true })
      await api.launchTask(this.data.taskId, { startMode, task: this.data.task, eligibility })
      wx.hideLoading()
      wx.showToast({
        title: startMode === 'LOCAL_CONFIRM' ? '已下发，等待本地确认' : '启动命令已受理',
        icon: 'success',
      })
      this.load()
    } catch (err) {
      wx.hideLoading()
      showTaskError(err.message || '启动失败', '启动失败')
    }
  },

  async pause() { await api.pauseTask(this.data.taskId); this.load() },
  async resume() { await api.resumeTask(this.data.taskId); this.load() },
  async takeover() {
    wx.showModal({
      title: '人工接管',
      content: '确认接管？',
      success: async (r) => { if (r.confirm) { await api.takeoverTask(this.data.taskId); this.load() } },
    })
  },
  async cancel() {
    wx.showModal({
      title: '取消任务',
      content: '确认取消？这是业务取消，不是设备急停。',
      success: async (r) => {
        if (!r.confirm) return
        try {
          await api.cancelTask(this.data.taskId)
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '操作失败', icon: 'none' })
        }
      },
    })
  },

  async emergencyStop() {
    wx.showModal({
      title: '远程急停',
      editable: true,
      placeholderText: '请输入急停原因（必填）',
      success: async (r) => {
        if (!r.confirm) return
        const reason = (r.content || '').trim()
        if (!reason) {
          wx.showToast({ title: '必须填写急停原因', icon: 'none' })
          return
        }
        try {
          await api.emergencyStopTask(this.data.taskId, reason)
          wx.showToast({ title: '急停已受理', icon: 'none' })
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '急停失败', icon: 'none' })
        }
      },
    })
  },
})
