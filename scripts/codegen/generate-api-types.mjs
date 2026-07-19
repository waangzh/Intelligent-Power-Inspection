#!/usr/bin/env node
/**
 * 由 shared/generated/openapi.json 生成 Web 端 API 类型（需先 openapi:export）。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { spawnSync } from 'node:child_process'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const specPath = path.join(repoRoot, 'shared/generated/openapi.json')
const webOut = path.join(repoRoot, 'frontend/web/src/generated/api-types.ts')
const mpOut = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/api-types.d.ts')

if (!fs.existsSync(specPath)) {
  console.warn('跳过 api:generate：缺少 shared/generated/openapi.json，请先运行 npm run openapi:export（需 backend 运行）')
  process.exit(0)
}

const result = spawnSync(
  'npx',
  ['--yes', 'openapi-typescript', pathToFileURL(specPath).href, '-o', webOut],
  { cwd: repoRoot, stdio: 'inherit', shell: true },
)
if (result.status !== 0) process.exit(result.status ?? 1)

const banner = '/** AUTO-GENERATED — 请勿手工编辑。运行: npm run api:generate */\n'
const webContent = fs.readFileSync(webOut, 'utf8')
if (!webContent.startsWith(banner.trim().slice(0, 20))) {
  fs.writeFileSync(webOut, `${banner}${webContent}`, 'utf8')
}

fs.mkdirSync(path.dirname(mpOut), { recursive: true })
fs.writeFileSync(
  mpOut,
  `${banner}/** @typedef {import('../../../../web/src/generated/api-types').components} OpenApiComponents */\n`,
  'utf8',
)

console.log(`Generated API types:`)
console.log(`  ${path.relative(repoRoot, webOut)}`)
console.log(`  ${path.relative(repoRoot, mpOut)}`)

await import('./generate-api-client.mjs')
