import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import { openapiClient } from '@/generated/api-client'
import type { Alarm, AlarmSeverity, AlarmWorkOrderMode, AlarmWorkOrderPolicy } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { useWorkOrderStore } from '@/stores/workOrder'
import { hasPermission } from '@/utils/permission'
import type { ListQuery } from '@/types/pagination'

export const useAlarmStore = defineStore('alarm', () => {
  const alarms = ref<Alarm[]>([])
  const total = ref(0)
  let latestLoadRequest = 0
  const workOrderPolicy = ref<AlarmWorkOrderPolicy>({
    id: 'default',
    rules: { CRITICAL: 'AUTO', HIGH: 'AUTO', MEDIUM: 'MANUAL', LOW: 'MANUAL' },
  })

  const unacknowledgedCount = computed(() => alarms.value.filter((a) => !a.acknowledged).length)

  async function load(query: ListQuery = { size: 20 }) {
    const requestId = ++latestLoadRequest
    const result = await resourcesApi.listAlarms(query)
    if (requestId !== latestLoadRequest) return false
    alarms.value = result.items
    total.value = result.total
    try {
      workOrderPolicy.value = await openapiClient.alarms.getWorkOrderPolicy()
    } catch {
      // 无法读取策略时继续使用默认规则，告警列表不受影响。
    }
    if (requestId !== latestLoadRequest) return false
    await tryAutoConvertPending()
    return true
  }

  async function tryAutoConvertForAlarm(alarm: Alarm) {
    const workOrderStore = useWorkOrderStore()
    const authStore = useAuthStore()
    const user = authStore.user
    if (!user || !hasPermission(authStore.permissions, 'workorder:create')) return
    if (workOrderStore.getByAlarmId(alarm.id)) return
    if (workOrderPolicy.value.rules[alarm.severity] !== 'AUTO') return
    try {
      await workOrderStore.createFromAlarm(alarm, { id: user.id, name: user.displayName }, { autoConverted: true })
      if (!alarm.acknowledged) acknowledge(alarm.id)
    } catch {
      // 已转工单或接口失败时忽略
    }
  }

  async function tryAutoConvertPending() {
    for (const alarm of alarms.value) {
      await tryAutoConvertForAlarm(alarm)
    }
  }

  function acknowledge(id: string) {
    const alarm = alarms.value.find((a) => a.id === id)
    if (alarm) {
      alarm.acknowledged = true
      void resourcesApi.acknowledgeAlarm(id).then(updateLocalAlarm)
    }
  }

  function acknowledgeAll() {
    alarms.value.forEach((a) => {
      a.acknowledged = true
    })
    void resourcesApi.acknowledgeAllAlarms().then((items) => {
      alarms.value = items
    })
  }

  function updateLocalAlarm(alarm: Alarm) {
    const idx = alarms.value.findIndex((a) => a.id === alarm.id)
    if (idx >= 0) alarms.value[idx] = alarm
    else alarms.value.unshift(alarm)
    void tryAutoConvertForAlarm(alarm)
  }

  async function retryWorkOrder(id: string) {
    const updated = await resourcesApi.retryAlarmWorkOrder(id)
    updateLocalAlarm(updated)
    return updated
  }

  async function loadWorkOrderPolicy() {
    try {
      workOrderPolicy.value = await openapiClient.alarms.getWorkOrderPolicy()
    } catch {
      // 保留当前默认/缓存规则
    }
    return workOrderPolicy.value
  }

  async function saveWorkOrderPolicy(rules: Record<AlarmSeverity, AlarmWorkOrderMode>) {
    workOrderPolicy.value = await openapiClient.alarms.updateWorkOrderPolicy(rules)
    return workOrderPolicy.value
  }

  return {
    alarms,
    total,
    workOrderPolicy,
    unacknowledgedCount,
    load,
    loadWorkOrderPolicy,
    acknowledge,
    acknowledgeAll,
    retryWorkOrder,
    saveWorkOrderPolicy,
    applyRemoteAlarm: updateLocalAlarm,
  }
})
