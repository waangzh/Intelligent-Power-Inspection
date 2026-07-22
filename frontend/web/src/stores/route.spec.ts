import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { Route } from '@/types'
import type { PageResult } from '@/types/pagination'

vi.mock('@/api/resources', () => ({
  resourcesApi: { listRoutes: vi.fn(), createRoute: vi.fn() },
}))

import { resourcesApi } from '@/api/resources'
import { useRouteStore } from '@/stores/route'

const eastRoute: Route = {
  id: 'route-east', siteId: 'site-east', name: '城东巡检路线', description: '', path: [],
  routeDetections: [], checkpoints: [], mapMode: '2d', createdAt: '2026-07-20T00:00:00Z',
}
const westRoute: Route = { ...eastRoute, id: 'route-west', siteId: 'site-west', name: '城西巡检路线' }
const newEastRoute: Route = { ...eastRoute, id: 'route-east-new', name: '新建城东巡检路线' }
const legacyEastRoute = {
  id: 'route-east-legacy', siteId: 'site-east', name: 'Legacy east route', description: '', createdAt: '2026-07-16T00:00:00Z',
} as Route


function page(items: Route[]): PageResult<Route> {
  return { items, total: items.length, page: 0, size: 20, hasMore: false }
}

describe('路线列表加载', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('忽略晚于新请求返回的旧站点结果', async () => {
    let resolveEast: (value: PageResult<Route>) => void = () => {}
    const eastResponse = new Promise<PageResult<Route>>((resolve) => { resolveEast = resolve })
    vi.mocked(resourcesApi.listRoutes)
      .mockReturnValueOnce(eastResponse)
      .mockResolvedValueOnce(page([westRoute]))
    const store = useRouteStore()

    const eastLoad = store.load('site-east')
    const westLoad = store.load('site-west')

    await expect(westLoad).resolves.toBe(true)
    resolveEast(page([eastRoute]))
    await expect(eastLoad).resolves.toBe(false)

    expect(store.routes).toEqual([westRoute])
  })

  it('清空列表后忽略尚未完成的请求结果', async () => {
    let resolveResponse: (value: PageResult<Route>) => void = () => {}
    const response = new Promise<PageResult<Route>>((resolve) => { resolveResponse = resolve })
    vi.mocked(resourcesApi.listRoutes).mockReturnValueOnce(response)
    const store = useRouteStore()

    const loading = store.load('site-east')
    store.clear()
    resolveResponse(page([eastRoute]))

    await expect(loading).resolves.toBe(false)
    expect(store.routes).toEqual([])
    expect(store.total).toBe(0)
  })

  it('新建路线后的刷新结果会覆盖创建前未完成的列表请求', async () => {
    let resolveStale: (value: PageResult<Route>) => void = () => {}
    const staleResponse = new Promise<PageResult<Route>>((resolve) => { resolveStale = resolve })
    vi.mocked(resourcesApi.listRoutes)
      .mockReturnValueOnce(staleResponse)
      .mockResolvedValueOnce(page([newEastRoute, eastRoute]))
    vi.mocked(resourcesApi.createRoute).mockResolvedValueOnce(newEastRoute)
    const store = useRouteStore()

    const staleLoad = store.load('site-east')
    await store.createRoute('site-east', newEastRoute.name)
    const refreshedLoad = store.load('site-east')
    resolveStale(page([eastRoute]))

    await expect(staleLoad).resolves.toBe(false)
    await expect(refreshedLoad).resolves.toBe(true)
    expect(store.routes).toEqual([newEastRoute, eastRoute])
  })

  it('create invalidates a pending route list request', async () => {
    let resolveStale: (value: PageResult<Route>) => void = () => {}
    const staleResponse = new Promise<PageResult<Route>>((resolve) => { resolveStale = resolve })
    vi.mocked(resourcesApi.listRoutes).mockReturnValueOnce(staleResponse)
    vi.mocked(resourcesApi.createRoute).mockResolvedValueOnce(newEastRoute)
    const store = useRouteStore()

    const staleLoad = store.load('site-east')
    await store.createRoute('site-east', newEastRoute.name)
    resolveStale(page([eastRoute]))

    await expect(staleLoad).resolves.toBe(false)
    expect(store.routes).toEqual([newEastRoute])
  })

  it('列表刷新未返回新建路线时仍保留创建结果', async () => {
    vi.mocked(resourcesApi.listRoutes).mockResolvedValueOnce(page([]))
    vi.mocked(resourcesApi.createRoute).mockResolvedValueOnce(newEastRoute)
    const store = useRouteStore()

    const created = await store.createRoute('site-east', newEastRoute.name)
    await store.load('site-east')
    store.ensureRoute(created)

    expect(store.routes).toEqual([newEastRoute])
    expect(store.total).toBe(1)
  })

  it('normalizes incomplete legacy routes returned by the API', async () => {
    vi.mocked(resourcesApi.listRoutes).mockResolvedValueOnce(page([legacyEastRoute]))
    const store = useRouteStore()

    await expect(store.load('site-east')).resolves.toBe(true)

    expect(store.routes[0]).toMatchObject({
      path: [],
      routeDetections: [],
      checkpoints: [],
      mapMode: '2d',
    })
  })

  it('creates route detection items with explicit alarm-safe defaults', async () => {
    vi.mocked(resourcesApi.createRoute).mockImplementationOnce(async (route) => route)
    const store = useRouteStore()

    await store.createRoute('site-east', '风险契约路线')

    const payload = vi.mocked(resourcesApi.createRoute).mock.calls[0][0]
    expect(payload.routeDetections.length).toBeGreaterThan(0)
    expect(payload.routeDetections.every((item) =>
      item.itemId === item.type
      && item.alarmMode === 'OFF'
      && item.alarmSeverity === 'MEDIUM'
      && item.alarmMessage === '',
    )).toBe(true)
  })
})
