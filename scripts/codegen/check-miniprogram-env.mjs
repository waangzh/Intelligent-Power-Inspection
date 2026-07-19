import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const apiJsPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/config/api.js')
const servicesPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/services/index.js')
const buildEnvPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/config/build-env.js')

let ok = true

try {
  const apiSource = fs.readFileSync(apiJsPath, 'utf8')
  if (/mockMode:\s*['"]openapi['"]/.test(apiSource) && !apiSource.includes('build-env.js')) {
    throw new Error('api.js 不得硬编码 mockMode: openapi，请使用 npm run miniprogram:env 生成 build-env.js')
  }
  if (!apiSource.includes('build-env.js')) {
    throw new Error('api.js 必须读取 build-env.js（构建变量）')
  }
  if (/merged\.mockMode\s*=\s*local/.test(apiSource) || /\.\.\.local\s*}/.test(apiSource)) {
    throw new Error('api.js 不得允许 api.local.js 覆盖 mockMode，演示模式只能通过构建变量启用')
  }

  const servicesSource = fs.readFileSync(servicesPath, 'utf8')
  if (/mock\/store/.test(servicesSource) || /\buseMock\b/.test(servicesSource)) {
    throw new Error('services/index.js 不得再引用 mock/store 或 useMock')
  }

  if (fs.existsSync(buildEnvPath)) {
    const buildEnv = fs.readFileSync(buildEnvPath, 'utf8')
    if (!/AUTO-GENERATED/.test(buildEnv)) {
      throw new Error('build-env.js 应为 npm run miniprogram:env 生成的文件')
    }
    if (/\buseMock\b/.test(buildEnv)) {
      throw new Error('build-env.js 已移除 useMock，请重新运行 npm run miniprogram:env')
    }
  }

  console.log('小程序 API 配置校验通过：演示模式仅通过 mockMode 构建变量启用')
} catch (err) {
  ok = false
  console.error(err.message || err)
}

process.exit(ok ? 0 : 1)
