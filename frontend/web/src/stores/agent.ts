import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import { subscribeTopic } from '@/api/realtime'
import type { AgentAction, AgentRealtimeEvent, AgentSession, CreateAgentSessionRequest } from '@/types/agent'

export const useAgentStore = defineStore('agent', () => {
  const sessions = ref<AgentSession[]>([])
  const activeSession = ref<AgentSession | null>(null)
  const realtimeEvents = ref<AgentRealtimeEvent[]>([])
  const loading = ref(false)
  let unsubscribe: (() => void) | null = null

  const pendingActions = computed(() => activeSession.value?.actions?.filter((item) => item.status === 'PENDING') ?? [])
  const latestSteps = computed(() => activeSession.value?.latestRun?.steps ?? [])

  async function loadSessions() {
    sessions.value = await resourcesApi.listAgentSessions()
  }

  async function loadSession(id: string, subscribe = true) {
    activeSession.value = await resourcesApi.getAgentSession(id)
    upsertSession(activeSession.value)
    if (subscribe) {
      subscribeSession(id)
    }
    return activeSession.value
  }

  async function createSession(body: CreateAgentSessionRequest) {
    loading.value = true
    try {
      const session = await resourcesApi.createAgentSession(body)
      activeSession.value = session
      upsertSession(session)
      subscribeSession(session.id)
      return session
    } finally {
      loading.value = false
    }
  }

  async function rerunActive() {
    if (!activeSession.value) return null
    loading.value = true
    try {
      const session = await resourcesApi.rerunAgentSession(activeSession.value.id)
      activeSession.value = session
      upsertSession(session)
      subscribeSession(session.id)
      return session
    } finally {
      loading.value = false
    }
  }

  async function confirmAction(action: AgentAction) {
    const saved = await resourcesApi.confirmAgentAction(action.id)
    await refreshActionSession(saved)
    return saved
  }

  async function rejectAction(action: AgentAction) {
    const saved = await resourcesApi.rejectAgentAction(action.id)
    await refreshActionSession(saved)
    return saved
  }

  function subscribeSession(sessionId: string) {
    unsubscribe?.()
    realtimeEvents.value = []
    unsubscribe = subscribeTopic<AgentRealtimeEvent>(`/topic/agents/${sessionId}`, (event) => {
      realtimeEvents.value.unshift(event)
      void loadSession(sessionId, false)
    })
  }

  function stopRealtime() {
    unsubscribe?.()
    unsubscribe = null
    realtimeEvents.value = []
  }

  async function refreshActionSession(action: AgentAction) {
    if (activeSession.value?.id === action.sessionId) {
      await loadSession(action.sessionId)
    }
  }

  function upsertSession(session: AgentSession) {
    const idx = sessions.value.findIndex((item) => item.id === session.id)
    if (idx >= 0) sessions.value[idx] = { ...sessions.value[idx], ...session }
    else sessions.value.unshift(session)
  }

  return {
    sessions,
    activeSession,
    realtimeEvents,
    loading,
    pendingActions,
    latestSteps,
    loadSessions,
    loadSession,
    createSession,
    rerunActive,
    confirmAction,
    rejectAction,
    subscribeSession,
    stopRealtime,
  }
})
