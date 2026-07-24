const api = require('../../services/index')
const { resolveRobotPresence } = require('../../utils/robot-status')
const { syncTabBar, refreshTabBarBadges } = require('../../utils/tab-page')
const { hasPermission } = require('../../utils/permission')
const { DEFAULT_CENTER, isValidGeoPoint, normalizeGeoPoint, cloneCenter } = require('../../utils/geo-coord')
const {
  locationLatLng,
  defaultTrackQuery,
  buildLocationSummary,
  isGpsApiCachedUnavailable,
  clearGpsApiUnavailable,
} = require('../../utils/robot-location')
const { getUiPreferences, saveUiPreferences } = require('../../utils/ui-preferences')

const POLL_MS = 5000
const ACTIVE_TASK_STATUSES = ['RUNNING', 'PAUSED', 'DISPATCHED', 'MANUAL_TAKEOVER', 'STARTING']

function resolveDisplayRoute(selected, tasks, routes) {
  const activeTask = tasks.find(
    (t) => t.robotId === selected.id && ACTIVE_TASK_STATUSES.includes(t.status),
  )
  if (activeTask?.routeId) {
    const route = routes.find((r) => r.id === activeTask.routeId)
    if (route) return { route, reference: false }
  }
  if (selected.currentTaskId) {
    const linkedTask = tasks.find((t) => t.id === selected.currentTaskId)
    if (linkedTask?.routeId) {
      const route = routes.find((r) => r.id === linkedTask.routeId)
      if (route) return { route, reference: true }
    }
  }
  if (selected.siteId) {
    const route = routes.find((r) => r.siteId === selected.siteId)
    if (route) return { route, reference: true }
  }
  return { route: null, reference: false }
}

function resolveMapCenter({ robotPos, route, siteCenter }) {
  const fallbackCenter = normalizeGeoPoint(siteCenter, DEFAULT_CENTER)
  if (robotPos) return cloneCenter(robotPos)
  const routeStart = route?.path?.[0]
  if (isValidGeoPoint(routeStart)) return normalizeGeoPoint(routeStart, fallbackCenter)
  return fallbackCenter
}

Page({
  data: {
    robots: [],
    tasks: [],
    routes: [],
    sites: [],
    selectedId: '',
    selected: null,
    activeRoute: null,
    routeReference: false,
    mapCenter: cloneCenter(DEFAULT_CENTER),
    robotPos: null,
    trackPoints: [],
    showTrack: true,
    canViewLocation: false,
    canViewTrack: false,
    gpsApiUnavailable: false,
    locationSummary: { modeLabel: '无 GPS 位置', fixLabel: '-', meta: '' },
    bridgeOnline: false,
    videoHint: '暂无视频流',
    loadError: '',
    refreshing: false,
  },

  onShow() {
    if (!getApp().requireAuth('/pages/monitor/index')) return
    if (typeof wx.hideHomeButton === 'function') wx.hideHomeButton()
    const perms = getApp().globalData.permissions || []
    const canViewLocation = hasPermission(perms, 'robot:location:view')
    const canViewTrack = hasPermission(perms, 'robot:track:view')
    const apiConfig = require('../../config/api')
    const gpsApiUnavailable = isGpsApiCachedUnavailable(apiConfig.baseUrl)
    this._gpsApiUnavailable = gpsApiUnavailable
    this.setData({
      canViewLocation,
      canViewTrack,
      gpsApiUnavailable,
      showTrack: getUiPreferences().showGpsTrack,
    })
    syncTabBar(this)
    refreshTabBarBadges(this)
    this.load({ canViewLocation, canViewTrack, gpsApiUnavailable })
    if (!gpsApiUnavailable) this._startPoll()
  },

  onHide() {
    this._stopPoll()
  },

  onUnload() {
    this._stopPoll()
  },

  _startPoll() {
    this._stopPoll()
    if (this._gpsApiUnavailable || this.data.gpsApiUnavailable) return
    this._pollTimer = setInterval(() => {
      if (this._gpsApiUnavailable || this.data.gpsApiUnavailable) {
        this._stopPoll()
        return
      }
      this.load({ silent: true })
    }, POLL_MS)
  },

  _stopPoll() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer)
      this._pollTimer = null
    }
  },

  async load(options = {}) {
    const silent = options.silent === true
    const canViewLocation = options.canViewLocation ?? this.data.canViewLocation
    const canViewTrack = options.canViewTrack ?? this.data.canViewTrack
    const skipGpsApi = options.gpsApiUnavailable ?? this._gpsApiUnavailable ?? this.data.gpsApiUnavailable
    if (!silent) this.setData({ loadError: '' })
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

      const telemetryPromise = api.getRobotTelemetry(selected.id)
      const gpsPromise = skipGpsApi
        ? Promise.resolve({ robotLocation: null, track: null, gpsApiUnavailable: true })
        : api.fetchRobotGpsData(selected.id, {
          canViewLocation,
          canViewTrack,
          trackQuery: defaultTrackQuery(),
        })

      const [telemetry, gpsData] = await Promise.all([telemetryPromise, gpsPromise])
      const { robotLocation, track, gpsApiUnavailable } = gpsData

      const { route: activeRoute, reference: routeReference } = resolveDisplayRoute(selected, tasks, routes)
      const siteCenter = selected.siteId ? sites.find((s) => s.id === selected.siteId)?.center : null

      const gpsPos = locationLatLng(robotLocation)
      const legacyPos = isValidGeoPoint(selected.position)
        ? normalizeGeoPoint(selected.position, normalizeGeoPoint(siteCenter, DEFAULT_CENTER))
        : null
      const robotPos = gpsPos || legacyPos
      const mapCenter = resolveMapCenter({ robotPos, route: activeRoute, siteCenter })
      const trackPoints = track?.points || []
      const bridgeOnline = telemetry?.bridgeReachable === true && telemetry?.online === true
      const nextGpsUnavailable = skipGpsApi || gpsApiUnavailable
      if (nextGpsUnavailable) {
        this._gpsApiUnavailable = true
        this._stopPoll()
      }

      this.setData({
        robots: list,
        tasks,
        routes,
        selectedId: selected.id,
        selected: { ...selected, telemetry: telemetry || selected.telemetry || null },
        activeRoute,
        routeReference,
        mapCenter,
        robotPos,
        trackPoints,
        gpsApiUnavailable: nextGpsUnavailable,
        locationSummary: buildLocationSummary(robotLocation, {
          gpsApiUnavailable: nextGpsUnavailable,
          legacyPos: !!legacyPos,
          hasRoute: !!activeRoute,
        }),
        bridgeOnline,
        videoHint: bridgeOnline
          ? `Bridge 在线 · ${telemetry?.systemMode || '运行中'}`
          : '机器人离线，暂无视频流',
      })
    } catch (err) {
      if (!silent) {
        this.setData({ loadError: err.message || '加载失败，请检查后端与登录状态' })
      }
    }
  },

  selectRobot(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ selectedId: id })
    this.load()
  },

  toggleTrack() {
    const showTrack = !this.data.showTrack
    this.setData({ showTrack })
    saveUiPreferences({ showGpsTrack: showTrack })
  },

  async onRefresh() {
    this.setData({ refreshing: true })
    const apiConfig = require('../../config/api')
    clearGpsApiUnavailable(apiConfig.baseUrl)
    this._gpsApiUnavailable = false
    this.setData({ gpsApiUnavailable: false })
    await this.load()
    if (!this._gpsApiUnavailable) this._startPoll()
    this.setData({ refreshing: false })
  },
})
