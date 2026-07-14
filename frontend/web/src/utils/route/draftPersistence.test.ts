import { describe, expect, it } from 'vitest'
import type { PersistedRouteDraftReport, RouteExecutorDocument } from '@/types/routeExecutor'
import {
  applySavedRouteDraft,
  keepLocalDraftAfterSaveFailure,
  restoreRouteDraft,
  routePublishBlockReason,
  serializeRouteDocument,
} from './draftPersistence'

const normalized = { version: 3, map: { yaml: 'server.yaml' } } as unknown as RouteExecutorDocument

function report(overrides: Partial<PersistedRouteDraftReport> = {}): PersistedRouteDraftReport {
  return {
    normalizedExecutorJson: normalized,
    valid: true,
    issues: [],
    checkedAt: '2026-07-13T12:00:00Z',
    publishable: true,
    mapAssetId: 'map_1',
    mapImageSha256: 'a'.repeat(64),
    draft: { version: 2, updatedAt: '2026-07-13T12:00:00Z', publishable: true },
    ...overrides,
  }
}

describe('路线草稿持久化编辑状态', () => {
  it('加载时恢复服务端草稿及其版本', () => {
    const restored = restoreRouteDraft(report(), null)
    expect(restored.document).toBe(normalized)
    expect(restored.state).toBe('saved')
    expect(restored.persistedDocument).toBe(serializeRouteDocument(normalized))
  })

  it('忽略对象字段顺序，避免规范化回填被误判为本地编辑', () => {
    const reordered = { map: { yaml: 'server.yaml' }, version: 3 } as unknown as RouteExecutorDocument
    expect(serializeRouteDocument(normalized)).toBe(serializeRouteDocument(reordered))
  })

  it('服务端规范化结果回写编辑器，保存失败不会丢失本地内容', () => {
    expect(applySavedRouteDraft(report())).toBe(normalized)
    const local = { version: 3, local: true } as unknown as RouteExecutorDocument
    expect(keepLocalDraftAfterSaveFailure(local)).toBe(local)
  })

  it('ERROR 或未保存编辑会禁用发布，WARNING 可保存且允许发布', () => {
    expect(routePublishBlockReason(true, false, report({ publishable: false, valid: false }))).toContain('ERROR')
    expect(routePublishBlockReason(true, true, report())).toContain('未保存')
    expect(routePublishBlockReason(true, false, report({ issues: [{ code: 'EMPTY_TARGETS', jsonPointer: '/targets', message: '提示', severity: 'WARNING' }] }))).toBe('')
  })
})
