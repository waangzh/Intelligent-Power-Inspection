import { describe, expect, it } from 'vitest'
import type { RobotHeartbeatStatus } from '@/types/robotHeartbeat'
import type { RouteDeployment } from '@/types/routeDeployment'
import { DEPLOYMENT_STATE_LABELS, deploymentEligibility, shouldPollDeployment, visibleDeploymentAudit } from './routeDeployment'

const online: RobotHeartbeatStatus = {
  robotId: 'robot-1', connectionStatus: 'CONNECTED', online: true,
  source: { name: 'robot-bridge', bridgeConfigured: true }, acceptedEventSequence: 0,
}

const deployment: RouteDeployment = {
  id: 'deploy-1', routeRevisionId: 'revision-1', robotId: 'robot-1', requestId: 'request-1', state: 'UNKNOWN', attemptCount: 2,
  routeContentSha256: 'a'.repeat(64), mapAssetId: 'map-1', mapImageSha256: 'b'.repeat(64), createdAt: '2026-07-14T00:00:00Z', updatedAt: '2026-07-14T00:00:00Z', stateVersion: 2,
  errorCode: 'BRIDGE_HTTP_503', errorMessage: 'Bridge 服务暂不可用',
}

describe('路线部署管理端', () => {
  it('只允许同站点、已配置、在线且 Bridge 可达的机器人部署', () => {
    expect(deploymentEligibility(online, true)).toEqual({ eligible: true, reason: '可部署' })
    expect(deploymentEligibility(online, false).eligible).toBe(false)
    expect(deploymentEligibility({ ...online, online: false }, true).reason).toContain('离线')
    expect(deploymentEligibility({ ...online, connectionStatus: 'BRIDGE_UNREACHABLE' }, true).reason).toBe('Bridge 不可达')
    expect(deploymentEligibility({ ...online, source: { name: 'robot-bridge', bridgeConfigured: false } }, true).reason).toContain('未配置')
  })

  it('展示部署状态并只轮询尚未有确定结果的状态', () => {
    expect(DEPLOYMENT_STATE_LABELS.READY_FOR_ROBOT).toBe('Bridge 已就绪，待机器人领取任务')
    expect(shouldPollDeployment('PENDING')).toBe(true)
    expect(shouldPollDeployment('UNKNOWN')).toBe(true)
    expect(shouldPollDeployment('FAILED')).toBe(false)
  })

  it('失败摘要与轮询更新所用对象不包含敏感字段', () => {
    const audit = visibleDeploymentAudit({ ...deployment, state: 'FAILED' })
    expect(audit.state).toBe('FAILED')
    expect(audit.errorMessage).toBe('Bridge 服务暂不可用')
    expect(audit).not.toHaveProperty('bridgeToken')
    expect(audit).not.toHaveProperty('robotToken')
    expect(audit).not.toHaveProperty('internalUrl')
  })
})
