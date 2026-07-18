import { defineStore } from 'pinia'
import { shallowRef } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Checkpoint, DetectionItem, DetectionType, Route } from '@/types'
import { CHECKPOINT_DETECTIONS, DETECTION_TARGET_LABELS, ROUTE_DETECTIONS } from '@/types'
import type { RouteExecutorDocument } from '@/types/routeExecutor'
import type { ListQuery } from '@/types/pagination'
import { withPlatformRouteName } from '@/utils/routeExecutorJson'
import { uid } from '@/utils/storage'

function defaultDetectionItems(types: DetectionType[]): DetectionItem[] {
  return types.map((type) => ({
    type,
    enabled: true,
    displayLabel: DETECTION_TARGET_LABELS[type],
    threshold: 0.75,
    prompt:
      type === 'SWITCH'
        ? '变电设备上的刀闸开关操作手柄、连杆及触头区域'
        : type === 'METER'
          ? '圆形机械压力表的完整表盘和指针区域'
          : type === 'OIL_LEAK'
            ? '变压器或电气设备表面、法兰、阀门、接口及底部可见的油渍、油迹或积油区域'
            : type === 'FIRE'
              ? '图像中清晰可见的火焰、火光或明显烟雾区域'
              : type === 'FOREIGN_OBJECT'
                ? '设备操作区域内不属于设备本体的遗留物，例如工具、纸箱、塑料袋、布料或其他杂物'
                : undefined,
  }))
}

export const useRouteStore = defineStore('route', () => {
  const routes = shallowRef<Route[]>([])
  const total = shallowRef(0)

  async function load(siteId?: string, query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listRoutes({ ...query, siteId })
    routes.value = result.items
    total.value = result.total
  }

  async function loadOne(id: string) {
    const route = await resourcesApi.getRoute(id)
    updateLocalRoute(route)
    return route
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
    const saved = await resourcesApi.createRoute(route)
    routes.value = [...routes.value, saved]
    return saved
  }

  async function updateRoute(id: string, patch: Partial<Route>) {
    const saved = await resourcesApi.updateRoute(id, patch)
    updateLocalRoute(saved)
    return saved
  }

  async function removeRoute(id: string) {
    await resourcesApi.removeRoute(id)
    routes.value = routes.value.filter((r) => r.id !== id)
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
    updateLocalRoute({ ...route, checkpoints: [...route.checkpoints, cp] })
    void resourcesApi.addCheckpoint(routeId, cp).then((saved) => updateLocalCheckpoint(routeId, saved))
    return cp
  }

  async function updateCheckpoint(routeId: string, checkpointId: string, patch: Partial<Checkpoint>) {
    const route = routes.value.find((r) => r.id === routeId)
    if (!route) throw new Error('路线不存在')
    const idx = route.checkpoints.findIndex((c) => c.id === checkpointId)
    if (idx >= 0) {
      const saved = await resourcesApi.updateCheckpoint(routeId, checkpointId, patch)
      updateLocalCheckpoint(routeId, saved)
      return saved
    }
    throw new Error('检查点不存在')
  }

  function removeCheckpoint(routeId: string, checkpointId: string) {
    const route = routes.value.find((r) => r.id === routeId)
    if (!route) return
    const checkpoints = route.checkpoints
      .filter((c) => c.id !== checkpointId)
      .map((c, i) => ({ ...c, seq: i + 1 }))
    updateLocalRoute({ ...route, checkpoints })
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

  async function saveExecutorRoute(routeId: string, doc: RouteExecutorDocument, mapId?: string) {
    if (doc.routes.length !== 1) throw new Error('路线执行 JSON 必须且只能包含一条 route。')
    const route = routes.value.find((r) => r.id === routeId)
    const platformName = doc.routes[0]?.name?.trim() || route?.name?.trim() || doc.active_route_id
    const executorJson = withPlatformRouteName(doc, platformName)
    const checkpoints = checkpointsFromExecutor(routeId, executorJson)
    const path = checkpoints.map((cp) => cp.position)
    const patch: Partial<Route> = {
      name: platformName,
      executorJson,
      checkpoints,
      path,
      mapMode: '2d',
    }
    if (mapId) patch.mapId = mapId
    return updateRoute(routeId, patch)
  }

  function getRouteById(id: string) {
    return routes.value.find((r) => r.id === id)
  }

  function getRoutesBySite(siteId: string) {
    return routes.value.filter((r) => r.siteId === siteId)
  }

  function updateLocalRoute(route: Route) {
    const idx = routes.value.findIndex((r) => r.id === route.id)
    if (idx >= 0) routes.value = routes.value.map((item) => (item.id === route.id ? route : item))
    else routes.value = [route, ...routes.value]
  }

  function updateLocalCheckpoint(routeId: string, checkpoint: Checkpoint) {
    const route = routes.value.find((r) => r.id === routeId)
    if (!route || !route.checkpoints.some((item) => item.id === checkpoint.id)) return
    updateLocalRoute({
      ...route,
      checkpoints: route.checkpoints.map((item) => (item.id === checkpoint.id ? checkpoint : item)),
    })
  }

  return {
    routes,
    total,
    load,
    loadOne,
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
