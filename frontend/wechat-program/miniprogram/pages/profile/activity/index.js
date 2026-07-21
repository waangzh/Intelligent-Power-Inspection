const api = require('../../../services/index')
const { ACTIVITY_TYPE_LABELS } = require('../../../utils/constants')

const TYPE_TAG = {
  LOGIN: 'info',
  PROFILE: 'primary',
  AVATAR: 'success',
  PASSWORD: 'warning',
  TASK: 'primary',
  ALARM: 'danger',
  SETTINGS: 'info',
}

function formatActivityTime(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return String(iso).slice(0, 19).replace('T', ' ')
  return date.toLocaleString('zh-CN', { hour12: false })
}

Page({
  data: {
    activities: [],
    loading: false,
    loadError: '',
  },

  onShow() {
    if (!getApp().requireAuth('/pages/profile/activity/index')) return
    this.load()
  },

  async load() {
    this.setData({ loading: true, loadError: '' })
    try {
      const items = await api.getActivities()
      const list = (Array.isArray(items) ? items : [])
        .map((a) => ({
          ...a,
          typeLabel: ACTIVITY_TYPE_LABELS[a.type] || a.type || '记录',
          tagType: TYPE_TAG[a.type] || 'primary',
          time: formatActivityTime(a.createdAt),
        }))
      this.setData({ activities: list, loading: false })
    } catch (e) {
      this.setData({
        activities: [],
        loading: false,
        loadError: e.message || '加载失败，请检查登录状态与后端服务',
      })
    }
  },
})
