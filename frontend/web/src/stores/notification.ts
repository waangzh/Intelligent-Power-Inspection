import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { AppNotification, NotificationType } from '@/types/notification'
import type { ListQuery } from '@/types/pagination'

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<AppNotification[]>([])
  const total = ref(0)

  const unreadCount = computed(() => items.value.filter((n) => !n.read).length)

  async function load(query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listNotifications(query)
    items.value = result.items
    total.value = result.total
  }

  function push(
    userId: string,
    type: NotificationType,
    title: string,
    content: string,
    link?: string,
  ) {
    const n = resourcesApi.pushLocalOnly(userId, type, title, content, link)
    items.value.unshift(n)
    return n
  }

  function pushToAll(type: NotificationType, title: string, content: string, link?: string) {
    return push('*', type, title, content, link)
  }

  function forUser(userId: string) {
    return items.value.filter((n) => n.userId === userId || n.userId === '*')
  }

  function markRead(id: string) {
    const n = items.value.find((i) => i.id === id)
    if (n) {
      n.read = true
      void resourcesApi.markNotificationRead(id).then(updateLocalNotification)
    }
  }

  function markAllRead(userId: string) {
    items.value.forEach((n) => {
      if ((n.userId === userId || n.userId === '*') && !n.read) n.read = true
    })
    void resourcesApi.markAllNotificationsRead().then((remote) => {
      items.value = remote
    })
  }

  function remove(id: string) {
    items.value = items.value.filter((n) => n.id !== id)
    void resourcesApi.removeNotification(id)
  }

  function updateLocalNotification(item: AppNotification) {
    const idx = items.value.findIndex((n) => n.id === item.id)
    if (idx >= 0) items.value[idx] = item
    else items.value.unshift(item)
  }

  return {
    items,
    total,
    unreadCount,
    load,
    push,
    pushToAll,
    forUser,
    markRead,
    markAllRead,
    remove,
    applyRemoteNotification: updateLocalNotification,
  }
})
