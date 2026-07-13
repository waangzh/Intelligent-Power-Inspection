import type { EditableKeepoutZone, EditableRouteDraft, EditableTarget, MapAssetIdentity, RouteExecutorDocument, RouteExecutorDocumentV3 } from '@/types/routeExecutor'
import { createEditableRouteDraft, toEditableRouteDraft } from '@/utils/route/editableRoute'
import { mergeManagedRouteFields } from '@/utils/route/templateMerge'

export type RouteExecutorTarget = EditableTarget
export interface RouteFormState {
  startName: string; startX: number; startY: number; startYaw: number; publishInitialPose: boolean; covX: number; covY: number; covYaw: number
  routeId: string; activeRouteId: string; routeName: string; goalTimeout: number; maxRetries: number
  failurePolicy: 'abort_and_return_home' | 'abort'; returnToStart: boolean; loopEnabled: boolean; loopWait: number; maxCycles: number
}

export function createDefaultRouteForm(routeId = 'route_patrol_001'): RouteFormState {
  return formFromDraft(createEditableRouteDraft({ defaultRouteId: routeId }))
}

export function loadRouteJson(route: RouteExecutorDocument, form: RouteFormState): { targets: RouteExecutorTarget[]; keepoutZones: EditableKeepoutZone[]; nextTargetNo: number } {
  const draft = toEditableRouteDraft(route, { defaultRouteId: form.routeId })
  Object.assign(form, formFromDraft(draft))
  const maxNo = draft.targets.reduce((max, target) => Math.max(max, Number(target.id.match(/target_(\d+)/)?.[1] || 0)), draft.targets.length)
  return { targets: draft.targets, keepoutZones: draft.keepoutZones, nextTargetNo: maxNo + 1 }
}

export function draftFromForm(
  form: RouteFormState,
  targets: RouteExecutorTarget[],
  keepoutZones: EditableKeepoutZone[],
  sourceTemplate: RouteExecutorDocument | null = null,
): EditableRouteDraft {
  return {
    sourceTemplate, requiresConversion: sourceTemplate?.version === 2,
    start: { name: form.startName, x: form.startX, y: form.startY, yaw: form.startYaw, publishInitialPose: form.publishInitialPose, covX: form.covX, covY: form.covY, covYaw: form.covYaw },
    targets, keepoutZones,
    route: { id: form.routeId, name: form.routeName, goalTimeout: form.goalTimeout, maxRetries: form.maxRetries, failurePolicy: form.failurePolicy, returnToStart: form.returnToStart, loopEnabled: form.loopEnabled, loopWait: form.loopWait, maxCycles: form.maxCycles },
  }
}

export function buildRouteJson(
  form: RouteFormState,
  targets: RouteExecutorTarget[],
  keepoutZones: EditableKeepoutZone[],
  mapAsset: MapAssetIdentity,
  sourceTemplate: RouteExecutorDocument | null = null,
): RouteExecutorDocumentV3 {
  const resolvedMap: MapAssetIdentity = mapAsset.image_sha256 ? mapAsset : sourceTemplate?.version === 3 ? sourceTemplate.map : {
    yaml: '', image: '', resolution: 0, origin: [0, 0, 0], width: 0, height: 0, image_sha256: '0'.repeat(64),
  }
  return mergeManagedRouteFields(sourceTemplate, draftFromForm(form, targets, keepoutZones, sourceTemplate), resolvedMap)
}

export function withPlatformRouteName(doc: RouteExecutorDocument, routeName: string): RouteExecutorDocument {
  const normalizedName = routeName.trim()
  if (!normalizedName || !doc.routes[0] || doc.routes[0].name === normalizedName) return doc
  return {
    ...doc,
    routes: [{ ...doc.routes[0], name: normalizedName }],
  }
}

export function downloadRouteJson(doc: RouteExecutorDocument, filename?: string) {
  const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob); const link = document.createElement('a')
  link.href = url; link.download = filename || `${doc.routes[0]?.id || 'route_patrol_001'}.json`; link.click(); URL.revokeObjectURL(url)
}

function formFromDraft(draft: EditableRouteDraft): RouteFormState {
  return {
    startName: draft.start.name, startX: draft.start.x, startY: draft.start.y, startYaw: draft.start.yaw, publishInitialPose: draft.start.publishInitialPose, covX: draft.start.covX, covY: draft.start.covY, covYaw: draft.start.covYaw,
    routeId: draft.route.id, activeRouteId: draft.route.id, routeName: draft.route.name, goalTimeout: draft.route.goalTimeout, maxRetries: draft.route.maxRetries, failurePolicy: draft.route.failurePolicy, returnToStart: draft.route.returnToStart, loopEnabled: draft.route.loopEnabled, loopWait: draft.route.loopWait, maxCycles: draft.route.maxCycles,
  }
}
