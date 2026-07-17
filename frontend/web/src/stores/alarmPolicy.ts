import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlarmSeverity } from '@/types'

export type EscalationMode = 'AUTO' | 'MANUAL'

const STORAGE_KEY = 'pi_alarm_escalation_policy'

export const DEFAULT_ESCALATION_POLICY: Record<AlarmSeverity, EscalationMode> = {
  CRITICAL: 'AUTO',
  HIGH: 'AUTO',
  MEDIUM: 'MANUAL',
  LOW: 'MANUAL',
}

function loadPolicy(): Record<AlarmSeverity, EscalationMode> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULT_ESCALATION_POLICY }
    return { ...DEFAULT_ESCALATION_POLICY, ...JSON.parse(raw) }
  } catch {
    return { ...DEFAULT_ESCALATION_POLICY }
  }
}

export const useAlarmPolicyStore = defineStore('alarmPolicy', () => {
  const policy = ref<Record<AlarmSeverity, EscalationMode>>(loadPolicy())

  function save() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(policy.value))
  }

  function setMode(severity: AlarmSeverity, mode: EscalationMode) {
    policy.value[severity] = mode
    save()
  }

  function shouldAutoConvert(severity: AlarmSeverity) {
    return policy.value[severity] === 'AUTO'
  }

  return { policy, setMode, shouldAutoConvert, save }
})
