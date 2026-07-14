import { describe, expect, it } from 'vitest'
import type { RobotHeartbeatStatus } from '@/types/robotHeartbeat'
import { connectionLabel, heartbeatListState, heartbeatVisual, offlineReasonLabel } from './robotHeartbeatStatus'

const base: RobotHeartbeatStatus = {
  robotId: 'robot-001',
  connectionStatus: 'CONNECTED',
  online: true,
  source: { name: 'robot-bridge', bridgeConfigured: true },
  acceptedEventSequence: 0,
}

describe('robotHeartbeatStatus', () => {
  it('显示在线与离线状态', () => {
    expect(heartbeatVisual(base)).toBe('success')
    expect(connectionLabel('CONNECTED')).toBe('已连接')
    expect(heartbeatVisual({ ...base, online: false, connectionStatus: 'OFFLINE', offlineReason: 'HEARTBEAT_TIMEOUT' })).toBe('danger')
    expect(offlineReasonLabel('HEARTBEAT_TIMEOUT')).toContain('12 秒')
  })

  it('区分空数据与接口失败', () => {
    expect(heartbeatListState([], false)).toBe('empty')
    expect(heartbeatListState([], true)).toBe('failed')
  })
})
