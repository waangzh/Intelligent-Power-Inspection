import { describe, expect, it } from 'vitest'
import { parseYaml } from './rosMap'

describe('parseYaml', () => {
  it('支持 origin 多行列表中的空行', () => {
    expect(parseYaml(`
image: floor.pgm
resolution: 0.05
origin:
  - -2.89

  - -6.37
  - 0
`)).toMatchObject({
      image: 'floor.pgm',
      resolution: 0.05,
      origin: [-2.89, -6.37, 0],
    })
  })

  it('不把含非法值或缺少 yaw 的 origin 规范化为另一组坐标', () => {
    expect(parseYaml('origin: [1, invalid, 3]')).not.toHaveProperty('origin')
    expect(parseYaml('origin: [1, 2]')).not.toHaveProperty('origin')
  })
})
