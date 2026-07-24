const { DEFAULT_CENTER, normalizeGeoPoint, toMapPoint } = require('../../utils/geo-coord')
const { trackPointLatLng } = require('../../utils/robot-location')

Component({
  properties: {
    center: { type: Object, value: { lat: 30.2741, lng: 120.1551 } },
    route: { type: Object, value: null },
    areas: { type: Array, value: [] },
    robotPosition: { type: Object, value: null },
    trackPoints: { type: Array, value: [] },
    showTrack: { type: Boolean, value: true },
    editable: { type: Boolean, value: false },
    drawMode: { type: Boolean, value: false },
    height: { type: Number, value: 360 },
  },
  data: {
    latitude: DEFAULT_CENTER.lat,
    longitude: DEFAULT_CENTER.lng,
    scale: 16,
    markers: [],
    polyline: [],
    polygons: [],
  },
  observers: {
    'center, route, areas, robotPosition, trackPoints, showTrack'() { this.render() },
  },
  lifetimes: {
    attached() { this.render() },
  },
  methods: {
    render() {
      const center = normalizeGeoPoint(this.properties.center)
      const route = this.properties.route
      const areas = this.properties.areas || []
      const robot = this.properties.robotPosition
      const markers = []
      let id = 1

      if (route && route.checkpoints) {
        route.checkpoints.forEach((cp) => {
          const pos = toMapPoint(cp.position)
          if (!pos) return
          markers.push({
            id: id++,
            latitude: pos.latitude,
            longitude: pos.longitude,
            title: cp.name,
            label: { content: String(cp.seq), color: '#fff', bgColor: '#ff8a00', padding: 4, borderRadius: 8 },
            width: 24,
            height: 24,
            cpId: cp.id,
          })
        })
      }

      const robotPoint = toMapPoint(robot)
      if (robotPoint) {
        markers.push({
          id: id++,
          latitude: robotPoint.latitude,
          longitude: robotPoint.longitude,
          width: 28,
          height: 28,
          label: { content: '机', color: '#fff', bgColor: '#12b76a', padding: 6, borderRadius: 14 },
        })
      }

      const polyline = []
      if (route && route.path && route.path.length > 1) {
        const points = route.path.map((p) => toMapPoint(p)).filter(Boolean)
        if (points.length > 1) {
          polyline.push({
            points,
            color: '#1768f2',
            width: 4,
            arrowLine: true,
          })
        }
      }

      const trackPoints = this.properties.showTrack ? (this.properties.trackPoints || []) : []
      if (trackPoints.length > 1) {
        const points = trackPoints.map((p) => {
          const latlng = trackPointLatLng(p)
          return latlng ? { latitude: latlng.lat, longitude: latlng.lng } : null
        }).filter(Boolean)
        if (points.length > 1) {
          polyline.push({
            points,
            color: '#16a34a',
            width: 3,
            arrowLine: false,
          })
          const start = points[0]
          const end = points[points.length - 1]
          markers.push({
            id: id++,
            latitude: start.latitude,
            longitude: start.longitude,
            width: 20,
            height: 20,
            label: { content: '起', color: '#fff', bgColor: '#16a34a', padding: 4, borderRadius: 8 },
          })
          if (end.latitude !== start.latitude || end.longitude !== start.longitude) {
            markers.push({
              id: id++,
              latitude: end.latitude,
              longitude: end.longitude,
              width: 20,
              height: 20,
              label: { content: '终', color: '#fff', bgColor: '#15803d', padding: 4, borderRadius: 8 },
            })
          }
        }
      }

      const polygons = areas.map((a) => {
        const points = (a.polygon || []).map((p) => toMapPoint(p)).filter(Boolean)
        if (points.length < 3) return null
        return {
          points,
          strokeWidth: 2,
          strokeColor: '#1768f288',
          fillColor: '#1768f222',
        }
      }).filter(Boolean)

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
