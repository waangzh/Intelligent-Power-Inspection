import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Alarm, AlarmSeverity, Checkpoint, DetectionType, InspectionTask } from '@/types'
import { DETECTION_LABELS } from '@/types'
import { useAlarmPolicyStore } from '@/stores/alarmPolicy'
import { useAuthStore } from '@/stores/auth'
import { useWorkOrderStore } from '@/stores/workOrder'
import { hasPermission } from '@/utils/permission'
import { uid } from '@/utils/storage'

const ROUTE_ALARM_TYPES: DetectionType[] = ['PERSON', 'HELMET', 'FIRE', 'OBSTACLE']

const SEVERITY_MAP: Record<DetectionType, AlarmSeverity> = {
  PERSON: 'MEDIUM',
  HELMET: 'HIGH',
  OBSTACLE: 'MEDIUM',
  FIRE: 'CRITICAL',
  SWITCH: 'HIGH',
  METER: 'LOW',
  OIL_LEAK: 'HIGH',
  FOREIGN_OBJECT: 'MEDIUM',
}

export const useAlarmStore = defineStore('alarm', () => {
  const alarms = ref<Alarm[]>([])

  const unacknowledgedCount = computed(() => alarms.value.filter((a) => !a.acknowledged).length)

  async function load() {
    alarms.value = await resourcesApi.listAlarms()
    await tryAutoConvertPending()
  }

  async function tryAutoConvertForAlarm(alarm: Alarm) {
    const policyStore = useAlarmPolicyStore()
    const workOrderStore = useWorkOrderStore()
    const authStore = useAuthStore()
    const user = authStore.user
    if (!user || !hasPermission(user.role, 'workorder:create')) return
    if (workOrderStore.getByAlarmId(alarm.id)) return
    if (!policyStore.shouldAutoConvert(alarm.severity)) return
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

  function addAlarm(partial: Omit<Alarm, 'id' | 'createdAt' | 'acknowledged'>) {
    const alarm: Alarm = {
      ...partial,
      id: uid('alarm'),
      acknowledged: false,
      createdAt: new Date().toISOString(),
    }
    alarms.value.unshift(alarm)
    return alarm
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

  function maybeGenerateRouteAlarm(task: InspectionTask, routeName: string) {
    const type = ROUTE_ALARM_TYPES[Math.floor(Math.random() * ROUTE_ALARM_TYPES.length)]
    const messages: Record<DetectionType, string> = {
      PERSON: '路线行进中检测到未授权人员',
      HELMET: '检测到作业人员未佩戴安全帽',
      OBSTACLE: '前方检测到障碍物，机器人已减速避障',
      FIRE: '路线视野内检测到疑似火源/烟雾',
      SWITCH: '',
      METER: '',
      OIL_LEAK: '',
      FOREIGN_OBJECT: '',
    }
    addAlarm({
      taskId: task.id,
      routeName,
      type,
      severity: SEVERITY_MAP[type],
      message: messages[type],
      imageUrl: `https://picsum.photos/seed/${Date.now()}/400/240`,
    })
  }

  function maybeGenerateCheckpointAlarm(task: InspectionTask, routeName: string, cp: Checkpoint) {
    const enabled = cp.detections.filter((d) => d.enabled)
    if (enabled.length === 0 || Math.random() > 0.55) return

    const det = enabled[Math.floor(Math.random() * enabled.length)]
    const type = det.type
    const label = DETECTION_LABELS[type]

    addAlarm({
      taskId: task.id,
      routeName,
      checkpointName: cp.name,
      type,
      severity: SEVERITY_MAP[type],
      message: `检查点「${cp.name}」${label}异常${det.prompt ? `（LocateAnything: ${det.prompt}）` : ''}`,
      imageUrl: `https://picsum.photos/seed/${cp.id}_${Date.now()}/400/240`,
    })
  }

  function updateLocalAlarm(alarm: Alarm) {
    const idx = alarms.value.findIndex((a) => a.id === alarm.id)
    if (idx >= 0) alarms.value[idx] = alarm
    else alarms.value.unshift(alarm)
    void tryAutoConvertForAlarm(alarm)
  }

  return {
    alarms,
    unacknowledgedCount,
    load,
    addAlarm,
    acknowledge,
    acknowledgeAll,
    maybeGenerateRouteAlarm,
    maybeGenerateCheckpointAlarm,
    applyRemoteAlarm: updateLocalAlarm,
  }
})
