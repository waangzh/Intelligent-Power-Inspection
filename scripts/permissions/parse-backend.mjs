import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const permissionJavaPath = path.join(
  repoRoot,
  'backend/src/main/java/com/powerinspection/user/Permission.java',
)
const permissionServicePath = path.join(
  repoRoot,
  'backend/src/main/java/com/powerinspection/user/PermissionService.java',
)

/** @returns {Record<string, string>} e.g. TASK_VIEW -> task:view */
export function parsePermissionEnum(source) {
  const map = {}
  for (const match of source.matchAll(/^\s+(\w+)\("([^"]+)"\)/gm)) {
    map[match[1]] = match[2]
  }
  if (!Object.keys(map).length) {
    throw new Error('无法解析 Permission.java')
  }
  return map
}

/** @returns {string[]} sorted permission values */
export function parseAllPermissionValues(source) {
  const map = parsePermissionEnum(source)
  return Object.values(map).sort()
}

/** @returns {Record<string, string[]>} role -> sorted permission values */
export function parseRolePermissions(source, permissionEnum) {
  const byRole = {}
  const blocks = source.matchAll(
    /ROLE_PERMISSIONS\.put\(\s*UserRole\.(\w+),\s*EnumSet\.of\(([\s\S]*?)\)\);/g,
  )
  for (const [, role, body] of blocks) {
    const perms = [...body.matchAll(/Permission\.(\w+)/g)]
      .map((m) => {
        const value = permissionEnum[m[1]]
        if (!value) throw new Error(`未知 Permission 常量: ${m[1]}`)
        return value
      })
      .sort()
    byRole[role] = perms
  }
  if (!Object.keys(byRole).length) {
    throw new Error('无法解析 PermissionService.java ROLE_PERMISSIONS')
  }
  return byRole
}

export function loadBackendPermissionManifest() {
  const permissionSource = fs.readFileSync(permissionJavaPath, 'utf8')
  const serviceSource = fs.readFileSync(permissionServicePath, 'utf8')
  const permissionEnum = parsePermissionEnum(permissionSource)
  const permissions = parseAllPermissionValues(permissionSource)
  const byRole = parseRolePermissions(serviceSource, permissionEnum)
  return {
    source: 'backend',
    generatedAt: new Date().toISOString(),
    permissions,
    byRole,
  }
}
