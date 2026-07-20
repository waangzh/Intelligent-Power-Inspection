import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { InspectionTask, RouteRevision, TaskExecution, TaskStartEligibility } from '@/types'
import type { RouteDeployment } from '@/types/routeDeployment'

vi.mock('@/api/resources', () => ({
  resourcesApi: {
    listTasks: vi.fn(), taskEvents: vi.fn(), listRecords: vi.fn(),
    getTaskExecution: vi.fn(), getTaskStartEligibility: vi.fn(), startTask: vi.fn(),
    pauseTask: vi.fn(), resumeTask: vi.fn(), takeoverTask: vi.fn(), cancelTask: vi.fn(),
    createTask: vi.fn(),
  },
}))

import { resourcesApi } from '@/api/resources'
import { latestReadyRevision, useTaskStore } from '@/stores/task'
import { DEPLOYMENT_STATE_LABELS } from '@/utils/routeDeployment'

const task: InspectionTask = {
  id: 'task-1', name: '测试巡检', routeId: 'route-1', robotId: 'robot-1', status: 'CREATED',
  progress: 0, currentCheckpointSeq: 0, createdAt: '2026-07-14T00:00:00Z', routeRevisionId: 'rev-1', executionId: 'exec-1',
  routeContentSha256: 'a'.repeat(64), mapImageSha256: 'b'.repeat(64),
}

function taskPage(items: InspectionTask[]) {
  return { items, total: items.length, page: 0, size: 50, hasMore: false }
}

function execution(status: TaskExecution['status'] = 'STARTING'): TaskExecution {
  return {
    taskId: 'task-1', executionId: 'exec-1', routeRevisionId: 'rev-1', robotId: 'robot-1', deploymentId: 'dep-1', executorRouteId: 'route-1',
    routeContentSha256: 'a'.repeat(64), mapImageSha256: 'b'.repeat(64), status, startMode: 'REMOTE_IMMEDIATE', currentTargetId: null, progress: 0,
    lastRobotSequence: 0, lastEventAt: null, lastErrorCode: null, lastErrorMessage: null, manualReconciliationRequired: false,
    createdAt: '2026-07-14T00:00:00Z', updatedAt: '2026-07-14T00:00:00Z',
  }
}

function eligibility(eligible = true): TaskStartEligibility {
  return {
    ...execution('CREATED'),
    eligible,
    ineligibleReason: eligible ? null : `部署尚未${DEPLOYMENT_STATE_LABELS.READY_FOR_ROBOT}`,
    supportsRemoteImmediateStart: true,
    supportsLocalConfirmStart: true,
  }
}

describe('任务执行轮询与启动状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(resourcesApi.listTasks).mockResolvedValue(taskPage([task]))
    vi.mocked(resourcesApi.taskEvents).mockResolvedValue({ items: [], total: 0, page: 0, size: 100, hasMore: false })
    vi.mocked(resourcesApi.listRecords).mockResolvedValue({ items: [], total: 0, page: 0, size: 20, hasMore: false })
    vi.mocked(resourcesApi.getTaskExecution).mockResolvedValue(execution())
    vi.mocked(resourcesApi.getTaskStartEligibility).mockResolvedValue(eligibility())
  })

  it('HTTP 启动受理后仍展示 STARTING，而不是 RUNNING', async () => {
    const store = useTaskStore()
    vi.mocked(resourcesApi.startTask).mockResolvedValue(execution('STARTING'))

    await store.loadDynamic()
    await store.refreshExecution('task-1')
    await store.startInspection('task-1', 'LOCAL_CONFIRM')

    expect(resourcesApi.startTask).toHaveBeenCalledWith('task-1', 'LOCAL_CONFIRM', expect.any(String))
    expect(store.statusOf(task)).toBe('STARTING')
    store.stopExecutionPolling()
  })

  it('启动资格不足时保留后端返回的禁用原因', async () => {
    vi.mocked(resourcesApi.getTaskStartEligibility).mockResolvedValue(eligibility(false))
    const store = useTaskStore()

    await store.loadDynamic()
    await store.refreshExecution('task-1')

    expect(store.eligibilityFor('task-1')?.eligible).toBe(false)
    expect(store.eligibilityFor('task-1')?.ineligibleReason).toContain(DEPLOYMENT_STATE_LABELS.READY_FOR_ROBOT)
    store.stopExecutionPolling()
  })

  it('不轮询未绑定执行快照的模拟任务', async () => {
    vi.mocked(resourcesApi.listTasks).mockResolvedValue(taskPage([{ ...task, executionId: undefined, routeRevisionId: undefined }]))
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

  it('控制请求保持等待态，且重复点击复用同一幂等键', async () => {
    const store = useTaskStore()
    await store.loadDynamic()
    store.stopExecutionPolling()
    const waiting = execution('PAUSING')
    vi.mocked(resourcesApi.pauseTask).mockResolvedValue(waiting)
    vi.mocked(resourcesApi.getTaskExecution).mockResolvedValue(waiting)

    await store.controlInspection('task-1', 'PAUSE')

    expect(resourcesApi.pauseTask).toHaveBeenCalledWith('task-1', expect.any(String))
    expect(store.statusOf(task)).toBe('PAUSING')
  })

  it('创建真实任务时提交 routeRevisionId 并保存 executionId', async () => {
    const store = useTaskStore()
    vi.mocked(resourcesApi.createTask).mockResolvedValue(task)
    vi.mocked(resourcesApi.getTaskExecution).mockResolvedValue(execution('CREATED'))

    const saved = await store.createTask('测试巡检', 'route-1', 'robot-1', 'rev-1')

    expect(resourcesApi.createTask).toHaveBeenCalledWith(expect.objectContaining({ routeRevisionId: 'rev-1' }))
    expect(saved.executionId).toBe('exec-1')
    expect(store.executionFor('task-1')?.status).toBe('CREATED')
    store.stopExecutionPolling()
  })

  it('只选择最新且哈希一致的 READY_FOR_ROBOT 修订', () => {
    const revisions = [1, 2].map((revisionNo) => ({
      id: `rev-${revisionNo}`, routeId: 'route-1', revisionNo, contentSha256: `${revisionNo}`.repeat(64),
      mapAssetId: 'map-1', mapImageSha256: 'b'.repeat(64), executorJson: {},
      validationReport: { valid: true, validatedAt: '2026-07-16T00:00:00Z', issues: [] }, createdAt: '2026-07-16T00:00:00Z',
    } satisfies RouteRevision))
    const deployments: RouteDeployment[] = [{
      id: 'dep-1', routeRevisionId: 'rev-1', robotId: 'robot-1', requestId: 'req-1', state: 'READY_FOR_ROBOT', attemptCount: 1,
      routeContentSha256: '1'.repeat(64), mapAssetId: 'map-1', mapImageSha256: 'b'.repeat(64),
      createdAt: '2026-07-16T00:00:00Z', updatedAt: '2026-07-16T00:00:00Z', stateVersion: 1,
    }]

    expect(latestReadyRevision(revisions, deployments, 'robot-1')?.id).toBe('rev-1')
    expect(latestReadyRevision(revisions, [{ ...deployments[0], routeContentSha256: 'x'.repeat(64) }], 'robot-1')).toBeUndefined()
  })
})
