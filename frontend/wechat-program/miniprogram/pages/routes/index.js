const api = require('../../services/index')
const { loadSlamMapForSite, buildSlamMapFromLocal } = require('../../utils/slam-map')
const {
  createEmptyRosRoute,
  loadRosRoute,
  orderedTargets,
  nextTargetId,
  buildRouteJson,
  buildTargetStatus,
  numOr,
} = require('../../utils/ros-route')

const FAILURE_POLICIES = [
  { label: 'abort_and_return_home', value: 'abort_and_return_home' },
  { label: 'abort', value: 'abort' },
]

function cloneRosRoute(rosRoute) {
  return JSON.parse(JSON.stringify(rosRoute))
}

function chooseChatFile(extensions) {
  return new Promise((resolve, reject) => {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: extensions,
      success: (res) => resolve(res.tempFiles[0]),
      fail: (err) => reject(new Error(err.errMsg || '选择文件失败')),
    })
  })
}

function readTextFile(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath,
      encoding: 'utf8',
      success: (res) => resolve(res.data),
      fail: (err) => reject(new Error(err.errMsg || '读取文件失败')),
    })
  })
}

function syncRosForms(rosRoute) {
  const start = rosRoute.start_pose || {}
  const pose = start.pose || { x: 0, y: 0, yaw: 0 }
  const cov = start.covariance || { x: 0.25, y: 0.25, yaw: 0.0685 }
  const routeDef = rosRoute.routes && rosRoute.routes[0] ? rosRoute.routes[0] : {}
  const loop = routeDef.loop || { enabled: false, wait_sec: 600, max_cycles: 0 }
  const policyIndex = Math.max(0, FAILURE_POLICIES.findIndex((p) => p.value === routeDef.failure_policy))
  return {
    startForm: {
      name: start.name || '初始起点',
      x: String(pose.x),
      y: String(pose.y),
      yaw: String(pose.yaw),
      publishInitialPose: start.publish_initial_pose !== false,
      covX: String(cov.x ?? 0.25),
      covY: String(cov.y ?? 0.25),
      covYaw: String(cov.yaw ?? 0.0685),
    },
    routeMetaForm: {
      routeId: routeDef.id || 'route_patrol_001',
      activeRouteId: rosRoute.active_route_id || routeDef.id || 'route_patrol_001',
      jsonRouteName: routeDef.name || '本地巡逻路线',
    },
    routeForm: {
      goalTimeoutSec: String(routeDef.goal_timeout_sec ?? 120),
      maxRetries: String(routeDef.max_retries_per_checkpoint ?? 1),
      loopEnabled: !!loop.enabled,
      loopWaitSec: String(loop.wait_sec ?? 600),
      loopMaxCycles: String(loop.max_cycles ?? 0),
      failurePolicyIndex: policyIndex >= 0 ? policyIndex : 0,
    },
    failurePolicies: FAILURE_POLICIES,
  }
}

function buildMapHud(slamMap, pgmName) {
  if (!slamMap || !slamMap.width) {
    return { info: '等待加载地图', cursorText: 'map: -, -' }
  }
  const wM = (slamMap.width * slamMap.resolution).toFixed(2)
  const hM = (slamMap.height * slamMap.resolution).toFixed(2)
  const name = pgmName || slamMap.imageName || 'map'
  return {
    info: `${name} | ${slamMap.width}x${slamMap.height}px | ${wM}m x ${hM}m | res=${slamMap.resolution}`,
    cursorText: 'map: -, -',
  }
}

function initialYawTarget(rosTargets) {
  if (rosTargets[0]) return { kind: 'target', id: rosTargets[0].id }
  return { kind: 'start', id: '' }
}

Page({
  _slamLoadSeq: 0,
  _importYamlText: null,
  _importPgmPath: null,
  _importPgmName: '',
  _activeRouteIdSynced: true,

  data: {
    sites: [],
    routes: [],
    filteredRoutes: [],
    siteIndex: 0,
    selectedRouteId: '',
    currentRoute: null,
    slamMap: null,
    slamMapLoading: false,
    slamMapError: '',
    slamMapImportHint: '',
    rosRoute: null,
    rosTargets: [],
    editMode: 'start',
    selectedTargetId: '',
    yawTargetKind: 'start',
    yawTargetId: '',
    returnToStart: true,
    startForm: {
      name: '初始起点', x: '0', y: '0', yaw: '0',
      publishInitialPose: true, covX: '0.25', covY: '0.25', covYaw: '0.0685',
    },
    routeMetaForm: {
      routeId: 'route_patrol_001',
      activeRouteId: 'route_patrol_001',
      jsonRouteName: '本地巡逻路线',
    },
    routeForm: {
      goalTimeoutSec: '120', maxRetries: '1', loopEnabled: false,
      loopWaitSec: '600', loopMaxCycles: '0', failurePolicyIndex: 0,
    },
    failurePolicies: FAILURE_POLICIES,
    mapHud: { info: '等待加载地图', cursorText: 'map: -, -' },
    jsonPreview: '',
    targetStatus: '还没有巡检点。当前方向点：起点。',
    targetStatusError: false,
  },

  onShow() {
    const app = getApp()
    if (!app.requireAuth('/pages/routes/index')) return
    if (!app.requirePermission('route:edit')) return
    this.load()
  },

  async load() {
    const [sites, routes] = await Promise.all([api.getSites(), api.getRoutes()])
    this.setData({ sites, routes })
    this.filterRoutes()
    if (!this.data.selectedRouteId && this.data.filteredRoutes.length) {
      this.selectRoute({ currentTarget: { dataset: { id: this.data.filteredRoutes[0].id } } })
    } else if (this.data.selectedRouteId) {
      this.syncCurrent()
    }
  },

  onSiteChange(e) {
    this.setData({ siteIndex: Number(e.detail.value), selectedRouteId: '' })
    this.filterRoutes()
    if (this.data.filteredRoutes.length) {
      this.selectRoute({ currentTarget: { dataset: { id: this.data.filteredRoutes[0].id } } })
    } else {
      this.setData({ currentRoute: null, rosRoute: null, rosTargets: [] })
    }
  },

  filterRoutes() {
    const site = this.data.sites[this.data.siteIndex]
    const filteredRoutes = site ? this.data.routes.filter((r) => r.siteId === site.id) : []
    this.setData({ filteredRoutes })
  },

  selectRoute(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ selectedRouteId: id })
    this.syncCurrent()
  },

  async syncCurrent() {
    const route = this.data.routes.find((r) => r.id === this.data.selectedRouteId)
    if (!route) return
    const rosRoute = route.rosRoute
      ? cloneRosRoute(route.rosRoute)
      : createEmptyRosRoute(route.id, route.name)
    const rosTargets = orderedTargets(rosRoute)
    const returnToStart = !rosRoute.routes[0] || rosRoute.routes[0].return_to_start !== false
    const loadId = ++this._slamLoadSeq
    this._importYamlText = null
    this._importPgmPath = null
    this._importPgmName = ''
    this._activeRouteIdSynced = rosRoute.active_route_id === rosRoute.routes[0].id
    const rosForms = syncRosForms(rosRoute)
    const selectedTargetId = rosTargets[0] ? rosTargets[0].id : ''
    const yaw = initialYawTarget(rosTargets)
    const initialStatus = buildTargetStatus(rosRoute, null, yaw.kind, yaw.id)
    this.setData({
      currentRoute: route,
      slamMap: null,
      slamMapLoading: true,
      slamMapError: '',
      slamMapImportHint: '',
      rosRoute,
      rosTargets,
      editMode: 'start',
      selectedTargetId,
      yawTargetKind: yaw.kind,
      yawTargetId: yaw.id,
      returnToStart,
      mapHud: buildMapHud(null),
      jsonPreview: JSON.stringify(buildRouteJson(rosRoute), null, 2),
      ...rosForms,
      ...initialStatus,
    })
    try {
      const slamMap = await loadSlamMapForSite(route.siteId)
      if (loadId !== this._slamLoadSeq) return
      const status = buildTargetStatus(rosRoute, slamMap, yaw.kind, yaw.id)
      this.setData({
        slamMap,
        slamMapLoading: false,
        slamMapError: '',
        mapHud: buildMapHud(slamMap, slamMap.imageName),
        ...status,
      })
    } catch (err) {
      if (loadId !== this._slamLoadSeq) return
      this.setData({
        slamMap: null,
        slamMapLoading: false,
        slamMapError: err.message || 'SLAM 地图加载失败',
        mapHud: buildMapHud(null),
      })
    }
  },

  updateRosView(rosRoute, extra) {
    const yawTargetKind = extra && extra.yawTargetKind !== undefined
      ? extra.yawTargetKind
      : this.data.yawTargetKind
    const yawTargetId = extra && extra.yawTargetId !== undefined
      ? extra.yawTargetId
      : this.data.yawTargetId
    const slamMap = this.data.slamMap
    const status = buildTargetStatus(rosRoute, slamMap, yawTargetKind, yawTargetId)
    const patch = {
      rosRoute,
      rosTargets: orderedTargets(rosRoute),
      returnToStart: !rosRoute.routes[0] || rosRoute.routes[0].return_to_start !== false,
      jsonPreview: JSON.stringify(buildRouteJson(rosRoute), null, 2),
      ...syncRosForms(rosRoute),
      ...status,
      ...extra,
    }
    this.setData(patch)
  },

  getBuiltRosRoute() {
    return buildRouteJson(this.data.rosRoute)
  },

  applyStartForm() {
    const f = this.data.startForm
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    rosRoute.start_pose.name = f.name
    rosRoute.start_pose.pose.x = numOr(f.x, 0)
    rosRoute.start_pose.pose.y = numOr(f.y, 0)
    rosRoute.start_pose.pose.yaw = numOr(f.yaw, 0)
    rosRoute.start_pose.publish_initial_pose = !!f.publishInitialPose
    rosRoute.start_pose.covariance = {
      x: numOr(f.covX, 0.25),
      y: numOr(f.covY, 0.25),
      yaw: numOr(f.covYaw, 0.0685),
    }
    this.updateRosView(rosRoute)
  },

  onStartField(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`startForm.${field}`]: e.detail.value }, () => this.applyStartForm())
  },

  onStartPublishChange(e) {
    this.setData({ 'startForm.publishInitialPose': !!e.detail.value }, () => this.applyStartForm())
  },

  applyRouteMetaForm() {
    const f = this.data.routeMetaForm
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    const routeDef = rosRoute.routes[0]
    routeDef.id = (f.routeId || '').trim() || 'route_patrol_001'
    routeDef.name = (f.jsonRouteName || '').trim() || '本地巡逻路线'
    rosRoute.active_route_id = (f.activeRouteId || '').trim() || routeDef.id
    this.updateRosView(rosRoute)
  },

  onRouteMetaField(e) {
    const field = e.currentTarget.dataset.field
    const value = e.detail.value
    if (field === 'activeRouteId') this._activeRouteIdSynced = false
    if (field === 'routeId' && this._activeRouteIdSynced) {
      this.setData({
        'routeMetaForm.routeId': value,
        'routeMetaForm.activeRouteId': value,
      }, () => this.applyRouteMetaForm())
      return
    }
    this.setData({ [`routeMetaForm.${field}`]: value }, () => this.applyRouteMetaForm())
  },

  applyRouteForm() {
    const f = this.data.routeForm
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    const routeDef = rosRoute.routes[0]
    routeDef.goal_timeout_sec = Math.max(1, Math.round(numOr(f.goalTimeoutSec, 120)))
    routeDef.max_retries_per_checkpoint = Math.max(0, Math.round(numOr(f.maxRetries, 1)))
    routeDef.loop = {
      enabled: !!f.loopEnabled,
      wait_sec: Math.max(0, Math.round(numOr(f.loopWaitSec, 600))),
      max_cycles: Math.max(0, Math.round(numOr(f.loopMaxCycles, 0))),
    }
    const policy = this.data.failurePolicies[f.failurePolicyIndex] || FAILURE_POLICIES[0]
    routeDef.failure_policy = policy.value
    this.updateRosView(rosRoute)
  },

  onRouteField(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`routeForm.${field}`]: e.detail.value }, () => this.applyRouteForm())
  },

  onRouteLoopChange(e) {
    this.setData({ 'routeForm.loopEnabled': !!e.detail.value }, () => this.applyRouteForm())
  },

  onFailurePolicyChange(e) {
    this.setData({ 'routeForm.failurePolicyIndex': Number(e.detail.value) }, () => this.applyRouteForm())
  },

  onTargetField(e) {
    const { id, field } = e.currentTarget.dataset
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    const target = rosRoute.targets.find((t) => t.id === id)
    if (!target) return
    if (field === 'name') {
      target.name = e.detail.value
    } else if (field === 'task_duration_sec') {
      target.task_duration_sec = numOr(e.detail.value, 5)
    } else if (field === 'x' || field === 'y' || field === 'yaw') {
      target.pose[field] = numOr(e.detail.value, 0)
    }
    this.updateRosView(rosRoute)
  },

  onMapCursor(e) {
    const { x, y } = e.detail
    this.setData({ 'mapHud.cursorText': `map: x=${x}, y=${y}` })
  },

  fitMapView() {
    const map = this.selectComponent('#rosMap')
    if (map) map.fitView()
  },

  zoomMapIn() {
    const map = this.selectComponent('#rosMap')
    if (map) map.zoomIn()
  },

  zoomMapOut() {
    const map = this.selectComponent('#rosMap')
    if (map) map.zoomOut()
  },

  copyRosRoute() {
    wx.setClipboardData({
      data: JSON.stringify(this.getBuiltRosRoute(), null, 2),
      success: () => wx.showToast({ title: '已复制 JSON' }),
    })
  },

  exportRosRoute() {
    const json = this.getBuiltRosRoute()
    const text = JSON.stringify(json, null, 2)
    const fileName = `${json.routes[0].id}.json`
    const filePath = `${wx.env.USER_DATA_PATH}/${fileName}`
    wx.getFileSystemManager().writeFile({
      filePath,
      data: text,
      encoding: 'utf8',
      success: () => {
        if (wx.shareFileMessage) {
          wx.shareFileMessage({
            filePath,
            fileName,
            success: () => wx.showToast({ title: '已发起分享' }),
            fail: () => wx.openDocument({ filePath, showMenu: true }),
          })
        } else {
          wx.openDocument({
            filePath,
            showMenu: true,
            success: () => wx.showToast({ title: '已打开文件' }),
            fail: (err) => wx.showToast({ title: err.errMsg || '导出失败', icon: 'none' }),
          })
        }
      },
      fail: (err) => wx.showToast({ title: err.errMsg || '写入失败', icon: 'none' }),
    })
  },

  setEditMode(e) {
    this.setData({ editMode: e.currentTarget.dataset.mode })
  },

  onSetStart(e) {
    const { x, y } = e.detail
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    rosRoute.start_pose.pose.x = x
    rosRoute.start_pose.pose.y = y
    this.updateRosView(rosRoute, { yawTargetKind: 'start', yawTargetId: '' })
  },

  addTargetAtMap(x, y) {
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    const id = nextTargetId(rosRoute.targets)
    const no = rosRoute.targets.length + 1
    rosRoute.targets.push({
      id,
      name: `巡检点${no}`,
      pose: { x, y, yaw: 0 },
      task_duration_sec: 5,
    })
    rosRoute.routes[0].target_ids.push(id)
    this.updateRosView(rosRoute, {
      selectedTargetId: id,
      yawTargetKind: 'target',
      yawTargetId: id,
    })
  },

  onAddTarget(e) {
    const { x, y } = e.detail
    this.addTargetAtMap(x, y)
  },

  addCenterTarget() {
    const map = this.selectComponent('#rosMap')
    if (!map) {
      wx.showToast({ title: '地图组件未就绪', icon: 'none' })
      return
    }
    const coord = map.getCenterMapCoord()
    if (!coord) {
      wx.showToast({ title: '请先加载 SLAM 地图', icon: 'none' })
      return
    }
    this.addTargetAtMap(coord.x, coord.y)
  },

  clearAllTargets() {
    if (!this.data.rosTargets.length) return
    wx.showModal({
      title: '清空巡检点',
      content: '确定清空全部巡检点？',
      success: (res) => {
        if (!res.confirm) return
        const rosRoute = cloneRosRoute(this.data.rosRoute)
        rosRoute.targets = []
        rosRoute.routes[0].target_ids = []
        this.updateRosView(rosRoute, {
          selectedTargetId: '',
          yawTargetKind: 'start',
          yawTargetId: '',
        })
      },
    })
  },

  onMoveTarget(e) {
    const { id, x, y } = e.detail
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    const target = rosRoute.targets.find((t) => t.id === id)
    if (!target) return
    target.pose.x = x
    target.pose.y = y
    this.updateRosView(rosRoute)
  },

  onSetYaw(e) {
    const { kind, id, yaw } = e.detail
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    if (kind === 'start') {
      rosRoute.start_pose.pose.yaw = yaw
    } else {
      const target = rosRoute.targets.find((t) => t.id === id)
      if (target) target.pose.yaw = yaw
    }
    this.updateRosView(rosRoute)
  },

  onSelectTarget(e) {
    const id = e.detail.id
    const rosRoute = this.data.rosRoute
    const status = buildTargetStatus(rosRoute, this.data.slamMap, 'target', id)
    this.setData({
      selectedTargetId: id,
      yawTargetKind: 'target',
      yawTargetId: id,
      ...status,
    })
  },

  onYawTargetChange(e) {
    const { kind, id } = e.detail
    const rosRoute = this.data.rosRoute
    const status = buildTargetStatus(rosRoute, this.data.slamMap, kind, id || '')
    this.setData({ yawTargetKind: kind, yawTargetId: id || '', ...status })
  },

  onRosTargetSelect(e) {
    const id = e.currentTarget.dataset.id
    const rosRoute = this.data.rosRoute
    const status = buildTargetStatus(rosRoute, this.data.slamMap, 'target', id)
    this.setData({
      selectedTargetId: id,
      yawTargetKind: 'target',
      yawTargetId: id,
      ...status,
    })
  },

  moveRosTarget(e) {
    const id = e.currentTarget.dataset.id
    const delta = Number(e.currentTarget.dataset.delta)
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    const ids = rosRoute.routes[0].target_ids
    const index = ids.indexOf(id)
    const next = index + delta
    if (index < 0 || next < 0 || next >= ids.length) return
    ids.splice(index, 1)
    ids.splice(next, 0, id)
    this.updateRosView(rosRoute)
  },

  orientRosTarget(e) {
    const id = e.currentTarget.dataset.id
    const rosRoute = this.data.rosRoute
    const status = buildTargetStatus(rosRoute, this.data.slamMap, 'target', id)
    this.setData({
      selectedTargetId: id,
      yawTargetKind: 'target',
      yawTargetId: id,
      editMode: 'yaw',
      ...status,
    })
  },

  removeRosTarget(e) {
    const id = e.currentTarget.dataset.id
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    rosRoute.targets = rosRoute.targets.filter((t) => t.id !== id)
    rosRoute.routes[0].target_ids = rosRoute.routes[0].target_ids.filter((tid) => tid !== id)
    const rosTargets = orderedTargets(rosRoute)
    let selectedTargetId = this.data.selectedTargetId
    if (selectedTargetId === id) selectedTargetId = rosTargets[0] ? rosTargets[0].id : ''
    let yawTargetKind = this.data.yawTargetKind
    let yawTargetId = this.data.yawTargetId
    if (yawTargetId === id) {
      if (selectedTargetId) {
        yawTargetKind = 'target'
        yawTargetId = selectedTargetId
      } else {
        yawTargetKind = 'start'
        yawTargetId = ''
      }
    }
    this.updateRosView(rosRoute, { selectedTargetId, yawTargetKind, yawTargetId })
  },

  onReturnToStartChange(e) {
    const rosRoute = cloneRosRoute(this.data.rosRoute)
    rosRoute.routes[0].return_to_start = !!e.detail.value
    this.updateRosView(rosRoute)
  },

  noop() {},

  async _tryApplyImportedSlamMap() {
    const yamlText = this._importYamlText
    const pgmPath = this._importPgmPath
    if (!yamlText || !pgmPath) {
      const parts = []
      if (yamlText) parts.push('YAML')
      if (pgmPath) parts.push('PGM')
      const pending = parts.length
        ? `已选 ${parts.join(' + ')}，待选 ${parts.includes('YAML') ? 'PGM' : 'YAML'}`
        : ''
      this.setData({ slamMapImportHint: pending })
      return
    }
    const loadId = ++this._slamLoadSeq
    this.setData({ slamMapLoading: true, slamMapError: '', slamMapImportHint: '正在解析本地地图…' })
    try {
      const slamMap = await buildSlamMapFromLocal(yamlText, pgmPath, this._importPgmName)
      if (loadId !== this._slamLoadSeq) return
      const status = buildTargetStatus(
        this.data.rosRoute,
        slamMap,
        this.data.yawTargetKind,
        this.data.yawTargetId,
      )
      this.setData({
        slamMap,
        slamMapLoading: false,
        slamMapError: '',
        slamMapImportHint: '',
        mapHud: buildMapHud(slamMap, this._importPgmName),
        ...status,
      })
    } catch (err) {
      if (loadId !== this._slamLoadSeq) return
      this.setData({
        slamMap: null,
        slamMapLoading: false,
        slamMapError: err.message || '本地地图加载失败',
        slamMapImportHint: '',
        mapHud: buildMapHud(null),
      })
    }
  },

  async importSlamYaml() {
    try {
      const file = await chooseChatFile(['yaml', 'yml', 'txt'])
      this._importYamlText = await readTextFile(file.path)
      wx.showToast({ title: `已选 ${file.name}` })
      await this._tryApplyImportedSlamMap()
    } catch (err) {
      if (err.message && !/cancel/i.test(err.message)) {
        wx.showToast({ title: err.message, icon: 'none' })
      }
    }
  },

  async importSlamPgm() {
    try {
      const file = await chooseChatFile(['pgm'])
      this._importPgmPath = file.path
      this._importPgmName = file.name
      wx.showToast({ title: `已选 ${file.name}` })
      await this._tryApplyImportedSlamMap()
    } catch (err) {
      if (err.message && !/cancel/i.test(err.message)) {
        wx.showToast({ title: err.message, icon: 'none' })
      }
    }
  },

  async importRosRoute() {
    try {
      const file = await chooseChatFile(['json'])
      const text = await readTextFile(file.path)
      const json = JSON.parse(text)
      const route = this.data.currentRoute
      const rosRoute = loadRosRoute(json, route.id, route.name)
      const rosTargets = orderedTargets(rosRoute)
      const selectedTargetId = rosTargets[0] ? rosTargets[0].id : ''
      const yaw = initialYawTarget(rosTargets)
      this._activeRouteIdSynced = rosRoute.active_route_id === rosRoute.routes[0].id
      this.updateRosView(rosRoute, {
        selectedTargetId,
        yawTargetKind: yaw.kind,
        yawTargetId: yaw.id,
      })
      wx.showToast({ title: '导入成功' })
    } catch (err) {
      if (err.message && !/cancel/i.test(err.message)) {
        wx.showToast({ title: err.message || 'JSON 无效', icon: 'none' })
      }
    }
  },

  async addRoute() {
    const site = this.data.sites[this.data.siteIndex]
    if (!site) return
    wx.showModal({
      title: '新建路线',
      editable: true,
      placeholderText: '路线名称',
      success: async (res) => {
        if (!res.confirm || !res.content) return
        const route = await api.createRoute(site.id, res.content.trim())
        await this.load()
        this.setData({ selectedRouteId: route.id })
        this.syncCurrent()
      },
    })
  },
})
