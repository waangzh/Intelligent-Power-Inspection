import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadBackendPermissionManifest } from './parse-backend.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const manifestPath = path.join(repoRoot, 'shared/generated/permissions.json')
const openapiPath = path.join(repoRoot, 'shared/generated/openapi-permissions.yaml')
const webOutPath = path.join(repoRoot, 'frontend/web/src/generated/permissions.ts')
const mpOutPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/permissions.js')

const banner = `/** AUTO-GENERATED — 请勿手工编辑。运行: npm run permissions:generate */\n`

function writeManifest(manifest) {
  fs.mkdirSync(path.dirname(manifestPath), { recursive: true })
  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
}

function writeOpenApi(manifest) {
  const enumLines = manifest.permissions.map((p) => `        - ${p}`).join('\n')
  const yaml = `# AUTO-GENERATED — 权限码 OpenAPI 片段（由 backend Permission.java 生成）
components:
  schemas:
    PermissionCode:
      type: string
      description: 权限码，权威定义见 backend Permission 枚举
      enum:
${enumLines}
    AuthScopes:
      type: object
      properties:
        siteIds:
          type: array
          items:
            type: string
    AuthFeatures:
      type: object
      properties:
        robotRegistration:
          type: boolean
        agentEnabled:
          type: boolean
    MeResponse:
      type: object
      required:
        - user
        - permissions
      properties:
        user:
          type: object
          description: UserDto
        permissions:
          type: array
          items:
            $ref: '#/components/schemas/PermissionCode'
        scopes:
          $ref: '#/components/schemas/AuthScopes'
        features:
          $ref: '#/components/schemas/AuthFeatures'
    LoginResponse:
      type: object
      required:
        - token
        - user
        - permissions
      properties:
        token:
          type: string
        user:
          type: object
        permissions:
          type: array
          items:
            $ref: '#/components/schemas/PermissionCode'
        scopes:
          $ref: '#/components/schemas/AuthScopes'
        features:
          $ref: '#/components/schemas/AuthFeatures'
        expiresAt:
          type: integer
          format: int64
          nullable: true
`
  fs.writeFileSync(openapiPath, yaml, 'utf8')
}

function writeWebTs(manifest) {
  const union = manifest.permissions.map((p) => `  | '${p}'`).join('\n')
  const byRoleEntries = Object.entries(manifest.byRole)
    .map(([role, perms]) => `  ${role}: [\n${perms.map((p) => `    '${p}',`).join('\n')}\n  ],`)
    .join('\n')

  const content = `${banner}export const PERMISSION_VALUES = [
${manifest.permissions.map((p) => `  '${p}',`).join('\n')}
] as const

export type Permission = (typeof PERMISSION_VALUES)[number]

/** Mock 演示登录专用 — 联调时权限必须来自 /auth/login 或 /auth/me */
export const PERMISSIONS_BY_ROLE: Record<'ADMIN' | 'DISPATCHER' | 'VIEWER', Permission[]> = {
${byRoleEntries}
}

/** @deprecated 仅 Mock 登录使用；运行时勿调用 */
export function permissionsForRole(role: 'ADMIN' | 'DISPATCHER' | 'VIEWER' | undefined): Permission[] {
  if (!role) return []
  return [...(PERMISSIONS_BY_ROLE[role] || [])]
}
`
  fs.mkdirSync(path.dirname(webOutPath), { recursive: true })
  fs.writeFileSync(webOutPath, content, 'utf8')
}

function writeMiniProgramJs(manifest) {
  const values = manifest.permissions.map((p) => `  '${p}',`).join('\n')
  const byRoleEntries = Object.entries(manifest.byRole)
    .map(([role, perms]) => `  ${role}: [\n${perms.map((p) => `    '${p}',`).join('\n')}\n  ],`)
    .join('\n')

  const content = `${banner}const PERMISSION_VALUES = [
${values}
]

/** Mock 演示登录专用 — 联调时权限必须来自 /auth/login 或 /auth/me */
const PERMISSIONS_BY_ROLE = {
${byRoleEntries}
}

function permissionsForRole(role) {
  if (!role) return []
  return [...(PERMISSIONS_BY_ROLE[role] || [])]
}

module.exports = {
  PERMISSION_VALUES,
  PERMISSIONS_BY_ROLE,
  permissionsForRole,
}
`
  fs.mkdirSync(path.dirname(mpOutPath), { recursive: true })
  fs.writeFileSync(mpOutPath, content, 'utf8')
}

const manifest = loadBackendPermissionManifest()
writeManifest(manifest)
writeOpenApi(manifest)
writeWebTs(manifest)
writeMiniProgramJs(manifest)

console.log('Generated permissions artifacts from backend:')
console.log(`  ${path.relative(repoRoot, manifestPath)}`)
console.log(`  ${path.relative(repoRoot, openapiPath)}`)
console.log(`  ${path.relative(repoRoot, webOutPath)}`)
console.log(`  ${path.relative(repoRoot, mpOutPath)}`)
