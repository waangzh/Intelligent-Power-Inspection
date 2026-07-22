import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/resources', () => ({
  resourcesApi: {
    listAlarms: vi.fn(),
    acknowledgeAlarm: vi.fn(),
    acknowledgeAllAlarms: vi.fn(),
    retryAlarmWorkOrder: vi.fn(),
  },
}))

vi.mock('@/generated/api-client', () => ({
  openapiClient: { alarms: {} },
}))

import { useAlarmStore } from '@/stores/alarm'

describe('告警 store 生产接口', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('不暴露浏览器本地或随机告警创建方法', () => {
    const store = useAlarmStore() as unknown as Record<string, unknown>

    expect(store).not.toHaveProperty('addAlarm')
    expect(store).not.toHaveProperty('maybeGenerateRouteAlarm')
    expect(store).not.toHaveProperty('maybeGenerateCheckpointAlarm')
  })
})
