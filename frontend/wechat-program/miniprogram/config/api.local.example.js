/**
 * 本地开发覆盖配置（不提交 Git）
 *
 * 使用方式：
 *   1. 复制本文件为同目录下的 api.local.js
 *   2. 按需修改 useMock / baseUrl
 */
module.exports = {
  baseUrl: 'http://localhost:8080/api/v1',
  useMock: true,
}
