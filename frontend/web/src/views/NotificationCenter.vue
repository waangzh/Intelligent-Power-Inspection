<template>
  <div class="inbox-page">
    <PageHeader title="消息中心" description="告警、任务、工单与系统通知" :breadcrumbs="[{ label: '运维中心' }, { label: '消息中心' }]">
      <template #actions>
        <el-button :disabled="!unreadCount" plain @click="markAll">全部标为已读</el-button>
      </template>
    </PageHeader>

    <div class="inbox-filters">
      <div class="chip-row">
        <button
          v-for="chip in readChips"
          :key="chip.key"
          type="button"
          class="inbox-chip"
          :class="{ active: readFilter === chip.key }"
          @click="setReadFilter(chip.key)"
        >
          {{ chip.label }}
        </button>
      </div>
      <div class="chip-row type-row">
        <button
          v-for="chip in typeChips"
          :key="chip.key"
          type="button"
          class="inbox-chip subtle"
          :class="{ active: typeFilter === chip.key }"
          @click="setTypeFilter(chip.key)"
        >
          {{ chip.label }}
        </button>
      </div>
    </div>

    <div class="inbox-layout">
      <el-card shadow="never" class="inbox-list-card">
        <el-empty v-if="!filteredList.length" description="暂无消息" :image-size="88" />
        <div v-else class="inbox-list">
          <button
            v-for="item in filteredList"
            :key="item.id"
            type="button"
            class="inbox-row"
            :class="{ unread: !item.read, active: selected?.id === item.id }"
            @click="selectItem(item)"
          >
            <span class="unread-dot" :class="{ visible: !item.read }" />
            <div class="row-body">
              <div class="row-top">
                <span class="type-prefix" :class="`type-${item.type.toLowerCase()}`">
                  {{ NOTIFICATION_TYPE_LABELS[item.type] }}
                </span>
                <span class="row-title">{{ item.title }}</span>
                <span class="row-time" :title="fmtFull(item.createdAt)">{{ fmtRelative(item.createdAt) }}</span>
              </div>
              <p class="row-preview">{{ item.content }}</p>
            </div>
          </button>
        </div>
        <ListPagination
          v-if="filteredList.length"
          :total="notificationStore.total"
          :page="notificationPage"
          @change="loadNotificationPage"
        />
      </el-card>

      <el-card shadow="never" class="inbox-detail-card">
        <template v-if="selected">
          <div class="detail-head">
            <span class="type-prefix lg" :class="`type-${selected.type.toLowerCase()}`">
              {{ NOTIFICATION_TYPE_LABELS[selected.type] }}
            </span>
            <h3 class="detail-title">{{ selected.title }}</h3>
            <time class="detail-time" :datetime="selected.createdAt">{{ fmtFull(selected.createdAt) }}</time>
          </div>
          <div class="detail-content">{{ selected.content }}</div>
          <div class="detail-actions">
            <el-button v-if="selected.link" type="primary" @click="goLink(selected)">查看详情</el-button>
            <el-button v-if="!selected.read" plain @click="markOne(selected.id)">标为已读</el-button>
            <el-button plain type="danger" @click="removeSelected">删除</el-button>
          </div>
        </template>
        <div v-else class="detail-empty">
          <el-icon :size="40"><Message /></el-icon>
          <p>选择左侧消息查看详情</p>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import type { AppNotification } from '@/types/notification'
import { NOTIFICATION_TYPE_LABELS } from '@/types/notification'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const typeFilter = ref('')
const readFilter = ref<'all' | 'unread'>('all')
const notificationPage = ref(0)
const selected = ref<AppNotification | null>(null)

const readChips = [
  { key: 'all' as const, label: '全部消息' },
  { key: 'unread' as const, label: '仅未读' },
]

const typeChips = [
  { key: '', label: '全部类型' },
  ...Object.entries(NOTIFICATION_TYPE_LABELS).map(([key, label]) => ({
    key,
    label,
  })),
]

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

function setReadFilter(key: 'all' | 'unread') {
  readFilter.value = key
  searchNotifications()
}

function setTypeFilter(key: string) {
  typeFilter.value = key
  searchNotifications()
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

watch(filteredList, (items) => {
  if (!items.length) {
    selected.value = null
    return
  }
  if (!selected.value || !items.some((item) => item.id === selected.value?.id)) {
    selected.value = items[0]
  }
}, { immediate: true })

function selectItem(item: AppNotification) {
  selected.value = item
}

function markAll() {
  if (userId.value) notificationStore.markAllRead(userId.value)
}

function markOne(id: string) {
  notificationStore.markRead(id)
  if (selected.value?.id === id) {
    selected.value = { ...selected.value, read: true }
  }
}

function removeOne(id: string) {
  notificationStore.remove(id)
}

function removeSelected() {
  if (!selected.value) return
  removeOne(selected.value.id)
}

function goLink(item: AppNotification) {
  notificationStore.markRead(item.id)
  if (item.link) router.push(item.link)
}

function fmtFull(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function fmtRelative(iso: string) {
  const diffSec = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diffSec < 60) return '刚刚'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`
  if (diffSec < 604800) return `${Math.floor(diffSec / 86400)} 天前`
  return fmtFull(iso)
}
</script>

<style scoped>
.inbox-filters {
  margin-bottom: 16px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid var(--pi-border);
  border-radius: var(--pi-card-radius);
  box-shadow: var(--pi-card-shadow);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.type-row {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--pi-border-soft);
}

.inbox-chip {
  padding: 6px 14px;
  border: 1px solid var(--pi-border);
  border-radius: 999px;
  background: #fff;
  color: #526986;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.inbox-chip.subtle {
  font-weight: 500;
  font-size: 12px;
  padding: 5px 12px;
}

.inbox-chip:hover {
  border-color: #c5d4e8;
  background: #f7faff;
}

.inbox-chip.active {
  border-color: #1768f2;
  background: #e6f4ff;
  color: #1768f2;
}

.inbox-layout {
  display: grid;
  grid-template-columns: minmax(280px, 38%) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.inbox-list-card,
.inbox-detail-card {
  min-height: 520px;
}

.inbox-list-card :deep(.el-card__body) {
  padding: 0;
}

.inbox-list-card :deep(.el-pagination) {
  padding: 12px 16px;
}

.inbox-list {
  display: flex;
  flex-direction: column;
}

.inbox-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 14px 16px;
  border: none;
  border-bottom: 1px solid var(--pi-border-soft);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.inbox-row:hover {
  background: #fafcff;
}

.inbox-row.active {
  background: #f0f7ff;
}

.inbox-row.unread .row-title {
  font-weight: 700;
  color: #102a56;
}

.unread-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  opacity: 0;
}

.unread-dot.visible {
  opacity: 1;
  background: #1768f2;
}

.row-body {
  flex: 1;
  min-width: 0;
}

.row-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.type-prefix {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.type-prefix.lg {
  font-size: 12px;
}

.type-prefix.type-alarm { color: #cf1322; }
.type-prefix.type-task { color: #d46b08; }
.type-prefix.type-workorder { color: #1768f2; }
.type-prefix.type-system { color: #6f8099; }
.type-prefix.type-agent { color: #389e0d; }

.row-title {
  flex: 1;
  font-size: 14px;
  color: #314a6b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-time {
  flex-shrink: 0;
  font-size: 11px;
  color: #909399;
}

.row-preview {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: #8a9bb0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.inbox-row.unread .row-preview {
  color: #526986;
}

.inbox-detail-card :deep(.el-card__body) {
  padding: 20px 22px;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.detail-head {
  margin-bottom: 16px;
}

.detail-title {
  margin: 8px 0 6px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--pi-text);
}

.detail-time {
  font-size: 12px;
  color: var(--pi-muted);
}

.detail-content {
  flex: 1;
  font-size: 14px;
  line-height: 1.7;
  color: #314a6b;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--pi-border-soft);
}

.detail-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #909399;
  min-height: 400px;
}

.detail-empty p {
  margin: 0;
  font-size: 14px;
}

@media (max-width: 960px) {
  .inbox-layout {
    grid-template-columns: 1fr;
  }

  .inbox-list-card,
  .inbox-detail-card {
    min-height: auto;
  }

  .inbox-detail-card :deep(.el-card__body) {
    min-height: 240px;
  }
}
</style>
