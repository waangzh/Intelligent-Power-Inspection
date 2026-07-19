#!/usr/bin/env node
/**
 * 基于 OpenAPI 启动 Prism Mock Server。
 *
 * 优先使用仓库内 shared/generated/openapi.json（无需后端运行）；
 * 也可指定 BACKEND_OPENAPI_URL 从运行中的 backend 拉取最新契约。
 *
 *   npm run mock:openapi
 *
 * 小程序对接：
 *   npm run miniprogram:env:mock
 */
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const bundledSpec = path.join(repoRoot, 'shared/generated/openapi.json')
const port = process.env.MOCK_PORT || '4010'
const backendUrl = process.env.BACKEND_OPENAPI_URL

let specArg
if (backendUrl) {
  specArg = backendUrl
  console.log(`OpenAPI source (live): ${backendUrl}`)
} else if (fs.existsSync(bundledSpec)) {
  specArg = bundledSpec
  console.log(`OpenAPI source (bundled): ${path.relative(repoRoot, bundledSpec)}`)
} else {
  specArg = 'http://localhost:8080/v3/api-docs'
  console.warn('缺少 shared/generated/openapi.json，回退到运行中的 backend /v3/api-docs')
}

console.log(`Starting Prism on :${port}`)
console.log('Point miniprogram to http://localhost:' + port + '/api/v1 (npm run miniprogram:env:mock)')

const child = spawn(
  'npx',
  ['--yes', '@stoplight/prism-cli', 'mock', specArg, '-p', port, '--host', '0.0.0.0'],
  { stdio: 'inherit', shell: true, cwd: repoRoot },
)

child.on('exit', (code) => process.exit(code ?? 1))
