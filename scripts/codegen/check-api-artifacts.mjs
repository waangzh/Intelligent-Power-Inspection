import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const required = [
  'shared/generated/openapi.json',
  'shared/generated/api-paths.json',
  'shared/generated/dto-schemas.json',
  'frontend/web/src/generated/api-types.ts',
  'frontend/web/src/generated/api-client.ts',
  'frontend/web/src/generated/api-paths.ts',
  'frontend/web/src/generated/dto-types.ts',
  'frontend/wechat-program/miniprogram/generated/api-client.js',
  'frontend/wechat-program/miniprogram/generated/api-paths.js',
]

let ok = true
for (const rel of required) {
  const full = path.join(repoRoot, rel)
  if (!fs.existsSync(full)) {
    ok = false
    console.error(`缺少 OpenAPI 生成物: ${rel}，请运行 npm run openapi:export && npm run api:generate`)
  }
}

if (ok) {
  const spec = JSON.parse(fs.readFileSync(path.join(repoRoot, 'shared/generated/openapi.json'), 'utf8'))
  const pathsDoc = JSON.parse(fs.readFileSync(path.join(repoRoot, 'shared/generated/api-paths.json'), 'utf8'))
  const apiPathCount = Object.keys(spec.paths || {}).filter((p) => p.startsWith('/api/v1/')).length
  if (pathsDoc.paths?.length !== apiPathCount) {
    ok = false
    console.error(`api-paths.json 与 openapi.json 不一致 (${pathsDoc.paths?.length} vs ${apiPathCount})，请重新运行 npm run api:generate`)
  } else {
    console.log(`OpenAPI 生成物校验通过（${apiPathCount} 个 /api/v1 路径）`)
  }
}

process.exit(ok ? 0 : 1)
