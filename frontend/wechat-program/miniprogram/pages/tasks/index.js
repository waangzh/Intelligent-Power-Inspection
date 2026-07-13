const api = require('../../services/index')
const { hasPermission } = require('../../utils/permission')
const { syncTabBar } = require('../../utils/tab-page')

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
    mapCenter: { lat: 30.2741, lng: 120.1551 },
    robotPos: null,
    showCreate: false,
    form: { name: '', routeIndex: 0, robotIndex: 0 },
    canCreate: false,
    canDispatch: false,
    canControl: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/tasks/index')) return
    if (!app.requirePermission('task:view')) return
    syncTabBar(this)
    app.refreshBadges()
    const user = app.globalData.user
    this.setData({
      canCreate: hasPermission(user.role, 'task:create'),
      canDispatch: hasPermission(user.role, 'task:dispatch'),
      canControl: hasPermission(user.role, 'task:control'),
    })
    this.load()
  },

  async load() {
    try {
      const [tasks, routes, robots, sites] = await Promise.all([
        api.getTasks(), api.getRoutes(), api.getRobots(), api.getSites(),
      ])
      const routeLabels = routes.map((r) => {
        const site = sites.find((s) => s.id === r.siteId)
        return { ...r, label: `${site ? site.name : ''} / ${r.name}` }
      })
      const availableRobots = robots
        .filter((r) => r.status !== 'OFFLINE')
        .map((r) => ({ ...r, label: `${r.name} (${r.status})` }))
      const activeTask = tasks.find((t) =>
        ['DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER'].includes(t.status))
      let activeRoute = null
      let activeRouteName = ''
      let activeRobotName = ''
      let mapCenter = { lat: 30.2741, lng: 120.1551 }
      let robotPos = null
      if (activeTask) {
        activeRoute = routes.find((r) => r.id === activeTask.routeId) || null
        const robot = robots.find((r) => r.id === activeTask.robotId)
        activeRouteName = activeRoute?.name || '-'
        activeRobotName = robot?.name || '-'
        mapCenter = activeRoute?.path?.[0] || mapCenter
        robotPos = robot?.position || null
      }
      this.setData({
        tasks,
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
    wx.showModal({
      title: '取消任务',
      content: '确认取消该巡检任务？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.cancelTask(e.currentTarget.dataset.id)
          this.load()
        } catch (err) {
          wx.showToast({ title: err.message || '操作失败', icon: 'none' })
        }
      },
    })
  },
})
