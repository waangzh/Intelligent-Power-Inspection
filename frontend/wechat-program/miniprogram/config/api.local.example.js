/**
 * 本地开发覆盖配置（不提交 Git）
 *
 * 仅用于覆盖 baseUrl 等联调参数。
 * Mock 演示请使用构建变量，不要在此设置 useMock：
 *   USE_MOCK=true npm run miniprogram:env
 */
module.exports = {
  baseUrl: 'http://localhost:8080/api/v1',
}
