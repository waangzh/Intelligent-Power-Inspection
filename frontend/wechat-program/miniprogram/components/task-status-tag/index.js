const { TASK_STATUS_LABELS } = require('../../utils/constants')

const TYPE_MAP = {
  CREATED: 'info', DISPATCHED: 'primary', RUNNING: 'success', PAUSED: 'warning',
  MANUAL_TAKEOVER: 'warning', COMPLETED: 'success', CANCELLED: 'info',
}

Component({
  properties: { status: String },
  data: { label: '', type: 'info' },
  observers: {
    status(s) {
      this.setData({ label: TASK_STATUS_LABELS[s] || s, type: TYPE_MAP[s] || 'info' })
    },
  },
})
