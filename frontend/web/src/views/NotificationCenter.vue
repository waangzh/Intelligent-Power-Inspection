<template>
  <div>
    <PageHeader title="消息中心" description="告警、任务、工单与系统通知" :breadcrumbs="[{ label: '运维中心' }, { label: '消息中心' }]">
      <template #actions>
        <el-button :disabled="!unreadCount" @click="markAll">全部标为已读</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-radio-group v-model="typeFilter" size="small" @change="searchNotifications">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="(label, key) in NOTIFICATION_TYPE_LABELS" :key="key" :value="key">{{ label }}</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="readFilter" size="small" style="margin-left: 16px" @change="searchNotifications">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="unread">未读</el-radio-button>
      </el-radio-group>
    </el-card>

    <el-card shadow="never">
      <div v-if="!filteredList.length" class="empty-hint">暂无消息</div>
      <div
        v-for="item in filteredList"
        :key="item.id"
        class="msg-item"
        :class="{ unread: !item.read }"
        @click="openItem(item)"
      >
        <div class="msg-head">
          <el-tag size="small" :type="typeTag(item.type)">{{ NOTIFICATION_TYPE_LABELS[item.type] }}</el-tag>
          <span class="msg-title">{{ item.title }}</span>
          <span class="msg-time">{{ fmt(item.createdAt) }}</span>
        </div>
        <p class="msg-content">{{ item.content }}</p>
        <div class="msg-actions">
          <el-button v-if="!item.read" text type="primary" size="small" @click.stop="markOne(item.id)">标为已读</el-button>
          <el-button text type="danger" size="small" @click.stop="removeOne(item.id)">删除</el-button>
        </div>
      </div>
      <ListPagination :total="notificationStore.total" :page="notificationPage" @change="loadNotificationPage" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import type { AppNotification, NotificationType } from '@/types/notification'
import { NOTIFICATION_TYPE_LABELS } from '@/types/notification'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const typeFilter = ref('')
const readFilter = ref<'all' | 'unread'>('all')
const notificationPage = ref(0)

function loadNotificationPage(page: number) {
  notificationPage.value = page
  void notificationStore.load({
    page,
    size: 20,
    type: typeFilter.value || undefined,
    read: readFilter.value === 'unread' ? false : undefined,
  })
}

function searchNotifications() {
  loadNotificationPage(0)
}

const userId = computed(() => authStore.user?.id ?? '')

const list = computed(() => notificationStore.forUser(userId.value))

const unreadCount = computed(() => list.value.filter((n) => !n.read).length)

const filteredList = computed(() => {
  let items = list.value
  if (typeFilter.value) items = items.filter((n) => n.type === typeFilter.value)
  if (readFilter.value === 'unread') items = items.filter((n) => !n.read)
  return items
})

function markAll() {
  if (userId.value) notificationStore.markAllRead(userId.value)
}

function markOne(id: string) {
  notificationStore.markRead(id)
}

function removeOne(id: string) {
  notificationStore.remove(id)
}

function openItem(item: AppNotification) {
  notificationStore.markRead(item.id)
  if (item.link) router.push(item.link)
}

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function typeTag(t: NotificationType): '' | 'success' | 'warning' | 'danger' | 'info' {
  return { ALARM: 'danger', TASK: 'warning', WORKORDER: '', SYSTEM: 'info', AGENT: 'success' }[t] as '' | 'success' | 'warning' | 'danger' | 'info'
}
</script>

<style scoped>
.msg-item {
  padding: 14px 16px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
  transition: background 0.15s;
}

.msg-item:hover {
  background: #fafbfc;
}

.msg-item.unread {
  background: #f0f7ff;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.msg-title {
  flex: 1;
  font-weight: 600;
  color: #1a2b3c;
  font-size: 14px;
}

.msg-time {
  font-size: 12px;
  color: #909399;
}

.msg-content {
  margin: 8px 0 0;
  font-size: 13px;
  color: #606266;
}

.msg-actions {
  margin-top: 6px;
}
</style>
