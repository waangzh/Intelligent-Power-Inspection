<template>
  <div class="profile-section">
    <h3 class="section-title"><span class="title-bar" />我的记录</h3>
    <p class="section-desc">您在平台上的操作与动态记录</p>

    <el-table :data="records" size="small" empty-text="暂无记录">
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'
import { useTaskStore } from '@/stores/task'
import type { UserActivity, UserActivityType } from '@/types/auth'

const authStore = useAuthStore()
const profileStore = useProfileStore()
const taskStore = useTaskStore()
const alarmStore = useAlarmStore()

const TYPE_LABELS: Record<UserActivityType, string> = {
  LOGIN: '登录',
  PROFILE: '资料',
  AVATAR: '头像',
  PASSWORD: '安全',
  TASK: '任务',
  ALARM: '告警',
  SETTINGS: '设置',
}

function typeLabel(t: UserActivityType) {
  return TYPE_LABELS[t] ?? t
}

function typeTag(t: UserActivityType): '' | 'success' | 'warning' | 'info' | 'danger' {
  return { LOGIN: 'info', PROFILE: '', AVATAR: 'success', PASSWORD: 'warning', TASK: '', ALARM: 'danger', SETTINGS: 'info' }[t] as '' | 'success' | 'warning' | 'info' | 'danger'
}

const records = computed(() => {
  const userId = authStore.user?.id
  if (!userId) return []

  const derived: UserActivity[] = []

  taskStore.tasks.slice(0, 5).forEach((t) => {
    derived.push({
      id: `task_${t.id}`,
      userId,
      type: 'TASK',
      message: `创建/参与任务：${t.name}`,
      createdAt: t.createdAt,
    })
  })

  alarmStore.alarms.filter((a) => a.acknowledged).slice(0, 5).forEach((a) => {
    derived.push({
      id: `alarm_${a.id}`,
      userId,
      type: 'ALARM',
      message: `确认告警：${a.message}`,
      createdAt: a.createdAt,
    })
  })

  return [...profileStore.activities, ...derived]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 30)
})

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

onMounted(() => {
  if (authStore.user) {
    profileStore.loadActivities(authStore.user.id)
  }
})
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #1a2b3c;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 18px;
  background: #1a5fb4;
  border-radius: 2px;
}

.section-desc {
  margin: 0 0 24px;
  font-size: 13px;
  color: #909399;
}
</style>
