const api = require('../../services/index')
const { ROBOT_STATUS_LABELS } = require('../../utils/constants')

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
    videoPlaceholder: '',
    refreshing: false,
  },

  onShow() {
    if (!getApp().requireAuth('/pages/monitor/index')) return
    getApp().refreshBadges().then(() => {
      this.selectComponent('#inlineTabBar')?.refresh?.()
    })
    this.load()
  },

  async load() {
    const [robotsRaw, tasks, routes, sites] = await Promise.all([
      api.getRobots(), api.getTasks(), api.getRoutes(), api.getSites(),
    ])
    const robots = robotsRaw.slice(0, 1)
    const activeTask = tasks.find((t) => ['RUNNING', 'PAUSED', 'DISPATCHED', 'MANUAL_TAKEOVER'].includes(t.status))
    const list = robots.map((r) => ({
      ...r,
      statusType: r.status === 'ONLINE' ? 'success' : r.status === 'BUSY' ? 'warning' : 'info',
      statusLabel: ROBOT_STATUS_LABELS[r.status] || r.status,
      currentTaskName: r.currentTaskId ? (tasks.find((t) => t.id === r.currentTaskId)?.name || '-') : '-',
    }))
    let selected = list.find((r) => r.id === this.data.selectedId) || list[0] || null
    if (selected) {
      const task = tasks.find((t) => t.robotId === selected.id && ['RUNNING', 'PAUSED'].includes(t.status))
      const activeRoute = task ? routes.find((r) => r.id === task.routeId) : null
      const mapCenter = selected.position || (selected.siteId ? sites.find((s) => s.id === selected.siteId)?.center : null) || { lat: 30.2741, lng: 120.1551 }
      this.setData({
        robots: list,
        tasks,
        routes,
        selectedId: selected.id,
        selected,
        activeRoute,
        mapCenter,
        robotPos: selected.position || null,
        videoPlaceholder: `https://picsum.photos/seed/monitor_${selected.id}/640/360`,
      })
    } else {
      this.setData({ robots: list })
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
