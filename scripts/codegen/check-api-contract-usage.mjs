#!/usr/bin/env node
/**
 * 契约校验：扫描 Web / 小程序手写的 http.get/post/put/patch/delete 调用，
 * 核对路径 + 方法是否真实存在于 shared/generated/openapi.json（后端权威契约）。
 *
 * 目的：即使还没把每个接口都迁移到 openapiClient，也不允许手写调用与后端契约悄悄分叉
 * （例如小程序误用 PUT 调用一个后端只支持 PATCH 的接口）。
 *
 *   npm run api:contract:check
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const specPath = path.join(repoRoot, 'shared/generated/openapi.json')

const targets = [
  'frontend/web/src/api/resources.ts',
  'frontend/web/src/api/profile.ts',
  'frontend/web/src/api/auth.ts',
]

const mpServicesPath = 'frontend/wechat-program/miniprogram/services/index.js'

/** http 方法别名 → 标准 HTTP method */
const METHOD_ALIAS = {
  get: 'get',
  post: 'post',
  postForm: 'post',
  put: 'put',
  patch: 'patch',
  delete: 'delete',
  del: 'delete',
}

if (!fs.existsSync(specPath)) {
  console.warn('跳过契约用法校验：缺少 shared/generated/openapi.json，请先运行 npm run openapi:export && npm run api:generate')
  process.exit(0)
}

const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'))
const specPaths = spec.paths || {}

/** 将 OpenAPI 路径模板拆成 segments，{param} 记为通配 */
function splitTemplate(apiPath) {
  return apiPath.split('/').filter(Boolean).map((seg) => ({
    isParam: seg.startsWith('{') && seg.endsWith('}'),
    value: seg,
  }))
}

const contractEntries = []
for (const [apiPath, methods] of Object.entries(specPaths)) {
  if (!apiPath.startsWith('/api/v1/')) continue
  const segments = splitTemplate(apiPath)
  for (const method of Object.keys(methods)) {
    contractEntries.push({ apiPath, method: method.toLowerCase(), segments })
  }
}

/**
 * 将调用点里的路径字符串（可能含 `${...}` 模板插值）规整成 segments。
 * 规则：
 *  - `${expr}` 紧跟在 '/' 之后（或字符串开头）→ 视为路径参数占位符
 *  - `${expr}` 紧跟在其它字符之后（无 '/' 分隔）→ 视为查询串等后缀，直接截断，不再处理后面的内容
 */
function normalizeCallPath(raw) {
  const regex = /\$\{[^}]*\}/g
  let result = ''
  let lastIndex = 0
  let match
  while ((match = regex.exec(raw))) {
    const literalBefore = raw.slice(lastIndex, match.index)
    const candidate = result + literalBefore
    const precededBySlash = candidate.endsWith('/') || candidate === ''
    if (precededBySlash) {
      result = `${candidate}__PARAM__`
      lastIndex = regex.lastIndex
    } else {
      result = candidate
      lastIndex = raw.length
      break
    }
  }
  result += raw.slice(lastIndex)
  return result
}

function toSegments(normalizedPath) {
  const full = `/api/v1${normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`}`
  return full.split('/').filter(Boolean).map((seg) => ({
    isParam: seg === '__PARAM__',
    value: seg,
  }))
}

function matches(callSegments, method) {
  return contractEntries.some((entry) => {
    if (entry.method !== method) return false
    if (entry.segments.length !== callSegments.length) return false
    return entry.segments.every((entrySeg, i) => {
      const callSeg = callSegments[i]
      if (entrySeg.isParam) return true
      if (callSeg.isParam) return false
      return entrySeg.value === callSeg.value
    })
  })
}

// 允许 TypeScript 泛型：http.get<T>(...) / http.post<PageResult<T>>(...)
const CALL_REGEX = /\bhttp\.(get|post|put|patch|delete|del|postForm)(?:<[^>]*>)?\(\s*(`[^`]*`|'[^']*'|"[^"]*")/g

let ok = true
let checked = 0
const byFile = {}

for (const rel of targets) {
  const full = path.join(repoRoot, rel)
  if (!fs.existsSync(full)) continue
  const source = fs.readFileSync(full, 'utf8')
  byFile[rel] = 0

  let m
  CALL_REGEX.lastIndex = 0
  while ((m = CALL_REGEX.exec(source))) {
    const alias = m[1]
    const method = METHOD_ALIAS[alias]
    const literal = m[2].slice(1, -1)
    if (!literal.startsWith('/')) continue // 相对占位符或非路径参数，跳过

    const normalized = normalizeCallPath(literal)
    const callSegments = toSegments(normalized)
    checked += 1
    byFile[rel] += 1

    if (!matches(callSegments, method)) {
      ok = false
      const lineNo = source.slice(0, m.index).split('\n').length
      console.error(
        `${rel}:${lineNo}  http.${alias}('${literal}') 未匹配到后端契约 (${method.toUpperCase()} /api/v1${normalized})`,
      )
    }
  }
}

const mpServicesFull = path.join(repoRoot, mpServicesPath)
if (fs.existsSync(mpServicesFull)) {
  const mpServicesSource = fs.readFileSync(mpServicesFull, 'utf8')
  if (/\bhttp\.(get|post|put|patch|delete|del)\(/.test(mpServicesSource)) {
    ok = false
    console.error(`${mpServicesPath} 不得手写 http 调用，请使用 generated/api-client 的 services 别名`)
  } else {
    console.log('小程序 services 层校验通过：无手写 http 调用')
  }
}

if (ok) {
  const breakdown = Object.entries(byFile)
    .filter(([, n]) => n > 0)
    .map(([file, n]) => `${path.basename(file)}=${n}`)
    .join(', ')
  console.log(`API 契约用法校验通过（检查 ${checked} 处手写调用，均匹配 openapi.json${breakdown ? `；${breakdown}` : ''}）`)
} else {
  console.error('\n提示：如确认后端已支持该接口，请重新运行 npm run api:export-and-generate 同步 openapi.json；否则请修正调用的路径/方法。')
}

process.exit(ok ? 0 : 1)
