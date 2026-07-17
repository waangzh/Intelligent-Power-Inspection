import type { RobotHeartbeatStatus } from '@/types/robotHeartbeat'
import type { RouteDeployment, RouteDeploymentState } from '@/types/routeDeployment'

export interface DeploymentEligibility {
  eligible: boolean
  reason: string
}

export const DEPLOYMENT_STATE_LABELS: Record<RouteDeploymentState, string> = {
  PENDING: '待同步',
  INSTALLING: '同步中',
  READY_FOR_ROBOT: '等待机器人领取',
  FAILED: '同步失败',
  UNKNOWN: '待对账',
}

export function deploymentEligibility(status: RobotHeartbeatStatus, belongsToRouteSite: boolean): DeploymentEligibility {
  if (!belongsToRouteSite) return { eligible: false, reason: '机器人不属于当前路线站点' }
  if (!status.source?.bridgeConfigured) return { eligible: false, reason: 'Bridge 未配置该机器人' }
  if (status.connectionStatus === 'BRIDGE_UNREACHABLE') return { eligible: false, reason: 'Bridge 不可达' }
  if (!status.online) return { eligible: false, reason: '机器人离线，等待心跳恢复' }
  if (status.connectionStatus !== 'CONNECTED') return { eligible: false, reason: 'Bridge 状态异常' }
  return { eligible: true, reason: '可部署' }
}

export function deploymentStateType(state: RouteDeploymentState): 'success' | 'danger' | 'warning' | 'info' {
  if (state === 'READY_FOR_ROBOT') return 'success'
  if (state === 'FAILED') return 'danger'
  if (state === 'UNKNOWN') return 'warning'
  return 'info'
}

export function shouldPollDeployment(state: RouteDeploymentState) {
  return state === 'PENDING' || state === 'INSTALLING' || state === 'UNKNOWN'
}

export function shortHash(value?: string | null) {
  if (!value) return '-'
  return value.length <= 16 ? value : `${value.slice(0, 10)}…${value.slice(-6)}`
}

/** 仅返回管理端可展示的审计字段，避免任何意外远端字段进入页面。 */
export function visibleDeploymentAudit(deployment: RouteDeployment) {
  return {
    state: deployment.state,
    attemptCount: deployment.attemptCount,
    lastAttemptAt: deployment.lastAttemptAt ?? null,
    errorCode: deployment.errorCode ?? null,
    errorMessage: deployment.errorMessage ?? null,
    routeContentSha256: deployment.routeContentSha256,
    mapAssetId: deployment.mapAssetId,
    mapImageSha256: deployment.mapImageSha256,
  }
}
