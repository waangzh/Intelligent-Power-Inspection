import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadBackendDomainEnums } from './parse-domain.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const manifestPath = path.join(repoRoot, 'shared/generated/domain-enums.json')
const webOutPath = path.join(repoRoot, 'frontend/web/src/generated/domain-enums.ts')
const mpOutPath = path.join(repoRoot, 'frontend/wechat-program/miniprogram/generated/domain-enums.js')
const openapiPath = path.join(repoRoot, 'shared/generated/openapi-domain-enums.yaml')

const banner = `/** AUTO-GENERATED — 请勿手工编辑。运行: npm run domain:generate */\n`

/** UI 中文标签 — 与 backend 枚举值分离维护，codegen 校验值集合一致 */
const LABELS = {
  UserRole: {
    ADMIN: '管理员',
    DISPATCHER: '调度员',
    VIEWER: '观察员',
  },
  TaskStatus: {
    CREATED: '已创建',
    DISPATCHED: '已下发',
    RUNNING: '执行中',
    PAUSED: '已暂停',
    MANUAL_TAKEOVER: '人工接管',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  },
  WorkOrderStatus: {
    PENDING: '待处理',
    PROCESSING: '处理中',
    REVIEW: '待复核',
    CLOSED: '已关闭',
    CANCELLED: '已取消',
  },
  WorkOrderPriority: {
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    URGENT: '紧急',
  },
  WorkOrderReviewConclusion: {
    RESOLVED: '已消缺',
    PARTIALLY_RESOLVED: '部分消缺',
    UNRESOLVED: '未消缺',
    FALSE_ALARM: '误报',
  },
  AlarmSeverity: {
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    CRITICAL: '紧急',
  },
  RobotStatus: {
    ONLINE: '在线',
    OFFLINE: '离线',
    BUSY: '忙碌',
  },
}

function assertLabelsMatch(enumName, values) {
  const labels = LABELS[enumName] || {}
  for (const value of values) {
    if (!labels[value]) {
      throw new Error(`domain labels 缺少 ${enumName}.${value}，请在 scripts/codegen/generate-domain-enums.mjs 补充`)
    }
  }
  for (const key of Object.keys(labels)) {
    if (!values.includes(key)) {
      throw new Error(`domain labels 多余项 ${enumName}.${key}，backend 中已不存在`)
    }
  }
}

function writeManifest(enums) {
  const manifest = {
    source: 'backend',
    generatedAt: new Date().toISOString(),
    enums,
    labels: LABELS,
  }
  fs.mkdirSync(path.dirname(manifestPath), { recursive: true })
  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
}

function writeOpenApi(enums) {
  const schemas = Object.entries(enums)
    .map(([name, values]) => {
      const enumLines = values.map((v) => `        - ${v}`).join('\n')
      return `    ${name}:\n      type: string\n      enum:\n${enumLines}`
    })
    .join('\n')
  const yaml = `# AUTO-GENERATED — 领域枚举 OpenAPI 片段（由 backend 约束生成）
components:
  schemas:
${schemas}
`
  fs.writeFileSync(openapiPath, yaml, 'utf8')
}

function enumConstName(enumName) {
  return `${enumName.replace(/([A-Z])/g, '_$1').replace(/^_/, '').toUpperCase()}_VALUES`
}

function enumLabelName(enumName) {
  return `${enumName.replace(/([A-Z])/g, '_$1').replace(/^_/, '').toUpperCase()}_LABELS`
}

function writeWebTs(enums) {
  const blocks = Object.entries(enums).map(([name, values]) => {
    assertLabelsMatch(name, values)
    const union = values.map((v) => `'${v}'`).join(' | ')
    const valuesConst = enumConstName(name)
    const labelsConst = enumLabelName(name)
    const labelEntries = values.map((v) => `  ${v}: '${LABELS[name][v]}',`).join('\n')
    return `export type ${name} = ${union}

export const ${valuesConst} = [
${values.map((v) => `  '${v}',`).join('\n')}
] as const

export const ${labelsConst}: Record<${name}, string> = {
${labelEntries}
}`
  })

  fs.mkdirSync(path.dirname(webOutPath), { recursive: true })
  fs.writeFileSync(webOutPath, `${banner}${blocks.join('\n\n')}\n`, 'utf8')
}

function toSnakeUpper(name) {
  return name.replace(/([A-Z])/g, '_$1').replace(/^_/, '').toUpperCase()
}

function writeMiniProgramJs(enums) {
  const exports = []
  const moduleExports = []

  for (const [name, values] of Object.entries(enums)) {
    assertLabelsMatch(name, values)
    const constName = `${toSnakeUpper(name)}_VALUES`
    const labelName = `${toSnakeUpper(name)}_LABELS`
    exports.push(`const ${constName} = [\n${values.map((v) => `  '${v}',`).join('\n')}\n]`)
    exports.push(`const ${labelName} = {\n${values.map((v) => `  ${v}: '${LABELS[name][v]}',`).join('\n')}\n}`)
    moduleExports.push(constName, labelName)
  }

  const content = `${banner}${exports.join('\n\n')}

module.exports = {
${moduleExports.map((n) => `  ${n},`).join('\n')}
}
`
  fs.mkdirSync(path.dirname(mpOutPath), { recursive: true })
  fs.writeFileSync(mpOutPath, content, 'utf8')
}

const enums = loadBackendDomainEnums()
for (const [name, values] of Object.entries(enums)) {
  assertLabelsMatch(name, values)
}

writeManifest(enums)
writeOpenApi(enums)
writeWebTs(enums)
writeMiniProgramJs(enums)

console.log('Generated domain enum artifacts from backend:')
console.log(`  ${path.relative(repoRoot, manifestPath)}`)
console.log(`  ${path.relative(repoRoot, openapiPath)}`)
console.log(`  ${path.relative(repoRoot, webOutPath)}`)
console.log(`  ${path.relative(repoRoot, mpOutPath)}`)
