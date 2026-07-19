/**
 * API 配置 — 与网页端共用同一后端
 *
 * mockMode 仅通过构建变量启用（npm run miniprogram:env），不在此文件写死。
 * 本地改 baseUrl 可用 api.local.js（不含 mockMode）。
 */
const defaults = {
  baseUrl: 'http://localhost:8080/api/v1',
  /** none: 真实后端；openapi: 对接 Prism Mock Server */
  mockMode: 'none',
  timeout: 15000,
}

let buildEnv = {}
try {
  buildEnv = require('./build-env.js')
} catch (e) {
  // 未运行 miniprogram:env 时使用默认值
}

let local = {}
try {
  local = require('./api.local.js')
} catch (e) {
  // 无本地覆盖
}

if (local.mockMode !== undefined) {
  console.warn('[power-inspection] api.local.js 中的 mockMode 已废弃且不再生效，请使用: npm run miniprogram:env:mock')
}

const { mockMode: _localMockModeIgnored, ...localOverrides } = local
const merged = { ...defaults, ...buildEnv, ...localOverrides }

module.exports = merged
