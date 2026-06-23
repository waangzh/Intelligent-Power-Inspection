const { DETECTION_LABELS } = require('../../utils/constants')

const PROMPT_TYPES = ['SWITCH', 'OIL_LEAK', 'METER', 'FOREIGN_OBJECT']

Component({
  properties: {
    items: { type: Array, value: [] },
  },
  data: { rows: [] },
  observers: {
    items(items) {
      const rows = (items || []).map((it) => ({
        type: it.type,
        label: DETECTION_LABELS[it.type] || it.type,
        enabled: !!it.enabled,
        thresholdPct: Math.round((it.threshold || 0.75) * 100),
        prompt: it.prompt || '',
        needsPrompt: PROMPT_TYPES.includes(it.type),
      }))
      this.setData({ rows })
    },
  },
  methods: {
    emitChange() {
      const items = this.data.rows.map((r) => ({
        type: r.type,
        enabled: r.enabled,
        threshold: r.thresholdPct / 100,
        prompt: r.prompt || undefined,
      }))
      this.triggerEvent('change', { items })
    },
    onToggle(e) {
      const idx = e.currentTarget.dataset.idx
      this.setData({ [`rows[${idx}].enabled`]: e.detail.value })
      this.emitChange()
    },
    onThreshold(e) {
      const idx = e.currentTarget.dataset.idx
      this.setData({ [`rows[${idx}].thresholdPct`]: e.detail.value })
      this.emitChange()
    },
    onPrompt(e) {
      const idx = e.currentTarget.dataset.idx
      this.setData({ [`rows[${idx}].prompt`]: e.detail.value })
      this.emitChange()
    },
  },
})
