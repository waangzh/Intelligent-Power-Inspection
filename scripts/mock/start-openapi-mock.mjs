#!/usr/bin/env node
/**
 * 基于 OpenAPI 启动 Prism Mock Server（需后端已启动并可访问 /v3/api-docs）。
 *
 *   npm run mock:openapi
 *
 * 小程序对接：
 *   npm run miniprogram:env:openapi
 */
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const specPath = path.join(repoRoot, 'shared/generated/openapi-mock.bundle.yaml')
const port = process.env.MOCK_PORT || '4010'
const backendUrl = process.env.BACKEND_OPENAPI_URL || 'http://localhost:8080/v3/api-docs'

console.log(`Starting Prism on :${port}`)
console.log(`OpenAPI source: ${backendUrl}`)
console.log('Ensure backend is running, then point miniprogram to http://localhost:' + port + '/api/v1')

const child = spawn(
  'npx',
  ['--yes', '@stoplight/prism-cli', 'mock', backendUrl, '-p', port, '--host', '0.0.0.0'],
  { stdio: 'inherit', shell: true, cwd: repoRoot },
)

child.on('exit', (code) => process.exit(code ?? 1))
