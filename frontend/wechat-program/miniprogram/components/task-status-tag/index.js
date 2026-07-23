const { TASK_STATUS_LABELS } = require('../../utils/constants')
const { BRIDGE_STATUS_LABELS } = require('../../utils/task-bridge')

const TYPE_MAP = {
  CREATED: 'info', DISPATCHED: 'primary', RUNNING: 'success', PAUSED: 'warning',
  MANUAL_TAKEOVER: 'warning', COMPLETED: 'success', CANCELLED: 'info',
  STARTING: 'primary', WAITING_LOCAL_CONFIRM: 'warning', START_FAILED: 'warning',
  FAILED: 'warning', ESTOPPED: 'warning', DISCONNECTED: 'warning', RECOVERING: 'primary',
}

Component({
  properties: { status: String },
  data: { label: '', type: 'info' },
  observers: {
    status(s) {
      const label = TASK_STATUS_LABELS[s] || BRIDGE_STATUS_LABELS[s] || s
      this.setData({ label, type: TYPE_MAP[s] || 'info' })
    },
  },
})
