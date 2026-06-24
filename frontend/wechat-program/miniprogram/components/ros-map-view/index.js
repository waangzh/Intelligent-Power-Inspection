const { mapToPixel, pixelToMap, isInside } = require('../../utils/ros-map')
const { orderedTargets, computeYawFromPixel } = require('../../utils/ros-route')

Component({
  properties: {
    mapMeta: { type: Object, value: null },
    mapImage: { type: String, value: '' },
    rosRoute: { type: Object, value: null },
    editable: { type: Boolean, value: false },
    mode: { type: String, value: 'pan' },
    selectedTargetId: { type: String, value: '' },
    yawTargetKind: { type: String, value: 'start' },
    yawTargetId: { type: String, value: '' },
    height: { type: Number, value: 480 },
  },

  data: {},

  observers: {
    mapMeta() {
      if (!this.ready) return
      this.fitToScreen()
      this.scheduleDraw()
    },
    mapImage() {
      if (!this.ready) return
      this.loadMapImage()
    },
    'rosRoute, mode, selectedTargetId, yawTargetKind, yawTargetId'() {
      this.scheduleDraw()
    },
  },

  lifetimes: {
    attached() {
      this.view = { scale: 1, offsetX: 0, offsetY: 0 }
      this.drag = null
      this.yawPreview = null
      this.mapBitmap = null
      this.ready = false
      wx.nextTick(() => this.initCanvas())
    },
    detached() {
      if (this._drawTimer) clearTimeout(this._drawTimer)
    },
  },

  methods: {
    scheduleDraw() {
      if (!this.ready) return
      if (this._drawTimer) clearTimeout(this._drawTimer)
      this._drawTimer = setTimeout(() => this.draw(), 16)
    },

    initCanvas() {
      const query = this.createSelectorQuery()
      query.select('#rosCanvas').fields({ node: true, size: true })
      query.exec((res) => {
        if (!res || !res[0] || !res[0].node) return
        const canvas = res[0].node
        const ctx = canvas.getContext('2d')
        const sys = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
        const dpr = sys.pixelRatio || 1
        this.canvas = canvas
        this.ctx = ctx
        this.displayWidth = res[0].width
        this.displayHeight = res[0].height
        canvas.width = Math.max(1, Math.floor(res[0].width * dpr))
        canvas.height = Math.max(1, Math.floor(res[0].height * dpr))
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
        this.ready = true
        this.loadMapImage()
      })
    },

    loadMapImage() {
      const src = this.properties.mapImage
      if (!src || !this.canvas) {
        this.fitToScreen()
        this.draw()
        return
      }
      const img = this.canvas.createImage()
      img.onload = () => {
        this.mapBitmap = img
        this.fitToScreen()
        this.draw()
      }
      img.onerror = () => {
        this.mapBitmap = null
        this.fitToScreen()
        this.draw()
      }
      img.src = src
    },

    fitToScreen() {
      const meta = this.properties.mapMeta
      if (!meta || !meta.width || !meta.height || !this.displayWidth) return
      const scaleX = this.displayWidth / meta.width
      const scaleY = this.displayHeight / meta.height
      this.view.scale = Math.max(0.05, Math.min(scaleX, scaleY) * 0.96)
      this.view.offsetX = (this.displayWidth - meta.width * this.view.scale) / 2
      this.view.offsetY = (this.displayHeight - meta.height * this.view.scale) / 2
    },

    screenToPixel(clientX, clientY) {
      const rect = this._rect
      const x = clientX - rect.left
      const y = clientY - rect.top
      return {
        x: (x - this.view.offsetX) / this.view.scale,
        y: (y - this.view.offsetY) / this.view.scale,
      }
    },

    updateRect(callback) {
      const query = this.createSelectorQuery()
      query.select('#rosCanvas').boundingClientRect()
      query.exec((res) => {
        if (res && res[0]) this._rect = res[0]
        if (callback) callback()
      })
    },

    getStartPose() {
      const rosRoute = this.properties.rosRoute
      return rosRoute && rosRoute.start_pose && rosRoute.start_pose.pose
        ? rosRoute.start_pose.pose
        : { x: 0, y: 0, yaw: 0 }
    },

    getReturnToStart() {
      const rosRoute = this.properties.rosRoute
      const routeDef = rosRoute && rosRoute.routes && rosRoute.routes[0]
      return !routeDef || routeDef.return_to_start !== false
    },

    getPointHit(px, py) {
      const threshold = 12 / this.view.scale
      const meta = this.properties.mapMeta
      if (!meta) return null
      const start = this.getStartPose()
      const startPx = mapToPixel(start.x, start.y, meta)
      if (Math.hypot(px - startPx.x, py - startPx.y) <= threshold) {
        return { kind: 'start' }
      }
      const targets = orderedTargets(this.properties.rosRoute)
      for (let i = 0; i < targets.length; i += 1) {
        const target = targets[i]
        const p = mapToPixel(target.pose.x, target.pose.y, meta)
        if (Math.hypot(px - p.x, py - p.y) <= threshold) {
          return { kind: 'target', id: target.id, index: i }
        }
      }
      return null
    },

    drawArrow(ctx, x, y, yaw, color) {
      const len = 24 / this.view.scale
      const angle = -yaw
      ctx.save()
      ctx.translate(x, y)
      ctx.rotate(angle)
      ctx.strokeStyle = color
      ctx.fillStyle = color
      ctx.lineWidth = 2 / this.view.scale
      ctx.beginPath()
      ctx.moveTo(0, 0)
      ctx.lineTo(len, 0)
      ctx.stroke()
      ctx.beginPath()
      ctx.moveTo(len, 0)
      ctx.lineTo(len - 7 / this.view.scale, -5 / this.view.scale)
      ctx.lineTo(len - 7 / this.view.scale, 5 / this.view.scale)
      ctx.closePath()
      ctx.fill()
      ctx.restore()
    },

    draw() {
      const ctx = this.ctx
      const meta = this.properties.mapMeta
      if (!ctx || !this.displayWidth) return

      ctx.clearRect(0, 0, this.displayWidth, this.displayHeight)
      ctx.save()
      ctx.translate(this.view.offsetX, this.view.offsetY)
      ctx.scale(this.view.scale, this.view.scale)

      if (this.mapBitmap && meta) {
        ctx.imageSmoothingEnabled = false
        ctx.drawImage(this.mapBitmap, 0, 0, meta.width, meta.height)
      } else if (meta) {
        ctx.fillStyle = '#f8fafc'
        ctx.fillRect(0, 0, meta.width, meta.height)
      }

      const rosRoute = this.properties.rosRoute
      const start = this.getStartPose()
      const targets = orderedTargets(rosRoute)

      if (meta && targets.length > 0) {
        ctx.strokeStyle = '#2563eb'
        ctx.lineWidth = 2 / this.view.scale
        ctx.setLineDash([7 / this.view.scale, 5 / this.view.scale])
        ctx.beginPath()
        const startPx = mapToPixel(start.x, start.y, meta)
        ctx.moveTo(startPx.x, startPx.y)
        targets.forEach((target) => {
          const p = mapToPixel(target.pose.x, target.pose.y, meta)
          ctx.lineTo(p.x, p.y)
        })
        if (this.getReturnToStart()) ctx.lineTo(startPx.x, startPx.y)
        ctx.stroke()
        ctx.setLineDash([])
      }

      if (meta && rosRoute) {
        this.drawPoint(ctx, meta, start, 'start', 0, rosRoute.start_pose.name || '起点')
        targets.forEach((target, index) => {
          this.drawPoint(ctx, meta, target.pose, 'target', index + 1, target.name, target.id)
        })
      }

      if (this.properties.mode === 'yaw' && this.yawPreview && meta) {
        const hit = this.getYawTarget()
        let origin
        if (hit.kind === 'start') {
          origin = mapToPixel(start.x, start.y, meta)
        } else {
          const t = targets.find((item) => item.id === hit.id)
          if (t) origin = mapToPixel(t.pose.x, t.pose.y, meta)
        }
        if (origin) {
          ctx.strokeStyle = '#f59e0b'
          ctx.lineWidth = 2 / this.view.scale
          ctx.setLineDash([6 / this.view.scale, 4 / this.view.scale])
          ctx.beginPath()
          ctx.moveTo(origin.x, origin.y)
          ctx.lineTo(this.yawPreview.x, this.yawPreview.y)
          ctx.stroke()
          ctx.setLineDash([])
        }
      }

      ctx.restore()
    },

    drawPoint(ctx, meta, pose, kind, order, label, id) {
      const p = mapToPixel(pose.x, pose.y, meta)
      const isStart = kind === 'start'
      const selectedTargetId = this.properties.selectedTargetId
      const yawTarget = this.getYawTarget()
      const selectedForYaw = yawTarget.kind === kind && (isStart || yawTarget.id === id)
      const selectedTarget = !isStart && selectedTargetId === id
      const inside = isInside(pose.x, pose.y, meta)
      const color = inside ? (isStart ? '#0f766e' : '#2563eb') : '#b42318'
      const radius = (isStart ? 8 : 7) / this.view.scale

      if (selectedForYaw || selectedTarget) {
        ctx.strokeStyle = selectedForYaw ? '#f59e0b' : '#0f766e'
        ctx.lineWidth = 3 / this.view.scale
        ctx.beginPath()
        ctx.arc(p.x, p.y, radius + 5 / this.view.scale, 0, Math.PI * 2)
        ctx.stroke()
      }

      ctx.fillStyle = color
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 2.5 / this.view.scale
      ctx.beginPath()
      ctx.arc(p.x, p.y, radius, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      this.drawArrow(ctx, p.x, p.y, pose.yaw || 0, color)

      if (!isStart) {
        ctx.fillStyle = '#ffffff'
        ctx.font = `700 ${10 / this.view.scale}px sans-serif`
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText(String(order), p.x, p.y)
        ctx.textAlign = 'left'
        ctx.textBaseline = 'alphabetic'
      }

      ctx.fillStyle = inside ? '#111827' : '#b42318'
      ctx.font = `${12 / this.view.scale}px sans-serif`
      const text = isStart ? '起点' : `#${order} ${label || ''}`
      ctx.fillText(text, p.x + 10 / this.view.scale, p.y - 8 / this.view.scale)
    },

    getYawTarget() {
      const kind = this.properties.yawTargetKind || 'start'
      if (kind === 'target' && this.properties.yawTargetId) {
        return { kind: 'target', id: this.properties.yawTargetId }
      }
      return { kind: 'start', id: null }
    },

    emitYawTarget(kind, id) {
      this.triggerEvent('yawtargetchange', { kind, id: id || '' })
    },

    touchDistance(t0, t1) {
      return Math.hypot(t0.clientX - t1.clientX, t0.clientY - t1.clientY)
    },

    getCenterMapCoord() {
      const meta = this.properties.mapMeta
      if (!meta || !this.displayWidth) return null
      const cx = this.displayWidth / 2
      const cy = this.displayHeight / 2
      const px = (cx - this.view.offsetX) / this.view.scale
      const py = (cy - this.view.offsetY) / this.view.scale
      return pixelToMap(px, py, meta)
    },

    onTouchStart(e) {
      if (!this.properties.editable || !this.properties.mapMeta) return
      this.updateRect(() => {
        if (e.touches.length >= 2) {
          const t0 = e.touches[0]
          const t1 = e.touches[1]
          const midX = (t0.clientX + t1.clientX) / 2 - this._rect.left
          const midY = (t0.clientY + t1.clientY) / 2 - this._rect.top
          this.drag = {
            type: 'pinch',
            startDist: this.touchDistance(t0, t1),
            startScale: this.view.scale,
            anchorPx: (midX - this.view.offsetX) / this.view.scale,
            anchorPy: (midY - this.view.offsetY) / this.view.scale,
            midX,
            midY,
          }
          return
        }

        const touch = e.touches[0]
        const p = this.screenToPixel(touch.clientX, touch.clientY)
        const hit = this.getPointHit(p.x, p.y)
        const mode = this.properties.mode

        if (mode === 'yaw') {
          this.yawPreview = p
          const yawHit = hit || this.getYawTarget()
          if (hit && hit.kind === 'target') {
            this.triggerEvent('selecttarget', { id: hit.id })
          }
          if (hit) {
            this.emitYawTarget(hit.kind, hit.id || '')
            this.drag = { type: 'yaw', hit: yawHit, moved: false }
          } else {
            this.applyYaw(yawHit, p)
            this.drag = { type: 'yaw', hit: yawHit, moved: true }
          }
          this.draw()
          return
        }

        if (hit && mode !== 'pan') {
          if (hit.kind === 'target') {
            this.triggerEvent('selecttarget', { id: hit.id })
            this.emitYawTarget('target', hit.id)
            this.drag = { type: 'target', id: hit.id }
          } else {
            this.emitYawTarget('start', '')
            this.drag = { type: 'start' }
          }
          return
        }

        if (mode === 'pan') {
          this.drag = {
            type: 'pan',
            startX: touch.clientX,
            startY: touch.clientY,
            ox: this.view.offsetX,
            oy: this.view.offsetY,
          }
        } else if (mode === 'start') {
          this.setStartFromPixel(p)
        } else if (mode === 'target') {
          this.triggerEvent('addtarget', pixelToMap(p.x, p.y, this.properties.mapMeta))
        }
      })
    },

    onTouchMove(e) {
      if (!this._rect) return

      if (e.touches.length >= 2 && this.drag && this.drag.type === 'pinch') {
        const t0 = e.touches[0]
        const t1 = e.touches[1]
        const midX = (t0.clientX + t1.clientX) / 2 - this._rect.left
        const midY = (t0.clientY + t1.clientY) / 2 - this._rect.top
        const dist = this.touchDistance(t0, t1)
        const factor = dist / this.drag.startDist
        this.view.scale = Math.max(0.04, Math.min(20, this.drag.startScale * factor))
        this.view.offsetX = midX - this.drag.anchorPx * this.view.scale
        this.view.offsetY = midY - this.drag.anchorPy * this.view.scale
        this.draw()
        return
      }

      const touch = e.touches[0]
      const p = this.screenToPixel(touch.clientX, touch.clientY)
      const meta = this.properties.mapMeta
      if (meta) {
        const map = pixelToMap(p.x, p.y, meta)
        this.triggerEvent('mapcursor', { x: map.x, y: map.y, px: Math.round(p.x), py: Math.round(p.y) })
      }

      if (!this.drag || this.drag.type === 'pinch') return

      if (this.properties.mode === 'yaw') {
        this.yawPreview = p
      }

      if (this.drag.type === 'pan') {
        this.view.offsetX = this.drag.ox + touch.clientX - this.drag.startX
        this.view.offsetY = this.drag.oy + touch.clientY - this.drag.startY
        this.draw()
      } else if (this.drag.type === 'start') {
        this.setStartFromPixel(p)
      } else if (this.drag.type === 'target') {
        const map = pixelToMap(p.x, p.y, this.properties.mapMeta)
        this.triggerEvent('movetarget', { id: this.drag.id, x: map.x, y: map.y })
      } else if (this.drag.type === 'yaw') {
        this.drag.moved = true
        this.applyYaw(this.drag.hit, p)
      }
    },

    onTouchEnd() {
      this.drag = null
      this.yawPreview = null
      this.draw()
    },

    setStartFromPixel(p) {
      const map = pixelToMap(p.x, p.y, this.properties.mapMeta)
      this.triggerEvent('setstart', map)
    },

    applyYaw(hit, pointerPx) {
      const meta = this.properties.mapMeta
      if (!meta) return
      let origin
      if (hit.kind === 'start') {
        const start = this.getStartPose()
        origin = mapToPixel(start.x, start.y, meta)
      } else {
        const targets = orderedTargets(this.properties.rosRoute)
        const target = targets.find((t) => t.id === hit.id)
        if (!target) return
        origin = mapToPixel(target.pose.x, target.pose.y, meta)
      }
      const yaw = computeYawFromPixel(origin, pointerPx)
      this.triggerEvent('setyaw', { kind: hit.kind, id: hit.id || '', yaw })
    },

    fitView() {
      this.fitToScreen()
      this.draw()
    },

    zoomBy(factor) {
      if (!this.properties.mapMeta || !this.displayWidth) return
      const cx = this.displayWidth / 2
      const cy = this.displayHeight / 2
      const px = (cx - this.view.offsetX) / this.view.scale
      const py = (cy - this.view.offsetY) / this.view.scale
      this.view.scale = Math.max(0.04, Math.min(20, this.view.scale * factor))
      this.view.offsetX = cx - px * this.view.scale
      this.view.offsetY = cy - py * this.view.scale
      this.draw()
    },

    zoomIn() {
      this.zoomBy(1.2)
    },

    zoomOut() {
      this.zoomBy(1 / 1.2)
    },
  },
})
