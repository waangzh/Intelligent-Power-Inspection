#!/usr/bin/env node
/**
 * 根据构建环境变量生成小程序 config/build-env.js（不提交 Git）。
 *
 *   npm run miniprogram:env              # 默认 mockMode=none
 *   npm run miniprogram:env:mock         # mockMode=openapi → :4010
 *
 * 可选：API_BASE_URL=https://host/api/v1
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const outPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/config/build-env.js')

function parseBool(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback
  const normalized = String(value).trim().toLowerCase()
  if (['1', 'true', 'yes', 'on'].includes(normalized)) return true
  if (['0', 'false', 'no', 'off'].includes(normalized)) return false
  throw new Error(`无法解析布尔环境变量: ${value}`)
}

const useMockLegacy = parseBool(process.env.USE_MOCK, false)
let mockMode = process.env.MOCK_MODE || (useMockLegacy ? 'openapi' : 'none')
if (mockMode === 'storage') {
  throw new Error('MOCK_MODE=storage 已移除，请使用 npm run miniprogram:env:mock（OpenAPI Prism）')
}
if (!['none', 'openapi'].includes(mockMode)) {
  throw new Error(`不支持的 MOCK_MODE=${mockMode}，仅允许 none / openapi`)
}

const defaultBaseUrl = mockMode === 'openapi'
  ? 'http://localhost:4010/api/v1'
  : 'http://localhost:8080/api/v1'
const baseUrl = process.env.API_BASE_URL || defaultBaseUrl

const content = `/** AUTO-GENERATED — 请勿手工编辑。运行: npm run miniprogram:env */
module.exports = {
  mockMode: '${mockMode}',
  baseUrl: '${baseUrl.replace(/'/g, "\\'")}',
}
`

fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, content, 'utf8')
console.log(`Wrote ${path.relative(repoRoot, outPath)} (mockMode=${mockMode})`)
if (mockMode === 'openapi') {
  console.warn('OpenAPI Mock 模式：请先运行 npm run mock:openapi，再编译小程序')
}
