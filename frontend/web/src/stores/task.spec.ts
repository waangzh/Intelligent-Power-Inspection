import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { InspectionTask, TaskExecution, TaskStartEligibility } from '@/types'

vi.mock('@/api/resources', () => ({
  resourcesApi: {
    listTasks: vi.fn(), taskEvents: vi.fn(), listRecords: vi.fn(),
    getTaskExecution: vi.fn(), getTaskStartEligibility: vi.fn(), startTask: vi.fn(),
  },
}))

import { resourcesApi } from '@/api/resources'
import { useTaskStore } from '@/stores/task'

const task: InspectionTask = {
  id: 'task-1', name: '测试巡检', routeId: 'route-1', robotId: 'robot-1', status: 'CREATED',
  progress: 0, currentCheckpointSeq: 0, createdAt: '2026-07-14T00:00:00Z', routeRevisionId: 'rev-1', executionId: 'exec-1',
  routeContentSha256: 'a'.repeat(64), mapImageSha256: 'b'.repeat(64),
}

function execution(status: TaskExecution['status'] = 'STARTING'): TaskExecution {
  return {
    taskId: 'task-1', executionId: 'exec-1', routeRevisionId: 'rev-1', robotId: 'robot-1', deploymentId: 'dep-1', executorRouteId: 'route-1',
    routeContentSha256: 'a'.repeat(64), mapImageSha256: 'b'.repeat(64), status, currentTargetId: null, progress: 0,
    lastRobotSequence: 0, lastEventAt: null, lastErrorCode: null, lastErrorMessage: null, manualReconciliationRequired: false,
    createdAt: '2026-07-14T00:00:00Z', updatedAt: '2026-07-14T00:00:00Z',
  }
}

function eligibility(eligible = true): TaskStartEligibility {
  return { ...execution('CREATED'), eligible, ineligibleReason: eligible ? null : '部署尚未 READY_FOR_ROBOT' }
}

describe('任务执行轮询与启动状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(resourcesApi.listTasks).mockResolvedValue([task])
    vi.mocked(resourcesApi.taskEvents).mockResolvedValue([])
    vi.mocked(resourcesApi.listRecords).mockResolvedValue([])
    vi.mocked(resourcesApi.getTaskExecution).mockResolvedValue(execution())
    vi.mocked(resourcesApi.getTaskStartEligibility).mockResolvedValue(eligibility())
  })

  it('HTTP 启动受理后仍展示 STARTING，而不是 RUNNING', async () => {
    const store = useTaskStore()
    vi.mocked(resourcesApi.startTask).mockResolvedValue(execution('STARTING'))

    await store.loadDynamic()
    await store.startInspection('task-1')

    expect(resourcesApi.startTask).toHaveBeenCalledWith('task-1', expect.any(String))
    expect(store.statusOf(task)).toBe('STARTING')
    store.stopExecutionPolling()
  })

  it('启动资格不足时保留后端返回的禁用原因', async () => {
    vi.mocked(resourcesApi.getTaskStartEligibility).mockResolvedValue(eligibility(false))
    const store = useTaskStore()

    await store.loadDynamic()

    expect(store.eligibilityFor('task-1')?.eligible).toBe(false)
    expect(store.eligibilityFor('task-1')?.ineligibleReason).toContain('READY_FOR_ROBOT')
    store.stopExecutionPolling()
  })

  it('不轮询未绑定执行快照的模拟任务', async () => {
    vi.mocked(resourcesApi.listTasks).mockResolvedValue([{ ...task, executionId: undefined, routeRevisionId: undefined }])
    const store = useTaskStore()

    await store.loadDynamic()

    expect(resourcesApi.getTaskExecution).not.toHaveBeenCalled()
    expect(resourcesApi.getTaskStartEligibility).not.toHaveBeenCalled()
    store.stopExecutionPolling()
  })

  it('轮询刷新失败状态和脱敏错误摘要', async () => {
    const store = useTaskStore()
    await store.loadDynamic()
    store.stopExecutionPolling()
    vi.clearAllMocks()
    vi.useFakeTimers()
    const failed = { ...execution('FAILED'), lastErrorCode: 'ROBOT_ROUTE_FAILED', lastErrorMessage: '路线校验失败' }
    vi.mocked(resourcesApi.getTaskExecution).mockResolvedValue(failed)

    store.startExecutionPolling()
    await vi.advanceTimersByTimeAsync(2000)

    expect(resourcesApi.getTaskExecution).toHaveBeenCalledWith('task-1')
    expect(store.executionFor('task-1')?.lastErrorMessage).toBe('路线校验失败')
    store.stopExecutionPolling()
    vi.useRealTimers()
  })
})
