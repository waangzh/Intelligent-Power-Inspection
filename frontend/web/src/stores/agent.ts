import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import { subscribeTopic } from '@/api/realtime'
import type {
  AgentActionDecisionRequest,
  AgentCaseDetail,
  AgentCaseSummary,
  AgentRunDetail,
  AuditedAgentAction,
  AuditedAgentRealtimeEvent,
  CreateAgentCaseRequest,
} from '@/types/agent'

export const useAgentStore = defineStore('agent', () => {
  const cases = ref<AgentCaseSummary[]>([])
  const activeCase = ref<AgentCaseDetail | null>(null)
  const activeRun = ref<AgentRunDetail | null>(null)
  const realtimeEvents = ref<AuditedAgentRealtimeEvent[]>([])
  const loading = ref(false)
  const seenEventKeys = new Set<string>()
  const MAX_EVENT_KEYS = 500
  let unsubscribe: (() => void) | null = null
  let refreshTimer: ReturnType<typeof setTimeout> | null = null

  const activeCaseSummary = computed(() => activeCase.value?.item ?? null)
  const activeRunId = computed(() => activeRun.value?.run.id ?? '')
  const pendingActions = computed(() => activeRun.value?.actions.filter((item) => item.status === 'PROPOSED') ?? [])

  async function loadCases() {
    cases.value = await resourcesApi.listAgentCases()
  }

  async function loadCase(caseId: string, runId?: string, subscribe = true) {
    const detail = await resourcesApi.getAgentCase(caseId)
    activeCase.value = detail
    upsertCase(detail.item)
    const selectedRunId = runId || activeRun.value?.run.id || detail.item.latestRun?.id || detail.runs[0]?.id
    if (selectedRunId) {
      await loadRun(selectedRunId, false)
    } else {
      activeRun.value = null
    }
    if (subscribe) subscribeCase(caseId)
    return detail
  }

  async function loadRun(runId: string, refreshCase = true) {
    activeRun.value = await resourcesApi.getAgentRun(runId)
    if (refreshCase && activeCase.value) {
      const detail = await resourcesApi.getAgentCase(activeCase.value.item.id)
      activeCase.value = detail
      upsertCase(detail.item)
    }
    return activeRun.value
  }

  async function selectRun(runId: string) {
    await loadRun(runId)
  }

  async function createCaseAndRun(request: CreateAgentCaseRequest) {
    loading.value = true
    try {
      const item = await resourcesApi.createAgentCase(request)
      upsertCase(item)
      const run = await resourcesApi.startAgentRun(item.id, { reason: 'INITIAL_ANALYSIS' })
      await loadCase(item.id, run.id)
      return item
    } finally {
      loading.value = false
    }
  }

  async function startActiveRun(reason = 'MANUAL_REANALYSIS') {
    if (!activeCase.value) return null
    loading.value = true
    try {
      const run = await resourcesApi.startAgentRun(activeCase.value.item.id, { reason })
      await loadCase(activeCase.value.item.id, run.id)
      return run
    } finally {
      loading.value = false
    }
  }

  async function approveAction(action: AuditedAgentAction, comment = '批准执行') {
    const request: AgentActionDecisionRequest = { version: action.version, comment }
    const saved = await resourcesApi.approveAuditedAgentAction(action.id, request)
    await refreshAfterAction(saved)
    return saved
  }

  async function rejectAction(action: AuditedAgentAction, comment = '拒绝执行') {
    const request: AgentActionDecisionRequest = { version: action.version, comment }
    const saved = await resourcesApi.rejectAuditedAgentAction(action.id, request)
    await refreshAfterAction(saved)
    return saved
  }

  function subscribeCase(caseId: string) {
    unsubscribe?.()
    realtimeEvents.value = []
    seenEventKeys.clear()
    unsubscribe = subscribeTopic<AuditedAgentRealtimeEvent>(`/topic/agent-cases/${caseId}`, (event) => {
      const key = `${event.runId}:${event.sequenceNo}`
      if (seenEventKeys.has(key)) return
      if (seenEventKeys.size >= MAX_EVENT_KEYS) {
        const oldest = seenEventKeys.values().next().value
        if (oldest) seenEventKeys.delete(oldest)
      }
      seenEventKeys.add(key)
      realtimeEvents.value = [...realtimeEvents.value, event].sort((left, right) => left.sequenceNo - right.sequenceNo)
      scheduleRealtimeRefresh(event)
    })
  }

  function scheduleRealtimeRefresh(event: AuditedAgentRealtimeEvent) {
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => {
      refreshTimer = null
      if (activeRun.value?.run.id === event.runId) {
        void loadRun(event.runId, true)
      } else if (activeCase.value?.item.id === event.caseId) {
        void loadCase(event.caseId, activeRun.value?.run.id, false)
      }
    }, 400)
  }

  function stopRealtime() {
    unsubscribe?.()
    unsubscribe = null
    if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
    realtimeEvents.value = []
    seenEventKeys.clear()
  }

  async function refreshAfterAction(action: AuditedAgentAction) {
    if (activeRun.value?.actions.some((item) => item.id === action.id)) {
      await loadRun(activeRun.value.run.id)
    }
  }

  function upsertCase(item: AgentCaseSummary) {
    const index = cases.value.findIndex((current) => current.id === item.id)
    if (index >= 0) cases.value[index] = item
    else cases.value.unshift(item)
  }

  return {
    cases,
    activeCase,
    activeCaseSummary,
    activeRun,
    activeRunId,
    realtimeEvents,
    loading,
    pendingActions,
    loadCases,
    loadCase,
    loadRun,
    selectRun,
    createCaseAndRun,
    startActiveRun,
    approveAction,
    rejectAction,
    subscribeCase,
    stopRealtime,
  }
})
