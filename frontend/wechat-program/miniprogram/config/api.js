/**
 * API 配置 — 与网页端共用同一后端
 * 默认对接真实后端；本地演示请复制 api.local.example.js 为 api.local.js
 */
const defaults = {
  /** 后端 REST API 根路径，与 web 端 docs/API.md 一致 */
  baseUrl: 'http://localhost:8080/api/v1',

  /**
   * false: 请求共用后端 HTTP 接口（默认）
   * true:  使用本地 mock（wx.storage 演示，需 api.local.js 显式开启）
   */
  useMock: false,

  /** 请求超时（毫秒） */
  timeout: 15000,
}

let local = {}
try {
  local = require('./api.local.js')
} catch (e) {
  // 无本地覆盖时使用默认值
}

module.exports = { ...defaults, ...local }
