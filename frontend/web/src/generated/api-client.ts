/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */
import { http } from '@/api/http'
import { API_PATHS } from '@/generated/api-paths'
import type { AuthSession } from '@/types/auth'
import type { AlarmWorkOrderPolicy } from '@/types'
import type { AlarmSeverity, AlarmWorkOrderMode } from '@/types'

function rel(path: string) {
  return path.replace(/^\/api\/v1/, '')
}

/** OpenAPI 对齐的 thin client — 与小程序 generated/api-client.js 路径一致 */
export const openapiClient = {
  auth: {
    login: (username: string, password: string, remember = false) =>
      http.post<AuthSession>(rel('/api/v1/auth/login'), { username, password, remember }),
    me: () => http.get<AuthSession>(rel('/api/v1/auth/me')),
    refresh: () => http.post<AuthSession>(rel('/api/v1/auth/refresh')),
  },
  alarms: {
    getWorkOrderPolicy: () => http.get<AlarmWorkOrderPolicy>(rel('/api/v1/alarms/work-order-policy')),
    updateWorkOrderPolicy: (rules: Record<AlarmSeverity, AlarmWorkOrderMode>) =>
      http.put<AlarmWorkOrderPolicy>(rel('/api/v1/alarms/work-order-policy'), { rules }),
  },
}

export { API_PATHS }
