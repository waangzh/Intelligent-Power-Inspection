#!/usr/bin/env node
/**
 * 从运行中的 backend 导出 OpenAPI JSON。
 *
 *   npm run openapi:export
 *   BACKEND_OPENAPI_URL=http://host:8080/v3/api-docs npm run openapi:export
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const outPath = path.join(repoRoot, 'shared/generated/openapi.json')
const url = process.env.BACKEND_OPENAPI_URL || 'http://localhost:8080/v3/api-docs'

const response = await fetch(url)
if (!response.ok) {
  throw new Error(`无法导出 OpenAPI (${response.status}): ${url}\n请先启动 backend，或设置 BACKEND_OPENAPI_URL`)
}

const spec = await response.json()
fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, `${JSON.stringify(spec, null, 2)}\n`, 'utf8')
console.log(`Exported OpenAPI → ${path.relative(repoRoot, outPath)}`)
