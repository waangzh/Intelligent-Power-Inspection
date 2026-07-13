import { describe, expect, it, vi } from 'vitest'
import type { RouteDraftValidationReport, RouteExecutorDocument } from '@/types/routeExecutor'
import { validateThenSaveRouteDraft } from './draftValidation'

const clientDraft = { version: 3 } as RouteExecutorDocument
const normalizedDraft = { version: 3, map: { yaml: 'server.yaml' } } as unknown as RouteExecutorDocument

function report(issues: RouteDraftValidationReport['issues']): RouteDraftValidationReport {
  return {
    valid: !issues.some((issue) => issue.severity === 'ERROR'),
    issues,
    normalizedExecutorJson: normalizedDraft,
    mapAssetId: 'map_1',
    mapImageSha256: 'a'.repeat(64),
  }
}

describe('路线草稿后端校验闭环', () => {
  it('后端 ERROR 阻止保存', async () => {
    const save = vi.fn(async () => undefined)
    const result = await validateThenSaveRouteDraft(
      async () => report([{ code: 'INVALID_POSE', jsonPointer: '/targets/0/pose/x', message: '坐标无效', severity: 'ERROR' }]),
      save,
    )
    expect(result.valid).toBe(false)
    expect(save).not.toHaveBeenCalled()
  })

  it('WARNING 可见且允许保存草稿', async () => {
    const save = vi.fn(async () => undefined)
    const result = await validateThenSaveRouteDraft(
      async () => report([{ code: 'NEAR_KEEP_OUT', jsonPointer: '/targets/0', message: '接近禁行区', severity: 'WARNING' }]),
      save,
    )
    expect(result.issues).toHaveLength(1)
    expect(result.issues[0].severity).toBe('WARNING')
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('使用服务端 normalizedExecutorJson 保存，而不是客户端草稿', async () => {
    const save = vi.fn(async () => undefined)
    await validateThenSaveRouteDraft(async () => report([]), save)
    expect(save).toHaveBeenCalledWith(normalizedDraft)
    expect(save).not.toHaveBeenCalledWith(clientDraft)
  })
})
