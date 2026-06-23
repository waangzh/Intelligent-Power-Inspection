const api = require('../../services/index')

Component({
  properties: {
    displayName: { type: String, value: '用户' },
    avatarUrl: String,
    seed: String,
    size: { type: Number, value: 80 },
  },
  data: { color: '#1a5fb4', initials: '?', fontSize: 32 },
  observers: {
    'displayName, seed, avatarUrl'() { this.update() },
  },
  lifetimes: {
    attached() { this.update() },
  },
  methods: {
    update() {
      const { displayName, seed, size } = this.properties
      const av = api.generateDefaultAvatar(displayName, seed || displayName)
      this.setData({ color: av.color, initials: av.initials, fontSize: Math.round(size * 0.4) })
    },
  },
})
