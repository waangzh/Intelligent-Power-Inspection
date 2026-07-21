const api = require('../../services/index')
const {
  hasPermission,
  canControlTask,
  canTakeoverTask,
  canShowTaskEstop,
  canShowTaskCancel,
} = require('../../utils/permission')
const { syncTabBar, refreshTabBarBadges } = require('../../utils/tab-page')
const { DEFAULT_CENTER, isValidGeoPoint, normalizeGeoPoint, cloneCenter } = require('../../utils/geo-coord')

Page({
  data: {
    tasks: [],
    routes: [],
    robots: [],
    availableRobots: [],
    activeTask: null,
    activeRoute: null,
    activeRouteName: '',
    activeRobotName: '',
    mapCenter: cloneCenter(DEFAULT_CENTER),
    robotPos: null,
    showCreate: false,
    form: { name: '', routeIndex: 0, robotIndex: 0 },
    canCreate: false,
    canDispatch: false,
    canControl: false,
    canTakeover: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/tasks/index')) return
    if (!app.requirePermission('task:view')) return
    if (typeof wx.hideHomeButton === 'function') wx.hideHomeButton()
    syncTabBar(this)
    refreshTabBarBadges(this)
    const user = app.globalData.user
    const perms = app.globalData.permissions
    this.setData({
      canCreate: hasPermission(perms, 'task:create'),
      canDispatch: hasPermission(perms, 'task:dispatch'),
      canControl: canControlTask(perms),
      canTakeover: canTakeoverTask(perms),
    })
    this.load()
  },

  async load() {
    try {
      const perms = getApp().globalData.permissions
      const [tasks, routes, robots, sites] = await Promise.all([
        api.getTasks(), api.getRoutes(), api.getRobots(), api.getSites(),
      ])
      const decorateTask = (t) => ({
        ...t,
        showEstop: canShowTaskEstop(t, perms),
        showCancel: canShowTaskCancel(t, perms),
      })
      const decoratedTasks = tasks.map(decorateTask)
      const routeLabels = routes.map((r) => {
        const site = sites.find((s) => s.id === r.siteId)
        return { ...r, label: `${site ? site.name : ''} / ${r.name}` }
      })
      const availableRobots = robots
        .filter((r) => r.status !== 'OFFLINE')
        .map((r) => ({ ...r, label: `${r.name} (${r.status})` }))
      const activeTask = decoratedTasks.find((t) =>
        ['DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status))
      let activeRoute = null
      let activeRouteName = ''
      let activeRobotName = ''
      let mapCenter = cloneCenter(DEFAULT_CENTER)
      let robotPos = null
      if (activeTask) {
        activeRoute = routes.find((r) => r.id === activeTask.routeId) || null
        const robot = robots.find((r) => r.id === activeTask.robotId)
        activeRouteName = activeRoute?.name || '-'
        activeRobotName = robot?.name || '-'
        const site = activeRoute ? sites.find((s) => s.id === activeRoute.siteId) : null
        const fallbackCenter = normalizeGeoPoint(site?.center, DEFAULT_CENTER)
        const routeStart = activeRoute?.path?.[0]
        mapCenter = isValidGeoPoint(routeStart) ? normalizeGeoPoint(routeStart, fallbackCenter) : fallbackCenter
        robotPos = isValidGeoPoint(robot?.position) ? normalizeGeoPoint(robot.position, fallbackCenter) : null
      }
      this.setData({
        tasks: decoratedTasks,
        routes: routeLabels,
        robots,
        availableRobots,
        activeTask: activeTask || null,
        activeRoute,
        activeRouteName,
        activeRobotName,
        mapCenter,
        robotPos,
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  toggleCreate() {
    this.setData({ showCreate: !this.data.showCreate, form: { name: '', routeIndex: 0, robotIndex: 0 } })
  },

  onNameInput(e) { this.setData({ 'form.name': e.detail.value }) },
  onRouteChange(e) { this.setData({ 'form.routeIndex': Number(e.detail.value) }) },
  onRobotChange(e) { this.setData({ 'form.robotIndex': Number(e.detail.value) }) },

  async submitCreate() {
    const { name, routeIndex, robotIndex } = this.data.form
    const route = this.data.routes[routeIndex]
    const robot = this.data.availableRobots[robotIndex]
    if (!name.trim()) {
      wx.showToast({ title: '请输入任务名称', icon: 'none' })
      return
    }
    if (!route || !robot) {
      wx.showToast({ title: '请选择路线和机器人', icon: 'none' })
      return
    }
    try {
      await api.createTask(name.trim(), route.id, robot.id)
      wx.showToast({ title: '任务已创建' })
      this.setData({ showCreate: false })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '创建失败', icon: 'none' })
    }
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/tasks/detail/index?id=${e.currentTarget.dataset.id}` })
  },

  async dispatch(e) {
    try {
      await api.dispatchTask(e.currentTarget.dataset.id)
      wx.showToast({ title: '已下发' })
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  async pause(e) {
    try {
      await api.pauseTask(e.currentTarget.dataset.id)
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  async resume(e) {
    try {
      await api.resumeTask(e.currentTarget.dataset.id)
      this.load()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  async takeover(e) {
    wx.showModal({
      title: '人工接管',
      content: '确认接管该任务？机器人将停止自动巡检。',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.takeoverTask(e.currentTarget.dataset.id)
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '操作失败', icon: 'none' })
        }
      },
    })
  },

  async cancel(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '取消任务',
      content: '确认取消该巡检任务？（业务取消，不是设备急停）',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.cancelTask(id)
          wx.showToast({ title: '已取消', icon: 'none' })
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '取消失败', icon: 'none' })
        }
      },
    })
  },

  async emergencyStop(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '远程急停',
      editable: true,
      placeholderText: '请输入急停原因（必填）',
      success: async (res) => {
        if (!res.confirm) return
        const reason = (res.content || '').trim()
        if (!reason) {
          wx.showToast({ title: '必须填写急停原因', icon: 'none' })
          return
        }
        try {
          await api.emergencyStopTask(id, reason)
          wx.showToast({ title: '急停已受理', icon: 'none' })
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '急停失败', icon: 'none' })
        }
      },
    })
  },
})
