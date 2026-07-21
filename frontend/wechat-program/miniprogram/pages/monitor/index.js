const api = require('../../services/index')
const { resolveRobotPresence } = require('../../utils/robot-status')
const { syncTabBar, refreshTabBarBadges } = require('../../utils/tab-page')

Page({
  data: {
    robots: [],
    tasks: [],
    routes: [],
    sites: [],
    selectedId: '',
    selected: null,
    activeRoute: null,
    mapCenter: { lat: 30.2741, lng: 120.1551 },
    robotPos: null,
    bridgeOnline: false,
    videoHint: '暂无视频流',
    loadError: '',
    refreshing: false,
  },

  onShow() {
    if (!getApp().requireAuth('/pages/monitor/index')) return
    if (typeof wx.hideHomeButton === 'function') wx.hideHomeButton()
    syncTabBar(this)
    refreshTabBarBadges(this)
    this.load()
  },

  async load() {
    this.setData({ loadError: '' })
    try {
      const [robotsRaw, heartbeatItems, tasks, routes, sites] = await Promise.all([
        api.getRobots(),
        api.getRobotHeartbeatStatus(),
        api.getTasks(),
        api.getRoutes(),
        api.getSites(),
      ])
      const heartbeatById = {}
      ;(heartbeatItems || []).forEach((item) => {
        if (item?.robotId) heartbeatById[item.robotId] = item
      })
      const list = (robotsRaw || []).map((robot) => {
        const presence = resolveRobotPresence(robot, heartbeatById[robot.id])
        return {
          ...robot,
          ...presence,
          currentTaskName: robot.currentTaskId
            ? (tasks.find((t) => t.id === robot.currentTaskId)?.name || '-')
            : '-',
        }
      })
      let selected = list.find((r) => r.id === this.data.selectedId) || list[0] || null
      if (!selected) {
        this.setData({ robots: [], selected: null, selectedId: '', loadError: '暂无机器人数据' })
        return
      }
      const telemetry = await api.getRobotTelemetry(selected.id)
      const task = tasks.find((t) => t.robotId === selected.id && ['RUNNING', 'PAUSED', 'DISPATCHED', 'MANUAL_TAKEOVER'].includes(t.status))
      const activeRoute = task ? routes.find((r) => r.id === task.routeId) : null
      const siteCenter = selected.siteId ? sites.find((s) => s.id === selected.siteId)?.center : null
      const mapCenter = selected.position || siteCenter || { lat: 30.2741, lng: 120.1551 }
      const bridgeOnline = telemetry?.bridgeReachable === true && telemetry?.online === true
      this.setData({
        robots: list,
        tasks,
        routes,
        selectedId: selected.id,
        selected: { ...selected, telemetry: telemetry || selected.telemetry || null },
        activeRoute,
        mapCenter,
        robotPos: selected.position || null,
        bridgeOnline,
        videoHint: bridgeOnline
          ? `Bridge 在线 · ${telemetry?.systemMode || '运行中'}`
          : '机器人离线，暂无视频流',
      })
    } catch (err) {
      this.setData({ loadError: err.message || '加载失败，请检查后端与登录状态' })
    }
  },

  selectRobot(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ selectedId: id })
    this.load()
  },

  async onRefresh() {
    this.setData({ refreshing: true })
    await this.load()
    this.setData({ refreshing: false })
  },
})
