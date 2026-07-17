import { describe, expect, it } from 'vitest'
import { createDefaultMapState } from '@/utils/rosMap'
import { calculateRouteSafety, classifyFootprint, validateAnnotationExport } from './routeSafety'

function freeMap() {
  return {
    ...createDefaultMapState(),
    width: 10,
    height: 10,
    resolution: 1,
    origin: [0, 0, 0] as [number, number, number],
    yamlName: 'floor.yaml',
    pgmName: 'floor.pgm',
    image: 'floor.pgm',
    pixels: new Uint8Array(100).fill(255),
  }
}

describe('routeSafety', () => {
  it('识别空闲、占用与禁行区的车体轮廓状态', () => {
    const map = freeMap()
    expect(classifyFootprint(map, [], { x: 5, y: 5, yaw: 0 })).toBe('free')
    map.pixels[55] = 0
    expect(classifyFootprint(map, [], { x: 5, y: 5, yaw: 0 })).toBe('occupied')
    map.pixels[55] = 255
    const zones = [{ id: 'zone_001', name: '禁行区1', type: 'hard_keepout' as const, enabled: true, maskPaddingM: 1, polygon: [{ x: 4.5, y: 4.5 }, { x: 5.5, y: 4.5 }, { x: 5.5, y: 5.5 }, { x: 4.5, y: 5.5 }] }]
    expect(classifyFootprint(map, zones, { x: 5, y: 5, yaw: 0 })).toBe('keepout')
  })

  it('不把仅与车体边缘延长线相交的远端禁行区误判为碰撞', () => {
    const map = freeMap()
    const zones = [{
      id: 'zone_001',
      name: '远端禁行区',
      type: 'hard_keepout' as const,
      enabled: true,
      maskPaddingM: 1,
      polygon: [{ x: 7, y: 4 }, { x: 8, y: 4 }, { x: 8, y: 6 }, { x: 7, y: 6 }],
    }]

    expect(classifyFootprint(map, zones, { x: 5, y: 5, yaw: 0 })).toBe('free')
  })

  it('将不安全点位和不完整禁行区拦截为不可导出', () => {
    const map = freeMap()
    const points = [{ id: 'start_pose', label: '起点', x: 5, y: 5, yaw: 0 }]
    const zones = [{ id: 'zone_001', name: '禁行区1', type: 'hard_keepout' as const, enabled: true, maskPaddingM: 1, polygon: [{ x: 1, y: 1 }, { x: 2, y: 2 }] }]
    const issues = validateAnnotationExport(map, 'a'.repeat(64), zones, points)
    expect(issues).toContain('禁行区1 至少需要 3 个顶点，当前只有 2 个。')
    map.pixels[55] = 0
    const safety = calculateRouteSafety(map, [], points)
    expect(safety.validation_status).toBe('unsafe')
    expect(safety.warnings[0]).toContain('障碍区')
  })
})
