const api = require('../../services/index')
const {
  hasPermission,
  canControlTask,
  canTakeoverTask,
  canShowTaskEstop,
  canShowTaskCancel,
} = require('../../utils/permission')
const {
  canLaunchTask,
  isActiveTask,
  launchButtonLabel,
  readyRevisionLabel,
  resolveTaskStatus,
} = require('../../utils/task-bridge')
const {
  buildStartModeOptions,
  formatStartBlockMessage,
  showTaskError,
} = require('../../utils/task-start')
const { syncTabBar, refreshTabBarBadges } = require('../../utils/tab-page')
const { DEFAULT_CENTER, isValidGeoPoint, normalizeGeoPoint, cloneCenter } = require('../../utils/geo-coord')

function formatDefaultTaskName() {
  return `巡检任务 ${new Date().toLocaleDateString('zh-CN')}`
}

function decorateTask(task, execution, perms) {
  const displayStatus = resolveTaskStatus(task, execution)
  const row = {
    ...task,
    execution,
    displayStatus,
    status: displayStatus,
    progress: execution?.progress ?? task.progress,
    showLaunch: canLaunchTask(task, execution),
    launchLabel: launchButtonLabel(task),
    showEstop: canShowTaskEstop({ ...task, displayStatus, executionId: task.executionId }, perms),
    showCancel: canShowTaskCancel({ ...task, displayStatus, executionId: task.executionId }, perms),
  }
  return row
}

Page({
  data: {
    tasks: [],
    routes: [],
    robots: [],
    robotOptions: [],
    activeTask: null,
    activeRoute: null,
    activeRouteName: '',
    activeRobotName: '',
    mapCenter: cloneCenter(DEFAULT_CENTER),
    robotPos: null,
    showCreate: false,
    form: { name: '', routeIndex: 0, robotIndex: 0 },
    readyRevision: null,
    readyRevisionLabel: '',
    revisionLoading: false,
    revisionHint: '',
    createDisabled: false,
    canCreate: false,
    canLaunch: false,
    canDispatch: false,
    canStartRemote: false,
    canStartLocal: false,
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
    const perms = app.globalData.permissions
    this.setData({
      canCreate: hasPermission(perms, 'task:create'),
      canLaunch: hasPermission(perms, 'task:dispatch')
        || hasPermission(perms, 'task:start-remote')
        || hasPermission(perms, 'task:start-local'),
      canDispatch: hasPermission(perms, 'task:dispatch'),
      canStartRemote: hasPermission(perms, 'task:start-remote'),
      canStartLocal: hasPermission(perms, 'task:start-local'),
      canControl: canControlTask(perms),
      canTakeover: canTakeoverTask(perms),
    })
    this.load()
  },

  async load() {
    try {
      const perms = getApp().globalData.permissions
      const [rawTasks, routes, robots, sites] = await Promise.all([
        api.getTasks(), api.getRoutes(), api.getRobots(), api.getSites(),
      ])
      const withExecutions = await api.enrichTasksWithExecutions(rawTasks)
      const decoratedTasks = withExecutions.map((t) => decorateTask(t, t.execution, perms))
      const routeLabels = routes.map((r) => {
        const site = sites.find((s) => s.id === r.siteId)
        return { ...r, label: `${site ? site.name : ''} / ${r.name}` }
      })
      const robotOptions = robots.map((r) => ({ ...r, label: `${r.name} (${r.status})` }))
      const activeTask = decoratedTasks.find((t) => isActiveTask(t, t.execution)) || null
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
        robotOptions,
        activeTask,
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
    const showCreate = !this.data.showCreate
    if (showCreate) {
      this.setData({
        showCreate: true,
        form: {
          name: formatDefaultTaskName(),
          routeIndex: 0,
          robotIndex: 0,
        },
        readyRevision: null,
        readyRevisionLabel: '',
        revisionHint: '',
        createDisabled: false,
      })
      this.loadReadyRevision()
      return
    }
    this.setData({ showCreate: false })
  },

  onNameInput(e) { this.setData({ 'form.name': e.detail.value }) },

  onRouteChange(e) {
    this.setData({ 'form.routeIndex': Number(e.detail.value) })
    this.loadReadyRevision()
  },

  onRobotChange(e) {
    this.setData({ 'form.robotIndex': Number(e.detail.value) })
    this.loadReadyRevision()
  },

  async loadReadyRevision() {
    const route = this.data.routes[this.data.form.routeIndex]
    const robot = this.data.robotOptions[this.data.form.robotIndex]
    if (!route || !robot) {
      this.setData({
        readyRevision: null,
        readyRevisionLabel: '',
        revisionHint: '',
        createDisabled: true,
      })
      return
    }
    this.setData({ revisionLoading: true })
    try {
      const ready = await api.findReadyRevision(route.id, robot.id)
      if (ready) {
        this.setData({
          readyRevision: ready,
          readyRevisionLabel: readyRevisionLabel(ready),
          revisionHint: '',
          createDisabled: false,
        })
        return
      }
      const revisions = await api.listRouteRevisions(route.id).catch(() => [])
      const hint = revisions.length
        ? '该路线没有已发布且待机器人就绪的版本，请先在 Web 发布路线并同步部署'
        : '未检测到路线修订，将按模拟任务创建（本地开发环境）'
      this.setData({
        readyRevision: null,
        readyRevisionLabel: '',
        revisionHint: hint,
        createDisabled: revisions.length > 0,
      })
    } catch (e) {
      this.setData({
        readyRevision: null,
        readyRevisionLabel: '',
        revisionHint: e.message || '可部署版本加载失败',
        createDisabled: true,
      })
    } finally {
      this.setData({ revisionLoading: false })
    }
  },

  async submitCreate() {
    const { name, routeIndex, robotIndex } = this.data.form
    const route = this.data.routes[routeIndex]
    const robot = this.data.robotOptions[robotIndex]
    if (!name.trim()) {
      wx.showToast({ title: '请输入任务名称', icon: 'none' })
      return
    }
    if (!route || !robot) {
      wx.showToast({ title: '请选择路线和机器人', icon: 'none' })
      return
    }
    if (this.data.createDisabled) {
      wx.showToast({ title: this.data.revisionHint || '当前无法创建任务', icon: 'none' })
      return
    }
    try {
      const task = await api.createTask(name.trim(), route.id, robot.id, {
        routeRevisionId: this.data.readyRevision?.id,
      })
      wx.showToast({
        title: task.executionId ? '任务已创建' : '任务已创建',
        icon: 'success',
      })
      this.setData({ showCreate: false })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '创建失败', icon: 'none' })
    }
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/tasks/detail/index?id=${e.currentTarget.dataset.id}` })
  },

  async launch(e) {
    const id = e.currentTarget.dataset.id
    const task = this.data.tasks.find((item) => item.id === id)
    if (!task) return
    if (task.executionId || task.routeRevisionId) {
      this.openStartOptions(id, task)
      return
    }
    try {
      await api.launchTask(id, { task })
      wx.showToast({ title: '已下发', icon: 'success' })
      this.load()
    } catch (err) {
      showTaskError(err.message || '操作失败')
    }
  },

  async openStartOptions(taskId, task) {
    wx.showLoading({ title: '校验启动条件…', mask: true })
    let eligibility = null
    try {
      eligibility = await api.getTaskStartEligibility(taskId)
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
              await this.startTask(taskId, startMode, task, eligibility)
            },
          })
          return
        }
        await this.startTask(taskId, startMode, task, eligibility)
      },
    })
  },

  async startTask(taskId, startMode, task, eligibility) {
    try {
      wx.showLoading({ title: '启动中…', mask: true })
      await api.launchTask(taskId, { startMode, task, eligibility })
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
