Component({
  properties: {
    center: { type: Object, value: { lat: 30.2741, lng: 120.1551 } },
    route: { type: Object, value: null },
    areas: { type: Array, value: [] },
    robotPosition: { type: Object, value: null },
    editable: { type: Boolean, value: false },
    drawMode: { type: Boolean, value: false },
    height: { type: Number, value: 360 },
  },
  data: {
    latitude: 30.2741,
    longitude: 120.1551,
    scale: 16,
    markers: [],
    polyline: [],
    polygons: [],
  },
  observers: {
    'center, route, areas, robotPosition'() { this.render() },
  },
  lifetimes: {
    attached() { this.render() },
  },
  methods: {
    render() {
      const center = this.properties.center || { lat: 30.2741, lng: 120.1551 }
      const route = this.properties.route
      const areas = this.properties.areas || []
      const robot = this.properties.robotPosition
      const markers = []
      let id = 1

      if (route && route.checkpoints) {
        route.checkpoints.forEach((cp) => {
          markers.push({
            id: id++,
            latitude: cp.position.lat,
            longitude: cp.position.lng,
            title: cp.name,
            label: { content: String(cp.seq), color: '#fff', bgColor: '#ff8a00', padding: 4, borderRadius: 8 },
            width: 24,
            height: 24,
            cpId: cp.id,
          })
        })
      }

      if (robot) {
        markers.push({
          id: id++,
          latitude: robot.lat,
          longitude: robot.lng,
          width: 28,
          height: 28,
          label: { content: '机', color: '#fff', bgColor: '#12b76a', padding: 6, borderRadius: 14 },
        })
      }

      const polyline = []
      if (route && route.path && route.path.length > 1) {
        polyline.push({
          points: route.path.map((p) => ({ latitude: p.lat, longitude: p.lng })),
          color: '#1768f2',
          width: 4,
          arrowLine: true,
        })
      }

      const polygons = areas.map((a, i) => ({
        points: (a.polygon || []).map((p) => ({ latitude: p.lat, longitude: p.lng })),
        strokeWidth: 2,
        strokeColor: '#1768f288',
        fillColor: '#1768f222',
      }))

      this.setData({
        latitude: center.lat,
        longitude: center.lng,
        markers,
        polyline,
        polygons,
      })
    },

    onTap(e) {
      if (!this.properties.editable && !this.properties.drawMode) return
      const { latitude, longitude } = e.detail
      this.triggerEvent('tapmap', { lat: latitude, lng: longitude })
    },

    onMarkerTap(e) {
      const markerId = e.detail.markerId
      const m = this.data.markers.find((x) => x.id === markerId)
      if (m && m.cpId) this.triggerEvent('checkpointselect', { id: m.cpId })
    },
  },
})
