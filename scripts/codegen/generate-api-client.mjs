#!/usr/bin/env node
/**
 * 从 openapi.json 生成 Web/小程序共用的 API 路径常量与 thin client 封装。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const specPath = path.join(repoRoot, 'shared/generated/openapi.json')
const pathsJsonPath = path.join(repoRoot, 'shared/generated/api-paths.json')
const webClientPath = path.join(repoRoot, 'frontend/web/src/generated/api-client.ts')
const webPathsPath = path.join(repoRoot, 'frontend/web/src/generated/api-paths.ts')
const mpClientPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/api-client.js')
const mpPathsPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/api-paths.js')
const dtoManifestPath = path.join(repoRoot, 'shared/generated/dto-schemas.json')
const webDtoPath = path.join(repoRoot, 'frontend/web/src/generated/dto-types.ts')
const mpDtoPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/dto-types.js')

const banner = `/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */\n`

if (!fs.existsSync(specPath)) {
  console.warn('跳过 api-client 生成：缺少 shared/generated/openapi.json')
  process.exit(0)
}

const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'))
const paths = spec.paths || {}
const schemas = spec.components?.schemas || {}

const apiPaths = Object.keys(paths)
  .filter((p) => p.startsWith('/api/v1/'))
  .sort()

function toConstName(apiPath) {
  const slug = apiPath
    .replace(/^\/api\/v1\//, '')
    .replace(/\{([^}]+)\}/g, '_$1')
    .replace(/[^a-zA-Z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
  return slug
    .split('_')
    .filter(Boolean)
    .map((part, i) => (i === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
    .join('') || 'root'
}

function rel(apiPath) {
  return apiPath.replace(/^\/api\/v1/, '')
}

const pathEntries = apiPaths.map((p) => ({ apiPath: p, relPath: rel(p), name: toConstName(p) }))
const used = new Set()
for (const entry of pathEntries) {
  let name = entry.name
  let i = 2
  while (used.has(name)) {
    name = `${entry.name}${i++}`
  }
  entry.name = name
  used.add(name)
}

fs.writeFileSync(
  pathsJsonPath,
  `${JSON.stringify({ generatedAt: new Date().toISOString(), paths: pathEntries }, null, 2)}\n`,
  'utf8',
)

const webPathsTs = `${banner}${pathEntries
  .map(({ apiPath, name }) => `export const ${name} = '${apiPath}' as const`)
  .join('\n')}

export const API_PATHS = {
${pathEntries.map(({ apiPath, name }) => `  ${name}: '${apiPath}',`).join('\n')}
} as const

export function apiRel(name: keyof typeof API_PATHS): string {
  return API_PATHS[name].replace(/^\\/api\\/v1/, '')
}
`
fs.mkdirSync(path.dirname(webPathsPath), { recursive: true })
fs.writeFileSync(webPathsPath, webPathsTs, 'utf8')

const mpPathsJs = `${banner}${pathEntries.map(({ apiPath, name }) => `const ${name} = '${apiPath}'`).join('\n')}

function apiRel(path) {
  return path.replace(/^\\/api\\/v1/, '')
}

module.exports = {
${pathEntries.map(({ name }) => `  ${name},`).join('\n')}
  API_PATHS: {
${pathEntries.map(({ apiPath, name }) => `    ${name}: '${apiPath}',`).join('\n')}
  },
  apiRel,
}
`
fs.writeFileSync(mpPathsPath, mpPathsJs, 'utf8')

function pick(nameSuffix) {
  return pathEntries.find((e) => e.apiPath === nameSuffix || e.apiPath.endsWith(nameSuffix))
}

const authLogin = pick('/api/v1/auth/login')
const authMe = pick('/api/v1/auth/me')
const authRefresh = pick('/api/v1/auth/refresh')
const alarmPolicy = pick('/api/v1/alarms/work-order-policy')

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete'])
const GENERIC_OPERATION_IDS = new Set([
  'create', 'update', 'delete', 'list', 'get', 'route', 'orders', 'sites', 'tasks',
  'routes', 'robots', 'alarms', 'notifications', 'records', 'role', 'enabled',
])

function pathParamNames(apiPath) {
  return [...apiPath.matchAll(/\{([^}]+)\}/g)].map((m) => m[1])
}

function toGroupName(apiPath) {
  const first = apiPath.replace(/^\/api\/v1\//, '').split('/')[0]
  return first.replace(/-([a-z])/g, (_, c) => c.toUpperCase())
}

function toCamelCase(slug) {
  return slug
    .split(/[^a-zA-Z0-9]+/)
    .filter(Boolean)
    .map((part, i) => (i === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
    .join('')
}

function deriveMethodName(httpMethod, apiPath, operationId, usedInGroup) {
  let base = operationId
  if (!base || /^list_\d+$/.test(base) || GENERIC_OPERATION_IDS.has(base)) {
    const literals = apiPath.replace(/^\/api\/v1\//, '').split('/').filter((seg) => seg && !seg.startsWith('{'))
    const tail = literals.slice(1).join('_') || literals[0] || 'root'
    base = `${httpMethod}_${tail.replace(/[^a-zA-Z0-9]+/g, '_')}`
  }
  base = toCamelCase(base.replace(/:/g, '_'))
  if (!base) base = `${httpMethod}Root`
  let name = base.charAt(0).toLowerCase() + base.slice(1)
  let i = 2
  while (usedInGroup.has(name)) {
    name = `${base.charAt(0).toLowerCase() + base.slice(1)}${i++}`
  }
  usedInGroup.add(name)
  return name
}

function genMpMethodBody(op) {
  const { httpMethod, pathConstName, params, methodName } = op
  const httpFn = httpMethod === 'delete' ? 'del' : httpMethod
  const pathArgs = params.length ? `{ ${params.join(', ')} }` : null
  const build = pathArgs
    ? `buildPath(API_PATHS.${pathConstName}, ${pathArgs})`
    : `buildPath(API_PATHS.${pathConstName})`

  if (params.length === 0) {
    if (httpMethod === 'get') return `${methodName}(query) { return ${httpFn}(${build}, query) }`
    if (httpMethod === 'delete') return `${methodName}() { return ${httpFn}(${build}) }`
    return `${methodName}(body) { return ${httpFn}(${build}, body) }`
  }
  const sig = [...params, httpMethod === 'get' ? 'query' : 'body'].join(', ')
  if (httpMethod === 'get') {
    return `${methodName}(${sig}) { return ${httpFn}(${build}, query) }`
  }
  if (httpMethod === 'delete') {
    return `${methodName}(${params.join(', ')}) { return ${httpFn}(${build}) }`
  }
  return `${methodName}(${sig}) { return ${httpFn}(${build}, body) }`
}

/** 小程序 services 层使用的友好别名 → OpenAPI 路径 + HTTP 方法 */
const MP_SERVICE_ENDPOINTS = [
  ['auth.register', '/api/v1/auth/register', 'post'],
  ['auth.logout', '/api/v1/auth/logout', 'post'],
  ['auth.changePassword', '/api/v1/auth/password', 'put'],
  ['users.list', '/api/v1/users', 'get'],
  ['users.updateMe', '/api/v1/users/me', 'patch'],
  ['users.updateRole', '/api/v1/users/{id}/role', 'patch'],
  ['users.toggleEnabled', '/api/v1/users/{id}/enabled', 'patch'],
  ['users.getPreferences', '/api/v1/users/me/preferences', 'get'],
  ['users.savePreferences', '/api/v1/users/me/preferences', 'put'],
  ['users.getActivities', '/api/v1/users/me/activities', 'get'],
  ['sites.list', '/api/v1/sites', 'get'],
  ['sites.listAreas', '/api/v1/sites/areas', 'get'],
  ['sites.create', '/api/v1/sites', 'post'],
  ['sites.update', '/api/v1/sites/{id}', 'patch'],
  ['sites.remove', '/api/v1/sites/{id}', 'delete'],
  ['sites.createArea', '/api/v1/sites/{id}/areas', 'post'],
  ['sites.removeArea', '/api/v1/sites/areas/{areaId}', 'delete'],
  ['sites.getSlamMap', '/api/v1/sites/{id}/slam-map', 'get'],
  ['sites.saveSlamMap', '/api/v1/sites/{id}/slam-map', 'put'],
  ['sites.removeSlamMap', '/api/v1/sites/{id}/slam-map', 'delete'],
  ['sites.listSlamMaps', '/api/v1/sites/slam-maps', 'get'],
  ['routes.list', '/api/v1/routes', 'get'],
  ['routes.create', '/api/v1/routes', 'post'],
  ['routes.replace', '/api/v1/routes/{id}', 'put'],
  ['routes.remove', '/api/v1/routes/{id}', 'delete'],
  ['tasks.list', '/api/v1/tasks', 'get'],
  ['tasks.create', '/api/v1/tasks', 'post'],
  ['tasks.dispatch', '/api/v1/tasks/{id}/dispatch', 'post'],
  ['tasks.pause', '/api/v1/tasks/{id}/pause', 'post'],
  ['tasks.resume', '/api/v1/tasks/{id}/resume', 'post'],
  ['tasks.takeover', '/api/v1/tasks/{id}/takeover', 'post'],
  ['tasks.cancel', '/api/v1/tasks/{id}/cancel', 'post'],
  ['tasks.listEvents', '/api/v1/tasks/{id}/events', 'get'],
  ['records.list', '/api/v1/records', 'get'],
  ['alarms.list', '/api/v1/alarms', 'get'],
  ['alarms.ack', '/api/v1/alarms/{id}/ack', 'post'],
  ['alarms.ackAll', '/api/v1/alarms/ack-all', 'post'],
  ['workOrders.list', '/api/v1/work-orders', 'get'],
  ['workOrders.patch', '/api/v1/work-orders/{id}', 'patch'],
  ['workOrders.createFromAlarm', '/api/v1/work-orders/from-alarm/{alarmId}', 'post'],
  ['workOrders.claim', '/api/v1/work-orders/{id}/claim', 'post'],
  ['workOrders.updateStatus', '/api/v1/work-orders/{id}/status', 'patch'],
  ['robots.list', '/api/v1/robots', 'get'],
  ['robots.create', '/api/v1/robots', 'post'],
  ['robots.remove', '/api/v1/robots/{id}', 'delete'],
  ['detectionTemplates.list', '/api/v1/detection-templates', 'get'],
  ['detectionTemplates.create', '/api/v1/detection-templates', 'post'],
  ['detectionTemplates.remove', '/api/v1/detection-templates/{id}', 'delete'],
  ['notifications.list', '/api/v1/notifications', 'get'],
  ['notifications.markRead', '/api/v1/notifications/{id}/read', 'patch'],
  ['notifications.markAllRead', '/api/v1/notifications/read-all', 'patch'],
  ['notifications.remove', '/api/v1/notifications/{id}', 'delete'],
]

const mpOperations = []
for (const entry of pathEntries) {
  const methods = paths[entry.apiPath] || {}
  for (const httpMethod of Object.keys(methods)) {
    if (!HTTP_METHODS.has(httpMethod)) continue
    const op = methods[httpMethod]
    mpOperations.push({
      apiPath: entry.apiPath,
      pathConstName: entry.name,
      httpMethod,
      operationId: op.operationId || '',
      params: pathParamNames(entry.apiPath),
      group: toGroupName(entry.apiPath),
    })
  }
}

const mpGroups = new Map()
for (const op of mpOperations) {
  if (!mpGroups.has(op.group)) mpGroups.set(op.group, { used: new Set(), ops: [] })
  const bucket = mpGroups.get(op.group)
  op.methodName = deriveMethodName(op.httpMethod, op.apiPath, op.operationId, bucket.used)
  bucket.ops.push(op)
}

const mpOpIndex = new Map()
for (const op of mpOperations) {
  mpOpIndex.set(`${op.httpMethod.toUpperCase()} ${op.apiPath}`, op)
}

const serviceAliasGroups = new Map()
for (const [alias, apiPath, httpMethod] of MP_SERVICE_ENDPOINTS) {
  const op = mpOpIndex.get(`${httpMethod.toUpperCase()} ${apiPath}`)
  if (!op) {
    throw new Error(`MP_SERVICE_ENDPOINTS 未匹配 openapi: ${httpMethod.toUpperCase()} ${apiPath} (${alias})`)
  }
  const [group, name] = alias.split('.')
  if (!serviceAliasGroups.has(group)) serviceAliasGroups.set(group, [])
  serviceAliasGroups.get(group).push(
    `    ${name}: (...args) => openapiClient.${op.group}.${op.methodName}(...args),`,
  )
}

const mpServicesBlock = [...serviceAliasGroups.entries()]
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([group, lines]) => `  ${group}: {\n${lines.join('\n')}\n  },`)
  .join('\n')

const mpGroupBlocks = [...mpGroups.entries()]
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([group, { ops }]) => {
    const methods = ops
      .sort((a, b) => a.apiPath.localeCompare(b.apiPath) || a.httpMethod.localeCompare(b.httpMethod))
      .map((op) => `    ${genMpMethodBody(op)},`)
      .join('\n')
    return `  ${group}: {\n${methods}\n  },`
  })
  .join('\n')

const webClientTs = `${banner}import { http } from '@/api/http'
import { API_PATHS } from '@/generated/api-paths'
import type { AuthSession } from '@/types/auth'
import type { AlarmWorkOrderPolicy } from '@/types'
import type { AlarmSeverity, AlarmWorkOrderMode } from '@/types'

function rel(path: string) {
  return path.replace(/^\\/api\\/v1/, '')
}

/** OpenAPI 对齐的 thin client — 与小程序 generated/api-client.js 路径一致 */
export const openapiClient = {
  auth: {
    login: (username: string, password: string, remember = false) =>
      http.post<AuthSession>(rel('${authLogin?.apiPath || '/api/v1/auth/login'}'), { username, password, remember }),
    me: () => http.get<AuthSession>(rel('${authMe?.apiPath || '/api/v1/auth/me'}')),
    refresh: () => http.post<AuthSession>(rel('${authRefresh?.apiPath || '/api/v1/auth/refresh'}')),
  },
  alarms: {
    getWorkOrderPolicy: () => http.get<AlarmWorkOrderPolicy>(rel('${alarmPolicy?.apiPath || '/api/v1/alarms/work-order-policy'}')),
    updateWorkOrderPolicy: (rules: Record<AlarmSeverity, AlarmWorkOrderMode>) =>
      http.put<AlarmWorkOrderPolicy>(rel('${alarmPolicy?.apiPath || '/api/v1/alarms/work-order-policy'}'), { rules }),
  },
}

export { API_PATHS }
`
fs.writeFileSync(webClientPath, webClientTs, 'utf8')

const mpClientJs = `${banner}const { get, post, put, patch, del } = require('../utils/request')
const { API_PATHS, apiRel } = require('./api-paths')

function buildPath(apiPath, params = {}) {
  let relPath = apiRel(apiPath)
  for (const [key, value] of Object.entries(params)) {
    relPath = relPath.replace(\`{\${key}}\`, encodeURIComponent(String(value)))
  }
  return relPath
}

/** OpenAPI 自动生成 — 路径与 backend openapi.json 一致；业务层优先用 services 别名 */
const openapiClient = {
${mpGroupBlocks}
}

openapiClient.auth.login = (username, password, remember = false) =>
  post(buildPath(API_PATHS.authLogin), { username, password, remember })
openapiClient.alarms.getWorkOrderPolicy = () => get(buildPath(API_PATHS.alarmsWorkOrderPolicy))
openapiClient.alarms.updateWorkOrderPolicy = (rules) => put(buildPath(API_PATHS.alarmsWorkOrderPolicy), { rules })

/** 小程序 services 层稳定别名（避免 operationId 变动影响业务代码） */
const services = {
${mpServicesBlock}
}

module.exports = { openapiClient, services, API_PATHS, apiRel }
`
fs.writeFileSync(mpClientPath, mpClientJs, 'utf8')

const dtoKeys = Object.keys(schemas).sort()

fs.writeFileSync(
  dtoManifestPath,
  `${JSON.stringify({ generatedAt: new Date().toISOString(), schemas: dtoKeys }, null, 2)}\n`,
  'utf8',
)

const webDtoTs = `${banner}import type { components } from '@/generated/api-types'

export type OpenApiComponents = components
${dtoKeys.slice(0, 80).map((name) => `export type ${name} = components['schemas']['${name}']`).join('\n')}
${dtoKeys.length > 80 ? `\n// ... ${dtoKeys.length - 80} more schemas in components.schemas` : ''}
`
fs.writeFileSync(webDtoPath, webDtoTs, 'utf8')

fs.writeFileSync(
  mpDtoPath,
  `${banner}/** DTO 类型权威源：frontend/web/src/generated/dto-types.ts（OpenAPI 生成） */\nmodule.exports = {}\n`,
  'utf8',
)

console.log('Generated API client artifacts:')
console.log(`  ${path.relative(repoRoot, pathsJsonPath)} (${pathEntries.length} paths)`)
console.log(`  ${path.relative(repoRoot, webClientPath)}`)
console.log(`  ${path.relative(repoRoot, mpClientPath)}`)
console.log(`  ${path.relative(repoRoot, dtoManifestPath)} (${dtoKeys.length} schemas)`)
