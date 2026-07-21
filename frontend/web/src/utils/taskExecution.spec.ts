import { describe, expect, it } from 'vitest'
import type { TaskExecution } from '@/types'
import { startReceiptOf } from '@/utils/taskExecution'

function execution(patch: Partial<TaskExecution> = {}): TaskExecution {
  return {
    taskId: 'task-1', executionId: 'exec-1', routeRevisionId: 'rev-1', robotId: 'robot-1',
    routeContentSha256: 'a'.repeat(64), mapImageSha256: 'b'.repeat(64),
    status: 'CREATED', startMode: 'REMOTE_IMMEDIATE', progress: 0, lastRobotSequence: 0,
    manualReconciliationRequired: false, createdAt: '2026-07-21T00:00:00Z', updatedAt: '2026-07-21T00:00:00Z',
    ...patch,
  }
}

describe('任务启动接收状态', () => {
  it('CREATED 明确显示尚未下发', () => {
    expect(startReceiptOf(execution()).label).toBe('尚未下发')
  })

  it('Bridge commandId 只表示 Bridge 已入队', () => {
    const result = startReceiptOf(execution({ status: 'STARTING', startCommandId: 'command-1' }))
    expect(result.label).toBe('Bridge 已入队')
    expect(result.detail).toContain('等待机器人领取')
  })

  it('收到机器人事件后显示机器人已回传', () => {
    expect(startReceiptOf(execution({ status: 'STARTING', lastRobotSequence: 1 })).label).toBe('机器人已领取')
  })

  it('本地确认等待态显示机器人已准备', () => {
    expect(startReceiptOf(execution({ status: 'WAITING_LOCAL_CONFIRM' })).label).toBe('等待机器人本地确认')
  })

  it('本地确认事件只显示已确认，不提前显示运行中', () => {
    expect(startReceiptOf(execution({
      status: 'WAITING_LOCAL_CONFIRM', localConfirmedAt: '2026-07-21T00:01:00Z',
    })).label).toBe('机器人已确认')
  })

  it('route_started 后显示机器人已执行', () => {
    expect(startReceiptOf(execution({ status: 'RUNNING', startedAt: '2026-07-21T00:01:00Z' })).label).toBe('机器人已执行')
  })
})
