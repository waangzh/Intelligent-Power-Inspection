import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Checkpoint, DetectionItem, DetectionType, Route } from '@/types'
import { CHECKPOINT_DETECTIONS, ROUTE_DETECTIONS } from '@/types'
import type { RouteExecutorDocument } from '@/types/routeExecutor'
import { withPlatformRouteName } from '@/utils/routeExecutorJson'
import { uid } from '@/utils/storage'

function defaultDetectionItems(types: DetectionType[]): DetectionItem[] {
  return types.map((type) => ({
    type,
    enabled: true,
    threshold: 0.75,
    prompt:
      type === 'SWITCH'
        ? '红色刀闸开关'
        : type === 'OIL_LEAK'
          ? '设备底部渗油区域'
          : type === 'METER'
            ? '压力表读数区域'
            : undefined,
  }))
}

export const useRouteStore = defineStore('route', () => {
  const routes = ref<Route[]>([])

  async function load() {
    routes.value = await resourcesApi.listRoutes()
  }

  async function createRoute(siteId: string, name: string, description = '') {
    const route: Route = {
      id: uid('route'),
      siteId,
      name,
      description,
      path: [],
      routeDetections: defaultDetectionItems(ROUTE_DETECTIONS),
      checkpoints: [],
      mapMode: '2d',
      createdAt: new Date().toISOString(),
    }
    routes.value.push(route)
    const saved = await resourcesApi.createRoute(route)
    updateLocalRoute(saved)
    return saved
  }

  async function updateRoute(id: string, patch: Partial<Route>) {
    const idx = routes.value.findIndex((r) => r.id === id)
    if (idx < 0) return
    routes.value[idx] = { ...routes.value[idx], ...patch }
    const saved = await resourcesApi.updateRoute(id, patch)
    updateLocalRoute(saved)
    return saved
  }

  async function removeRoute(id: string) {
    routes.value = routes.value.filter((r) => r.id !== id)
    await resourcesApi.removeRoute(id)
  }

  function addCheckpoint(routeId: string, checkpoint: Omit<Checkpoint, 'id' | 'routeId' | 'seq' | 'detections'>) {
    const route = routes.value.find((r) => r.id === routeId)
    if (!route) return null
    const cp: Checkpoint = {
      ...checkpoint,
      id: uid('cp'),
      routeId,
      seq: route.checkpoints.length + 1,
      detections: defaultDetectionItems(CHECKPOINT_DETECTIONS),
    }
    route.checkpoints.push(cp)
    void resourcesApi.addCheckpoint(routeId, cp).then((saved) => updateLocalCheckpoint(routeId, saved))
    return cp
  }

  function updateCheckpoint(routeId: string, checkpointId: string, patch: Partial<Checkpoint>) {
    const route = routes.value.find((r) => r.id === routeId)
    if (!route) return
    const idx = route.checkpoints.findIndex((c) => c.id === checkpointId)
    if (idx >= 0) {
      route.checkpoints[idx] = { ...route.checkpoints[idx], ...patch }
      void resourcesApi.updateCheckpoint(routeId, checkpointId, patch).then((saved) => updateLocalCheckpoint(routeId, saved))
    }
  }

  function removeCheckpoint(routeId: string, checkpointId: string) {
    const route = routes.value.find((r) => r.id === routeId)
    if (!route) return
    route.checkpoints = route.checkpoints
      .filter((c) => c.id !== checkpointId)
      .map((c, i) => ({ ...c, seq: i + 1 }))
    void resourcesApi.removeCheckpoint(routeId, checkpointId)
  }

  function checkpointsFromExecutor(routeId: string, doc: RouteExecutorDocument): Checkpoint[] {
    const orderedIds = doc.routes[0]?.target_ids?.length
      ? doc.routes[0].target_ids
      : doc.targets.map((t) => t.id)
    const byId = new Map(doc.targets.map((t) => [t.id, t]))
    return orderedIds
      .map((id) => byId.get(id))
      .filter(Boolean)
      .map((target, index) => ({
        id: target!.id,
        routeId,
        name: target!.name,
        seq: index + 1,
        position: { lat: target!.pose.y, lng: target!.pose.x, x: target!.pose.x, y: target!.pose.y },
        pan: Math.round(((target!.pose.yaw || 0) * 180) / Math.PI),
        tilt: -15,
        dwellSeconds: target!.task_duration_sec ?? 5,
        detections: defaultDetectionItems(CHECKPOINT_DETECTIONS),
      }))
  }

  async function saveExecutorRoute(routeId: string, doc: RouteExecutorDocument) {
    const route = routes.value.find((r) => r.id === routeId)
    const platformName = route?.name?.trim() || doc.routes[0]?.name || doc.active_route_id
    const executorJson = withPlatformRouteName(doc, platformName)
    const checkpoints = checkpointsFromExecutor(routeId, executorJson)
    const path = checkpoints.map((cp) => cp.position)
    return updateRoute(routeId, {
      name: platformName,
      executorJson,
      checkpoints,
      path,
      mapMode: '2d',
    })
  }

  function getRouteById(id: string) {
    return routes.value.find((r) => r.id === id)
  }

  function getRoutesBySite(siteId: string) {
    return routes.value.filter((r) => r.siteId === siteId)
  }

  function updateLocalRoute(route: Route) {
    const idx = routes.value.findIndex((r) => r.id === route.id)
    if (idx >= 0) routes.value[idx] = route
  }

  function updateLocalCheckpoint(routeId: string, checkpoint: Checkpoint) {
    const route = routes.value.find((r) => r.id === routeId)
    const idx = route?.checkpoints.findIndex((c) => c.id === checkpoint.id) ?? -1
    if (route && idx >= 0) route.checkpoints[idx] = checkpoint
  }

  return {
    routes,
    load,
    createRoute,
    updateRoute,
    removeRoute,
    addCheckpoint,
    updateCheckpoint,
    removeCheckpoint,
    saveExecutorRoute,
    getRouteById,
    getRoutesBySite,
  }
})
