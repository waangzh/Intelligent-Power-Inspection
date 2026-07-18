import { describe, expect, it } from 'vitest'
import { resolveRobotImageDetectionItems } from '@/utils/detectionStrategy'
import type { RobotInspectionImage, Route } from '@/types'

const image = {
  routeId: 'route-1',
  checkpointId: 'checkpoint-1',
} as RobotInspectionImage

describe('resolveRobotImageDetectionItems', () => {
  it('loads a cloned detection configuration from the image checkpoint', () => {
    const route = {
      id: 'route-1',
      checkpoints: [{
        id: 'checkpoint-1',
        detections: [{
          type: 'SWITCH',
          enabled: true,
          displayLabel: '刀闸开关',
          prompt: '检查刀闸操作手柄',
          threshold: 0.75,
        }],
      }],
    } as Route

    const resolved = resolveRobotImageDetectionItems(image, route)

    expect(resolved).toEqual(route.checkpoints[0].detections)
    expect(resolved).not.toBe(route.checkpoints[0].detections)
    resolved[0].prompt = '本次临时修改'
    expect(route.checkpoints[0].detections[0].prompt).toBe('检查刀闸操作手柄')
  })

  it('falls back to the five default checkpoint detections when no configuration exists', () => {
    const resolved = resolveRobotImageDetectionItems(image, undefined)

    expect(resolved.map((item) => item.type)).toEqual([
      'SWITCH',
      'METER',
      'OIL_LEAK',
      'FIRE',
      'FOREIGN_OBJECT',
    ])
    expect(resolved.every((item) => item.enabled && item.displayLabel && item.prompt)).toBe(true)
  })
})
