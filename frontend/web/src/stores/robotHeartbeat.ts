import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { RobotHeartbeatStatus } from '@/types/robotHeartbeat'

export const useRobotHeartbeatStore = defineStore('robotHeartbeat', () => {
  const items = ref<RobotHeartbeatStatus[]>([])
  const loaded = ref(false)
  const loadFailed = ref(false)

  const onlineCount = computed(() => items.value.filter((item) => item.online).length)
  const totalCount = computed(() => items.value.length)
  const byRobotId = computed(() => {
    const map = new Map<string, RobotHeartbeatStatus>()
    items.value.forEach((item) => map.set(item.robotId, item))
    return map
  })

  async function refresh() {
    loadFailed.value = false
    try {
      const page = await resourcesApi.listRobotHeartbeatStatus({
        sort: 'lastHeartbeatAt',
        direction: 'desc',
        page: 0,
        size: 100,
      })
      items.value = page.items
      loaded.value = true
    } catch {
      items.value = []
      loaded.value = false
      loadFailed.value = true
    }
  }

  function isOnline(robotId: string) {
    return byRobotId.value.get(robotId)?.online === true
  }

  return { items, loaded, loadFailed, onlineCount, totalCount, byRobotId, refresh, isOnline }
})
