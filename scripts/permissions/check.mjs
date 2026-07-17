import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadBackendPermissionManifest } from './parse-backend.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const manifestPath = path.join(repoRoot, 'shared/generated/permissions.json')
const webOutPath = path.join(repoRoot, 'frontend/web/src/generated/permissions.ts')
const mpOutPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/permissions.js')
const legacyWebCatalog = path.join(repoRoot, 'frontend/web/src/utils/permission-catalog.ts')
const legacyMpCatalog = path.join(repoRoot, 'frontend/wechat-program/miniprogram/utils/permission-catalog.js')

function parseGeneratedJson(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`缺少生成文件: ${path.relative(repoRoot, filePath)}，请先运行 npm run permissions:generate`)
  }
  return JSON.parse(fs.readFileSync(filePath, 'utf8'))
}

function parseWebGenerated(byRoleExpected, allExpected) {
  const source = fs.readFileSync(webOutPath, 'utf8')
  assertNoLegacyCatalog()
  for (const [role, perms] of Object.entries(byRoleExpected)) {
    const block = extractRoleBlock(source, role)
    const parsed = parseQuotedList(block)
    assertSame(`${path.relative(repoRoot, webOutPath)} [${role}]`, parsed, perms)
  }
  const valuesBlock = source.match(/export const PERMISSION_VALUES = \[([\s\S]*?)\] as const/)
  if (!valuesBlock) throw new Error('Web generated: 无法解析 PERMISSION_VALUES')
  const parsedValues = parseQuotedList(valuesBlock[1])
  assertSame(`${path.relative(repoRoot, webOutPath)} PERMISSION_VALUES`, parsedValues, allExpected)
}

function parseMpGenerated(byRoleExpected, allExpected) {
  const source = fs.readFileSync(mpOutPath, 'utf8')
  for (const [role, perms] of Object.entries(byRoleExpected)) {
    const block = extractRoleBlock(source, role)
    const parsed = parseQuotedList(block)
    assertSame(`${path.relative(repoRoot, mpOutPath)} [${role}]`, parsed, perms)
  }
  const valuesBlock = source.match(/const PERMISSION_VALUES = \[([\s\S]*?)\]/)
  if (!valuesBlock) throw new Error('Mini-program generated: 无法解析 PERMISSION_VALUES')
  const parsedValues = parseQuotedList(valuesBlock[1])
  assertSame(`${path.relative(repoRoot, mpOutPath)} PERMISSION_VALUES`, parsedValues, allExpected)
}

function extractRoleBlock(source, role) {
  const re = new RegExp(`${role}:\\s*\\[([\\s\\S]*?)\\],`)
  const match = source.match(re)
  if (!match) throw new Error(`无法解析角色块: ${role}`)
  return match[1]
}

function parseQuotedList(raw) {
  return [...raw.matchAll(/'([^']+)'/g)].map((m) => m[1]).sort()
}

function assertSame(label, actual, expected) {
  const a = actual.join(',')
  const b = [...expected].sort().join(',')
  if (a !== b) {
    throw new Error(
      `[${label}] 与 backend 不一致\n  实际: ${a || '(无)'}\n  期望: ${b || '(无)'}`,
    )
  }
}

function assertNoLegacyCatalog() {
  if (fs.existsSync(legacyWebCatalog)) {
    throw new Error(`仍存在旧文件 ${path.relative(repoRoot, legacyWebCatalog)}，应已删除`)
  }
  if (fs.existsSync(legacyMpCatalog)) {
    throw new Error(`仍存在旧文件 ${path.relative(repoRoot, legacyMpCatalog)}，应已删除`)
  }
}

function assertManifestMatchesBackend(backend, committed) {
  assertSame('permissions.json permissions', committed.permissions, backend.permissions)
  for (const role of Object.keys(backend.byRole)) {
    assertSame(`permissions.json [${role}]`, committed.byRole[role] || [], backend.byRole[role])
  }
}

let ok = true
try {
  const backend = loadBackendPermissionManifest()
  const committed = parseGeneratedJson(manifestPath)
  assertManifestMatchesBackend(backend, committed)
  parseWebGenerated(backend.byRole, backend.permissions)
  parseMpGenerated(backend.byRole, backend.permissions)
  assertNoLegacyCatalog()
  console.log('权限定义校验通过：backend ↔ shared/generated ↔ Web ↔ 小程序')
} catch (err) {
  ok = false
  console.error(err.message || err)
  console.error('\n请运行: npm run permissions:generate')
}

process.exit(ok ? 0 : 1)
