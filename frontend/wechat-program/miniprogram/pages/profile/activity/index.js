const api = require('../../../services/index')
const { ACTIVITY_TYPE_LABELS } = require('../../../utils/constants')

const TYPE_TAG = {
  LOGIN: 'info', PROFILE: 'primary', AVATAR: 'success', PASSWORD: 'warning',
  TASK: 'primary', ALARM: 'danger', SETTINGS: 'info',
}

Page({
  data: { activities: [] },

  onShow() {
    if (!getApp().requireAuth('/pages/profile/activity/index')) return
    this.load()
  },

  async load() {
    try {
      const userId = getApp().globalData.user.id
      const [base, tasks, alarms] = await Promise.all([
        api.getActivities(),
        api.getTasks(),
        api.getAlarms(),
      ])
      const derived = []
      tasks.slice(0, 5).forEach((t) => {
        derived.push({
          id: `task_${t.id}`,
          userId,
          type: 'TASK',
          message: `创建/参与任务：${t.name}`,
          createdAt: t.createdAt,
        })
      })
      alarms.filter((a) => a.acknowledged).slice(0, 5).forEach((a) => {
        derived.push({
          id: `alarm_${a.id}`,
          userId,
          type: 'ALARM',
          message: `确认告警：${a.message}`,
          createdAt: a.createdAt,
        })
      })
      const activities = [...base, ...derived]
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
        .slice(0, 30)
        .map((a) => ({
          ...a,
          typeLabel: ACTIVITY_TYPE_LABELS[a.type] || a.type,
          tagType: TYPE_TAG[a.type] || 'primary',
          time: a.createdAt ? a.createdAt.slice(0, 19).replace('T', ' ') : '',
        }))
      this.setData({ activities })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },
})
