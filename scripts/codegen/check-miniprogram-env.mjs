import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const apiJsPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/config/api.js')
const buildEnvPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/config/build-env.js')

let ok = true

try {
  const apiSource = fs.readFileSync(apiJsPath, 'utf8')
  if (/useMock:\s*true/.test(apiSource)) {
    throw new Error('api.js 不得硬编码 useMock: true，请使用 npm run miniprogram:env 生成 build-env.js')
  }
  if (!apiSource.includes('build-env.js')) {
    throw new Error('api.js 必须读取 build-env.js（构建变量）')
  }
  if (fs.existsSync(buildEnvPath)) {
    const buildEnv = fs.readFileSync(buildEnvPath, 'utf8')
    if (!/AUTO-GENERATED/.test(buildEnv)) {
      throw new Error('build-env.js 应为 npm run miniprogram:env 生成的文件')
    }
  }
  console.log('小程序 API 配置校验通过：Mock 仅通过构建变量启用')
} catch (err) {
  ok = false
  console.error(err.message || err)
}

process.exit(ok ? 0 : 1)
