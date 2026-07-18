#!/usr/bin/env node
/**
 * 根据构建环境变量生成小程序 config/build-env.js（不提交 Git）。
 *
 *   USE_MOCK=true  npm run miniprogram:env        # 演示模式
 *   npm run miniprogram:env                       # 默认 useMock=false
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

const useMock = parseBool(process.env.USE_MOCK, false)
const mockMode = process.env.MOCK_MODE || (useMock ? 'storage' : 'none')
const baseUrl = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

const content = `/** AUTO-GENERATED — 请勿手工编辑。运行: npm run miniprogram:env */
module.exports = {
  useMock: ${useMock},
  mockMode: '${mockMode}',
  baseUrl: '${baseUrl.replace(/'/g, "\\'")}',
}
`

fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, content, 'utf8')
console.log(`Wrote ${path.relative(repoRoot, outPath)} (useMock=${useMock}, mockMode=${mockMode})`)
if (useMock && mockMode === 'storage') {
  console.warn('提示：无后端演示优先使用 npm run miniprogram:env:openapi + npm run mock:openapi，避免 wx.storage 业务分叉')
}
