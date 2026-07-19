import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const v18Path = path.join(repoRoot, 'backend/src/main/resources/db/migration/V18__domain_hard_constraints.sql')
const workOrderControllerPath = path.join(
  repoRoot,
  'backend/src/main/java/com/powerinspection/workorder/WorkOrderController.java',
)
const userRolePath = path.join(repoRoot, 'backend/src/main/java/com/powerinspection/user/UserRole.java')
const alarmPolicyPath = path.join(
  repoRoot,
  'backend/src/main/java/com/powerinspection/alarm/AlarmWorkOrderPolicyService.java',
)

function extractCheckInValues(source, tableConstraintPrefix) {
  const re = new RegExp(`${tableConstraintPrefix}[\\s\\S]*?CHECK \\((\\w+) IN \\(([^)]+)\\)\\)`, 'm')
  const match = source.match(re)
  if (!match) throw new Error(`无法解析 CHECK 约束: ${tableConstraintPrefix}`)
  return match[2]
    .split(',')
    .map((s) => s.trim().replace(/^'|'$/g, ''))
    .filter(Boolean)
}

function extractJavaEnumValues(source, enumName) {
  const block = source.match(new RegExp(`enum ${enumName}\\s*\\{([\\s\\S]*?)\\}`))
  if (!block) throw new Error(`无法解析 Java enum: ${enumName}`)
  return [...block[1].matchAll(/^\s+([A-Z][A-Z0-9_]*)\s*,?\s*$/gm)].map((m) => m[1])
}

function extractReviewConclusions(source) {
  const match = source.match(
    /!\("RESOLVED"\.equals\(conclusion\)[\s\S]*?"FALSE_ALARM"\.equals\(conclusion\)\)/,
  )
  if (!match) throw new Error('无法解析 WorkOrder review conclusion 枚举')
  return [...match[0].matchAll(/"([A-Z_]+)"\.equals\(conclusion\)/g)].map((m) => m[1])
}

function extractListOfStrings(source, constName) {
  const match = source.match(new RegExp(`${constName}\\s*=\\s*List\\.of\\(([^)]*)\\)`))
  if (!match) throw new Error(`无法解析 ${constName}`)
  return match[1]
    .split(',')
    .map((s) => s.trim().replace(/^"|"$/g, ''))
    .filter(Boolean)
}

/** @returns {Record<string, string[]>} */
export function loadBackendDomainEnums() {
  const v18 = fs.readFileSync(v18Path, 'utf8')
  const workOrderController = fs.readFileSync(workOrderControllerPath, 'utf8')
  const userRole = fs.readFileSync(userRolePath, 'utf8')
  const alarmPolicy = fs.readFileSync(alarmPolicyPath, 'utf8')

  return {
    UserRole: extractJavaEnumValues(userRole, 'UserRole'),
    TaskStatus: extractCheckInValues(v18, 'ALTER TABLE inspection_tasks ADD CONSTRAINT chk_inspection_tasks_status'),
    WorkOrderStatus: extractCheckInValues(v18, 'ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_status'),
    WorkOrderPriority: extractCheckInValues(v18, 'ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_priority'),
    WorkOrderReviewConclusion: extractReviewConclusions(workOrderController),
    AlarmSeverity: extractListOfStrings(alarmPolicy, 'SEVERITIES'),
    RobotStatus: extractCheckInValues(v18, 'ALTER TABLE robots ADD CONSTRAINT chk_robots_status'),
  }
}
