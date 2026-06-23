const api = require('../../services/index')
const { uid } = require('../../utils/storage')
const { ROUTE_DETECTIONS, CHECKPOINT_DETECTIONS } = require('../../utils/constants')

function defaultItems(types) {
  return types.map((type) => ({
    type, enabled: true, threshold: 0.75,
    prompt: type === 'SWITCH' ? '红色刀闸开关' : type === 'OIL_LEAK' ? '设备底部渗油区域' : undefined,
  }))
}

Page({
  data: {
    sites: [],
    routes: [],
    filteredRoutes: [],
    siteIndex: 0,
    selectedRouteId: '',
    currentRoute: null,
    siteCenter: { lat: 30.2741, lng: 120.1551 },
    routeName: '',
    routeDesc: '',
    mapModes: [{ label: '2D 地图', value: '2d' }, { label: '3D 模式（演示）', value: '3d' }],
    mapModeIndex: 0,
    drawingPath: false,
    activeTab: 'route-det',
    selectedCpId: '',
    selectedCp: null,
    cpForm: { name: '', pan: 0, tilt: -15, dwell: 20 },
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
      this.setData({ currentRoute: null })
    }
  },

  filterRoutes() {
    const site = this.data.sites[this.data.siteIndex]
    const filteredRoutes = site ? this.data.routes.filter((r) => r.siteId === site.id) : []
    this.setData({
      filteredRoutes,
      siteCenter: site ? site.center : { lat: 30.2741, lng: 120.1551 },
    })
  },

  selectRoute(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ selectedRouteId: id })
    this.syncCurrent()
  },

  syncCurrent() {
    const route = this.data.routes.find((r) => r.id === this.data.selectedRouteId)
    if (!route) return
    const mapModeIndex = route.mapMode === '3d' ? 1 : 0
    this.setData({
      currentRoute: route,
      routeName: route.name,
      routeDesc: route.description || '',
      mapModeIndex,
      selectedCpId: '',
      selectedCp: null,
    })
  },

  onRouteName(e) { this.setData({ routeName: e.detail.value }) },
  onRouteDesc(e) { this.setData({ routeDesc: e.detail.value }) },

  onMapMode(e) { this.setData({ mapModeIndex: Number(e.detail.value) }) },

  async saveMeta() {
    const route = this.data.currentRoute
    if (!route) return
    await api.saveRoute({
      ...route,
      name: this.data.routeName,
      description: this.data.routeDesc,
      mapMode: this.data.mapModes[this.data.mapModeIndex].value,
    })
    wx.showToast({ title: '已保存' })
    await this.load()
    this.setData({ selectedRouteId: route.id })
    this.syncCurrent()
  },

  async deleteRoute() {
    wx.showModal({
      title: '删除路线',
      content: '确认删除该巡检路线？',
      success: async (res) => {
        if (!res.confirm) return
        await api.removeRoute(this.data.selectedRouteId)
        this.setData({ selectedRouteId: '', currentRoute: null })
        this.load()
      },
    })
  },

  toggleDraw() {
    this.setData({ drawingPath: !this.data.drawingPath })
  },

  async persistRoute(patch) {
    const route = { ...this.data.currentRoute, ...patch }
    await api.saveRoute(route)
    const routes = this.data.routes.map((r) => (r.id === route.id ? route : r))
    this.setData({ routes, currentRoute: route })
  },

  async onMapTap(e) {
    const { lat, lng } = e.detail
    const route = this.data.currentRoute
    if (!route) return
    if (this.data.drawingPath) {
      const path = [...(route.path || []), { lat, lng }]
      await this.persistRoute({ path })
      return
    }
    const seq = route.checkpoints.length + 1
    const cp = {
      id: uid('cp'),
      routeId: route.id,
      name: `检查点 ${seq}`,
      seq,
      position: { lat, lng },
      pan: 0,
      tilt: -15,
      dwellSeconds: 20,
      detections: defaultItems(CHECKPOINT_DETECTIONS),
    }
    await this.persistRoute({ checkpoints: [...route.checkpoints, cp] })
    wx.showToast({ title: '已添加检查点' })
  },

  async clearPath() {
    await this.persistRoute({ path: [] })
  },

  async addDemoPath() {
    const c = this.data.siteCenter
    const d = 0.0003
    const path = [
      { lat: c.lat, lng: c.lng },
      { lat: c.lat + d, lng: c.lng + d * 0.5 },
      { lat: c.lat + d * 0.5, lng: c.lng + d },
      { lat: c.lat - d * 0.3, lng: c.lng + d * 0.8 },
    ]
    await this.persistRoute({ path })
    wx.showToast({ title: '示例路线已添加' })
  },

  setTab(e) { this.setData({ activeTab: e.currentTarget.dataset.t }) },

  onRouteDetectionChange(e) {
    this.persistRoute({ routeDetections: e.detail.items })
  },

  onCpSelect(e) {
    const id = e.detail ? e.detail.id : e.currentTarget.dataset.id
    const cp = this.data.currentRoute.checkpoints.find((c) => c.id === id)
    if (!cp) return
    this.setData({
      selectedCpId: id,
      selectedCp: cp,
      cpForm: { name: cp.name, pan: cp.pan, tilt: cp.tilt, dwell: cp.dwellSeconds },
    })
  },

  onCpName(e) { this.setData({ 'cpForm.name': e.detail.value }) },
  onCpPan(e) { this.setData({ 'cpForm.pan': e.detail.value }) },
  onCpTilt(e) { this.setData({ 'cpForm.tilt': e.detail.value }) },
  onCpDwell(e) { this.setData({ 'cpForm.dwell': e.detail.value }) },

  async saveCp() {
    const { selectedCpId, cpForm, currentRoute } = this.data
    const checkpoints = currentRoute.checkpoints.map((c) =>
      c.id === selectedCpId
        ? { ...c, name: cpForm.name, pan: cpForm.pan, tilt: cpForm.tilt, dwellSeconds: cpForm.dwell }
        : c)
    await this.persistRoute({ checkpoints })
    const cp = checkpoints.find((c) => c.id === selectedCpId)
    this.setData({ selectedCp: cp, currentRoute: { ...currentRoute, checkpoints } })
    wx.showToast({ title: '检查点已保存' })
  },

  onCpDetectionChange(e) {
    const checkpoints = this.data.currentRoute.checkpoints.map((c) =>
      c.id === this.data.selectedCpId ? { ...c, detections: e.detail.items } : c)
    this.persistRoute({ checkpoints })
  },

  async removeCp(e) {
    const id = e.currentTarget.dataset.id
    const checkpoints = this.data.currentRoute.checkpoints.filter((c) => c.id !== id)
    await this.persistRoute({ checkpoints })
    if (this.data.selectedCpId === id) this.setData({ selectedCpId: '', selectedCp: null })
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
