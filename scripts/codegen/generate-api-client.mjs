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

const mpClientJs = `${banner}const { get, post, put } = require('../utils/request')
const paths = require('./api-paths')

function rel(apiPath) {
  return paths.apiRel(apiPath)
}

/** OpenAPI 对齐的 thin client — 路径与 Web generated/api-client 一致 */
const openapiClient = {
  auth: {
    login: (username, password, remember = false) =>
      post(rel('${authLogin?.apiPath || '/api/v1/auth/login'}'), { username, password, remember }),
    me: () => get(rel('${authMe?.apiPath || '/api/v1/auth/me'}')),
    refresh: () => post(rel('${authRefresh?.apiPath || '/api/v1/auth/refresh'}')),
  },
  alarms: {
    getWorkOrderPolicy: () => get(rel('${alarmPolicy?.apiPath || '/api/v1/alarms/work-order-policy'}')),
    updateWorkOrderPolicy: (rules) => put(rel('${alarmPolicy?.apiPath || '/api/v1/alarms/work-order-policy'}'), { rules }),
  },
}

module.exports = { openapiClient, ...paths }
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
