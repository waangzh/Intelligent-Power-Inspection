/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */
const { get, post, put } = require('../utils/request')
const paths = require('./api-paths')

function rel(apiPath) {
  return paths.apiRel(apiPath)
}

/** OpenAPI 对齐的 thin client — 路径与 Web generated/api-client 一致 */
const openapiClient = {
  auth: {
    login: (username, password, remember = false) =>
      post(rel('/api/v1/auth/login'), { username, password, remember }),
    me: () => get(rel('/api/v1/auth/me')),
    refresh: () => post(rel('/api/v1/auth/refresh')),
  },
  alarms: {
    getWorkOrderPolicy: () => get(rel('/api/v1/alarms/work-order-policy')),
    updateWorkOrderPolicy: (rules) => put(rel('/api/v1/alarms/work-order-policy'), { rules }),
  },
}

module.exports = { openapiClient, ...paths }
