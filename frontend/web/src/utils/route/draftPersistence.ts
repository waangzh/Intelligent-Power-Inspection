import type { PersistedRouteDraftReport, RouteExecutorDocument } from '@/types/routeExecutor'

export type DraftSaveState = 'unsaved' | 'saving' | 'saved' | 'failed'

export function serializeRouteDocument(document: RouteExecutorDocument | null): string | null {
  return document ? JSON.stringify(sortJson(document)) : null
}

function sortJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortJson)
  if (value && typeof value === 'object') {
    return Object.keys(value)
      .sort()
      .reduce<Record<string, unknown>>((result, key) => {
        result[key] = sortJson((value as Record<string, unknown>)[key])
        return result
      }, {})
  }
  return value
}

export function restoreRouteDraft(
  report: PersistedRouteDraftReport,
  fallback: RouteExecutorDocument | null,
): { document: RouteExecutorDocument | null; state: DraftSaveState; persistedDocument: string | null } {
  const document = report.normalizedExecutorJson ?? fallback
  return {
    document,
    state: report.draft ? 'saved' : 'unsaved',
    persistedDocument: serializeRouteDocument(document),
  }
}

export function applySavedRouteDraft(report: PersistedRouteDraftReport): RouteExecutorDocument {
  if (!report.normalizedExecutorJson) throw new Error('服务端未返回规范化草稿')
  return report.normalizedExecutorJson
}

export function keepLocalDraftAfterSaveFailure(document: RouteExecutorDocument | null): RouteExecutorDocument | null {
  return document
}

export function routePublishBlockReason(
  hasRoute: boolean,
  hasUnsavedChanges: boolean,
  draft: PersistedRouteDraftReport | null,
): string {
  if (!hasRoute) return '请先选择路线'
  if (hasUnsavedChanges) return '存在未保存的本地编辑'
  if (!draft?.draft) return '草稿尚未成功保存'
  if (!draft.publishable) return '草稿存在 ERROR 或地图身份不一致'
  return ''
}
