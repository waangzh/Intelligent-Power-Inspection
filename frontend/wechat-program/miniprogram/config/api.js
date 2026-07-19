/**
 * API 配置 — 与网页端共用同一后端
 *
 * useMock 仅通过构建变量启用（npm run miniprogram:env），不在此文件写死 true。
 * 本地改 baseUrl 可用 api.local.js（不含 useMock）。
 */
const defaults = {
  baseUrl: 'http://localhost:8080/api/v1',
  useMock: false,
  /** storage: wx.storage 本地演示；openapi: 对接 Prism Mock Server */
  mockMode: 'none',
  timeout: 15000,
}

let buildEnv = {}
try {
  buildEnv = require('./build-env.js')
} catch (e) {
  // 未运行 miniprogram:env 时使用默认值（useMock=false）
}

let local = {}
try {
  local = require('./api.local.js')
} catch (e) {
  // 无本地覆盖
}

if (local.useMock !== undefined) {
  console.warn('[power-inspection] api.local.js 中的 useMock 已废弃，请使用: USE_MOCK=true npm run miniprogram:env')
}

const merged = { ...defaults, ...buildEnv, ...local }
if (local.useMock !== undefined) {
  merged.useMock = local.useMock
}

module.exports = merged
