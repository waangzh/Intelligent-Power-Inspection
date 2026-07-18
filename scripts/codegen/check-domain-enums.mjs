import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadBackendDomainEnums } from './parse-domain.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const manifestPath = path.join(repoRoot, 'shared/generated/domain-enums.json')
const webOutPath = path.join(repoRoot, 'frontend/web/src/generated/domain-enums.ts')
const mpOutPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/domain-enums.js')

function parseGeneratedJson(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`缺少生成文件: ${path.relative(repoRoot, filePath)}，请先运行 npm run domain:generate`)
  }
  return JSON.parse(fs.readFileSync(filePath, 'utf8'))
}

function extractValuesBlock(source, constName) {
  const match = source.match(new RegExp(`(?:export )?const ${constName} = \\[([\\s\\S]*?)\\]`))
  if (!match) throw new Error(`无法解析 ${constName}`)
  return [...match[1].matchAll(/'([^']+)'/g)].map((m) => m[1]).sort()
}

function assertSame(label, actual, expected) {
  const a = [...actual].sort().join(',')
  const b = [...expected].sort().join(',')
  if (a !== b) {
    throw new Error(`[${label}] 与 backend 不一致\n  实际: ${a || '(无)'}\n  期望: ${b || '(无)'}`)
  }
}

function checkGeneratedFile(filePath, backendEnums, prefix) {
  const source = fs.readFileSync(filePath, 'utf8')
  for (const [enumName, expected] of Object.entries(backendEnums)) {
    const constName = `${enumName.replace(/([A-Z])/g, '_$1').replace(/^_/, '').toUpperCase()}_VALUES`
    const actual = extractValuesBlock(source, constName)
    assertSame(`${path.relative(repoRoot, filePath)} ${constName}`, actual, expected)
  }
}

let ok = true
try {
  const backend = loadBackendDomainEnums()
  const committed = parseGeneratedJson(manifestPath)
  for (const [enumName, expected] of Object.entries(backend)) {
    assertSame(`domain-enums.json [${enumName}]`, committed.enums?.[enumName] || [], expected)
  }
  checkGeneratedFile(webOutPath, backend, 'web')
  checkGeneratedFile(mpOutPath, backend, 'mp')
  console.log('领域枚举校验通过：backend ↔ shared/generated ↔ Web ↔ 小程序')
} catch (err) {
  ok = false
  console.error(err.message || err)
  console.error('\n请运行: npm run domain:generate')
}

process.exit(ok ? 0 : 1)
