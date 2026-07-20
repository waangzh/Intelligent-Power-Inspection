import { describe, expect, it } from 'vitest'
import { calculateAspectFillView, calculateAspectFitView } from './useRosMapRouteEditor'

describe('calculateAspectFitView', () => {
  it('使用统一缩放比例，并将纵向地图置于容器中央', () => {
    const view = calculateAspectFitView(1200, 790, 391, 649)

    expect(view.scale).toBeCloseTo((790 / 649) * 0.96)
    expect(view.offsetX).toBeGreaterThan(0)
    expect(view.offsetY).toBeGreaterThan(0)
    expect(391 * view.scale).toBeLessThan(1200)
    expect(649 * view.scale).toBeLessThan(790)
  })

  it('铺满模式仍保持统一比例，仅裁剪超出画布的边缘', () => {
    const view = calculateAspectFillView(1200, 790, 391, 649)

    expect(view.scale).toBeCloseTo(1200 / 391)
    expect(391 * view.scale).toBeCloseTo(1200)
    expect(649 * view.scale).toBeGreaterThan(790)
    expect(view.offsetY).toBeLessThan(0)
  })
})
