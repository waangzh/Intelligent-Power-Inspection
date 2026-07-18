const api = require('../../../services/index')
const {
  hasPermission,
  canControlTask,
  canTakeoverTask,
  canCancelTask,
  canEstopTask,
} = require('../../../utils/permission')

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
    mapCenter: { lat: 30.2741, lng: 120.1551 },
    robotPos: null,
    checkpointTotal: 0,
    createdLabel: '-',
    startedLabel: '-',
    events: [],
    taskAlarms: [],
    canDispatch: false,
    canControl: false,
    canTakeover: false,
    canCancel: false,
    canEstop: false,
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
    this.setData({
      canDispatch: hasPermission(perms, 'task:dispatch'),
      canControl: canControlTask(perms),
      canTakeover: canTakeoverTask(perms),
      canCancel: canCancelTask(perms),
      canEstop: canEstopTask(perms),
    })
    if (this.data.taskId) this.load()
  },

  async load() {
    const [tasks, routes, robots, alarms, events] = await Promise.all([
      api.getTasks(),
      api.getRoutes(),
      api.getRobots(),
      api.getAlarms(),
      api.getTaskEvents(this.data.taskId),
    ])
    const task = tasks.find((t) => t.id === this.data.taskId)
    const route = task ? routes.find((r) => r.id === task.routeId) : null
    const robot = task ? robots.find((r) => r.id === task.robotId) : null
    const fmt = (iso) => (iso ? iso.slice(0, 19).replace('T', ' ') : '-')
    const timeline = events.map((e) => ({
      ...e,
      typeLabel: EVENT_LABELS[e.type] || e.type,
      time: fmt(e.createdAt),
      evType: EVENT_TYPES[e.type] || 'primary',
    }))
    const mapCenter = route?.path?.[0] || { lat: 30.2741, lng: 120.1551 }
    this.setData({
      task,
      route,
      routeName: route?.name || '-',
      robotName: robot?.name || '-',
      robotPos: robot?.position || null,
      checkpointTotal: route?.checkpoints?.length || 0,
      createdLabel: task ? fmt(task.createdAt) : '-',
      startedLabel: task ? fmt(task.startedAt) : '-',
      mapCenter,
      events: timeline,
      taskAlarms: alarms.filter((a) => a.taskId === this.data.taskId),
    })
    if (task) wx.setNavigationBarTitle({ title: task.name })
  },

  goBack() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/tasks/index' }) })
  },

  async dispatch() { await api.dispatchTask(this.data.taskId); this.load() },
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
