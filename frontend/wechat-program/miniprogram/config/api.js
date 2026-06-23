/**
 * API 配置 — 与网页端共用同一后端
 * 开发时在微信开发者工具中可修改 baseUrl
 */
module.exports = {
  /** 后端 REST API 根路径，与 web 端 docs/API.md 第 9 章一致 */
  baseUrl: 'http://localhost:8080/api/v1',

  /**
   * true: 使用本地 mock（无后端时演示）
   * false: 请求共用后端 HTTP 接口
   */
  useMock: true,

  /** 请求超时（毫秒） */
  timeout: 15000,
}
