/**
 * 本地开发覆盖配置（不提交 Git）
 *
 * 仅用于覆盖 baseUrl 等联调参数。
 * OpenAPI 演示请使用构建变量，不要在此设置 mockMode：
 *   npm run miniprogram:env:mock
 */
module.exports = {
  baseUrl: 'http://112.124.49.152:8080/api/v1',
}
