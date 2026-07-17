import { describe, expect, it } from 'vitest'
import type { MapAsset } from '@/types/mapAsset'
import { availableMapAssets, buildMapAssetQuery, shortMapHash } from './mapAssetReview'

const base: MapAsset = {
  id: 'map-1', siteId: 'site-1', status: 'AVAILABLE', source: 'ROBOT', yamlName: 'map.yaml', pgmName: 'map.pgm',
  image: 'map.pgm', resolution: 0.05, origin: [0, 0, 0], negate: 0, width: 2, height: 1,
  yamlSize: 10, pgmSize: 14, yamlSha256: 'a'.repeat(64), pgmSha256: 'b'.repeat(64),
  createdAt: '2026-07-17T00:00:00Z', updatedAt: '2026-07-17T00:00:00Z',
}

describe('robot map review helpers', () => {
  it('builds the management query without leaking extra fields', () => {
    expect(buildMapAssetQuery({ source: 'ROBOT', status: 'PENDING_REVIEW', siteId: 'site 1' }))
      .toBe('?source=ROBOT&status=PENDING_REVIEW&siteId=site+1')
  })

  it('only exposes AVAILABLE assets from the selected site to route planning', () => {
    const pending = { ...base, id: 'pending', status: 'PENDING_REVIEW' as const }
    const rejected = { ...base, id: 'rejected', status: 'REJECTED' as const }
    const otherSite = { ...base, id: 'other', siteId: 'site-2' }
    expect(availableMapAssets([pending, rejected, otherSite, base], 'site-1').map(asset => asset.id)).toEqual(['map-1'])
  })

  it('formats a compact audit hash', () => {
    expect(shortMapHash('1234567890abcdef')).toBe('12345678…cdef')
  })
})
