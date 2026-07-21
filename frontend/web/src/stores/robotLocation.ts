import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type {
  RobotLocation,
  RobotLocationQuery,
  RobotTrackPoint,
  RobotTrackQuery,
} from '@/types/robotLocation'

const POLL_MS = 3000

export const useRobotLocationStore = defineStore('robotLocation', () => {
  const byRobotId = ref<Map<string, RobotLocation>>(new Map())
  const trackByRobotId = ref<Map<string, RobotTrackPoint[]>>(new Map())
  const loadFailed = ref(false)
  const polling = ref(false)

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let pollQuery: RobotLocationQuery = {}
  let refreshInFlight: Promise<void> | null = null
  let trackInFlight = new Map<string, Promise<RobotTrackPoint[]>>()

  const locations = computed(() => [...byRobotId.value.values()])

  function setLocation(location: RobotLocation) {
    byRobotId.value.set(location.robotId, location)
    byRobotId.value = new Map(byRobotId.value)
  }

  function getLocation(robotId: string): RobotLocation | undefined {
    return byRobotId.value.get(robotId)
  }

  async function refreshLocation(robotId: string) {
    loadFailed.value = false
    try {
      const location = await resourcesApi.getRobotLocation(robotId)
      setLocation(location)
      return location
    } catch {
      loadFailed.value = true
      return undefined
    }
  }

  async function refreshLocations(query: RobotLocationQuery = {}) {
    if (refreshInFlight) return refreshInFlight
    loadFailed.value = false
    refreshInFlight = (async () => {
      try {
        const items = await resourcesApi.listRobotLocations(query)
        const next = new Map(byRobotId.value)
        items.forEach((item) => next.set(item.robotId, item))
        byRobotId.value = next
      } catch {
        loadFailed.value = true
      } finally {
        refreshInFlight = null
      }
    })()
    return refreshInFlight
  }

  async function fetchTrack(robotId: string, query: RobotTrackQuery = {}) {
    const existing = trackInFlight.get(robotId)
    if (existing) return existing
    const task = (async () => {
      try {
        const response = await resourcesApi.getRobotTrack(robotId, query)
        trackByRobotId.value.set(robotId, response.points)
        trackByRobotId.value = new Map(trackByRobotId.value)
        return response.points
      } finally {
        trackInFlight.delete(robotId)
      }
    })()
    trackInFlight.set(robotId, task)
    return task
  }

  function getTrack(robotId: string): RobotTrackPoint[] {
    return trackByRobotId.value.get(robotId) ?? []
  }

  function clearTrack(robotId: string) {
    trackByRobotId.value.delete(robotId)
    trackByRobotId.value = new Map(trackByRobotId.value)
  }

  function startRobotPolling(robotId: string) {
    stopPolling()
    polling.value = true
    void refreshLocation(robotId)
    pollTimer = setInterval(() => {
      void refreshLocation(robotId)
    }, POLL_MS)
  }

  function startPolling(query: RobotLocationQuery = {}) {
    pollQuery = { ...query }
    stopPolling()
    polling.value = true
    void refreshLocations(pollQuery)
    pollTimer = setInterval(() => {
      void refreshLocations(pollQuery)
    }, POLL_MS)
  }

  function updatePollingQuery(query: RobotLocationQuery) {
    pollQuery = { ...pollQuery, ...query }
    if (polling.value) {
      void refreshLocations(pollQuery)
    }
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    polling.value = false
  }

  return {
    byRobotId,
    locations,
    loadFailed,
    polling,
    getLocation,
    refreshLocation,
    refreshLocations,
    fetchTrack,
    getTrack,
    clearTrack,
    startPolling,
    startRobotPolling,
    updatePollingQuery,
    stopPolling,
  }
})
