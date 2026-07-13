import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import type { MapAssetIdentity, RouteExecutorDocumentV3 } from '@/types/routeExecutor'
import { toEditableRouteDraft } from './editableRoute'
import { RouteDocumentParseError, parseRouteDocument } from './parseRouteDocument'
import { mergeManagedRouteFields } from './templateMerge'
import { validateRouteDocument } from './validation'

function fixture(group: 'valid' | 'invalid', name: string): unknown {
  const path = fileURLToPath(new URL(`../../../../../contracts/route-v3/${group}/${name}`, import.meta.url))
  return JSON.parse(readFileSync(path, 'utf8')) as unknown
}

describe('路线 v3 合同', () => {
  it('接受共享的合法样本', () => {
    for (const name of ['minimal-v3.json', 'extensions-v3.json', 'keepout-v3.json']) {
      const document = parseRouteDocument(fixture('valid', name)) as RouteExecutorDocumentV3
      expect(validateRouteDocument(document).valid, name).toBe(true)
    }
  })

  it('保留模板扩展字段并同步受管位置字段', () => {
    const source = parseRouteDocument(fixture('valid', 'extensions-v3.json')) as RouteExecutorDocumentV3
    const before = JSON.stringify(source)
    const draft = toEditableRouteDraft(source)
    draft.targets[0].x = 8
    const merged = mergeManagedRouteFields(source, draft, source.map)

    expect(JSON.stringify(source)).toBe(before)
    expect(merged.site).toEqual({ code: 'substation-a' })
    expect(merged.map.vendor_extension).toBe('preserve')
    expect(merged.targets[0].aliases).toEqual(['A'])
    expect(merged.targets[0].pose.x).toBe(8)
    expect(merged.targets[0].location.x).toBe(8)
  })

  it('将 v2 作为显式待转换草稿导入', () => {
    const source = parseRouteDocument(fixture('valid', 'imported-v2.json'))
    const draft = toEditableRouteDraft(source)
    const mapAsset = parseRouteDocument(fixture('valid', 'minimal-v3.json')) as RouteExecutorDocumentV3
    const converted = mergeManagedRouteFields(source, draft, mapAsset.map as MapAssetIdentity)

    expect(draft.requiresConversion).toBe(true)
    expect(converted.version).toBe(3)
    expect(converted.targets[0].aliases).toEqual(['A'])
    expect(validateRouteDocument(converted).valid).toBe(true)
  })

  it('按编辑目标顺序重建引用，并仅删除明确移除的模板目标', () => {
    const source = parseRouteDocument(fixture('valid', 'extensions-v3.json')) as RouteExecutorDocumentV3
    const draft = toEditableRouteDraft(source)
    draft.targets.push({ id: 'target_002', name: 'target 2', x: 3, y: 3, yaw: 0, taskDuration: 0 })
    draft.targets = [draft.targets[1], draft.targets[0]]
    const reordered = mergeManagedRouteFields(source, draft, source.map)
    expect(reordered.routes[0].target_ids).toEqual(['target_002', 'target_001'])
    expect(reordered.targets[1].aliases).toEqual(['A'])
    expect(reordered.targets[0].aliases).toBeUndefined()

    draft.targets = [draft.targets[0]]
    const deleted = mergeManagedRouteFields(source, draft, source.map)
    expect(deleted.targets.map((target) => target.id)).toEqual(['target_002'])
  })

  it.each([
    ['invalid-map-sha.json', 'INVALID_MAP_SHA256'],
    ['invalid-location.json', 'INVALID_LOCATION'],
    ['duplicate-target-id.json', 'DUPLICATE_TARGET_ID'],
    ['unknown-target-reference.json', 'UNKNOWN_TARGET_REFERENCE'],
    ['self-intersecting-polygon.json', 'SELF_INTERSECTING_POLYGON'],
    ['zero-area-polygon.json', 'ZERO_AREA_POLYGON'],
    ['invalid-mask-padding.json', 'INVALID_MASK_PADDING'],
  ])('拒绝 %s', (name, expectedCode) => {
    const document = parseRouteDocument(fixture('invalid', name)) as RouteExecutorDocumentV3
    expect(validateRouteDocument(document).issues.map((issue) => issue.code)).toContain(expectedCode)
  })

  it.each([
    ['multiple-routes.json', 'UNSUPPORTED_ROUTE_COUNT'],
    ['non-empty-schedules.json', 'UNSUPPORTED_SCHEDULES'],
  ])('在导入阶段拒绝 %s', (name, expectedCode) => {
    expect(() => parseRouteDocument(fixture('invalid', name))).toThrowError(RouteDocumentParseError)
    try {
      parseRouteDocument(fixture('invalid', name))
    } catch (error) {
      expect((error as RouteDocumentParseError).code).toBe(expectedCode)
    }
  })
})
