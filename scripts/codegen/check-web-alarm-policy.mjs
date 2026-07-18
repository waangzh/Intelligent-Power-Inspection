import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const webSrc = path.join(repoRoot, 'frontend/web/src')

let ok = true

function fail(message) {
  ok = false
  console.error(message)
}

if (fs.existsSync(path.join(webSrc, 'stores/alarmPolicy.ts'))) {
  fail('仍存在 frontend/web/src/stores/alarmPolicy.ts，告警策略应统一走 alarm store + backend API')
}

function scanDir(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) scanDir(full)
    else if (/\.(ts|vue)$/.test(entry.name)) {
      const text = fs.readFileSync(full, 'utf8')
      if (text.includes('pi_alarm_escalation_policy')) {
        fail(`Web 源码仍引用 localStorage 告警策略 key: ${path.relative(repoRoot, full)}`)
      }
      if (text.includes('useAlarmPolicyStore')) {
        fail(`Web 源码仍引用已删除的 useAlarmPolicyStore: ${path.relative(repoRoot, full)}`)
      }
    }
  }
}

scanDir(webSrc)

if (ok) {
  console.log('Web 告警策略校验通过：无 localStorage 策略 store')
}

process.exit(ok ? 0 : 1)
