import { describe, expect, it } from 'vitest'
import * as detectionStrategy from '@/utils/detectionStrategy'
import { cloneDetectionItems, resolveRobotImageDetectionItems } from '@/utils/detectionStrategy'
import type { DetectionItem, RobotInspectionImage, Route } from '@/types'

const image = {
  routeId: 'route-1',
  checkpointId: 'checkpoint-1',
} as RobotInspectionImage

describe('cloneDetectionItems', () => {
  it('upgrades legacy preset items with fields required by template saving', () => {
    const cloned = cloneDetectionItems([{
      type: 'SWITCH',
      enabled: true,
      displayLabel: '',
      threshold: 0.75,
    }])

    expect(cloned[0]).toMatchObject({
      itemId: 'SWITCH',
      name: '开关/刀闸状态',
      displayLabel: '刀闸开关',
      prompt: '变电设备上的刀闸开关操作手柄、连杆及触头区域',
      alarmEnabled: false,
      alarmOnFinding: false,
      alarmSeverity: 'MEDIUM',
      alarmMessage: '',
    })
  })

  it('preserves all configured alarm fields when cloning template items', () => {
    const cloned = cloneDetectionItems([{
      type: 'FIRE',
      enabled: true,
      displayLabel: '火焰',
      threshold: 0.75,
      alarmEnabled: true,
      alarmOnFinding: true,
      alarmSeverity: 'CRITICAL',
      alarmMessage: '发现明火，请立即处置',
    }])

    expect(cloned[0]).toMatchObject({
      alarmEnabled: true,
      alarmOnFinding: true,
      alarmSeverity: 'CRITICAL',
      alarmMessage: '发现明火，请立即处置',
    })
  })

  it('rejects an invalid alarm severity from runtime data', () => {
    expect(() => cloneDetectionItems([{
      type: 'FIRE',
      enabled: true,
      displayLabel: '火焰',
      threshold: 0.75,
      alarmSeverity: 'URGENT',
    } as unknown as DetectionItem])).toThrow('非法告警级别')
  })
})

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

    expect(resolved[0]).toMatchObject(route.checkpoints[0].detections[0])
    expect(resolved[0]).toMatchObject({ itemId: 'SWITCH', name: '开关/刀闸状态' })
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
    expect(resolved.every((item) => (
      item.alarmEnabled === false
      && item.alarmOnFinding === false
      && item.alarmSeverity === 'MEDIUM'
      && item.alarmMessage === ''
    ))).toBe(true)
  })
})

describe('formatDetectionElapsed', () => {
  const formatDetectionElapsed = (detectionStrategy as unknown as Record<string, unknown>).formatDetectionElapsed

  it('shows elapsed seconds while detection is running', () => {
    expect(formatDetectionElapsed).toBeTypeOf('function')
    if (typeof formatDetectionElapsed !== 'function') return

    expect(formatDetectionElapsed('2026-07-21T00:00:00Z', Date.parse('2026-07-21T00:00:12Z')))
      .toBe('已运行 12 秒')
  })

  it('shows minutes and seconds for longer detections', () => {
    expect(formatDetectionElapsed).toBeTypeOf('function')
    if (typeof formatDetectionElapsed !== 'function') return

    expect(formatDetectionElapsed('2026-07-21T00:00:00Z', Date.parse('2026-07-21T00:01:05Z')))
      .toBe('已运行 1 分 5 秒')
  })
})
