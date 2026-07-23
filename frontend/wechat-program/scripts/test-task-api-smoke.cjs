/**
 * 远程 API 冒烟：验证 Bridge 任务 eligibility / start / dispatch 行为
 * 运行：node frontend/wechat-program/scripts/test-task-api-smoke.cjs
 */
const BASE = process.env.PI_API_BASE || 'http://112.124.49.152:8080/api/v1'
const TASK_ID = process.env.PI_TASK_ID || 'task_1784789936937_7r5nqz3'

async function json(method, path, { token, body, headers = {} } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    parsed = { raw: text }
  }
  return { status: res.status, body: parsed }
}

function assert(name, ok, detail = '') {
  console.log(ok ? `  ✓ ${name}` : `  ✗ ${name}${detail ? `: ${detail}` : ''}`)
  return ok
}

async function main() {
  let failed = 0
  console.log(`API base: ${BASE}`)

  const login = await json('POST', '/auth/login', {
    body: { username: 'dispatcher', password: 'Disp@123', remember: true },
  })
  if (!assert('login success', login.body.code === 0, login.body.message)) failed += 1
  const token = login.body.data?.token
  if (!token) {
    console.error('No token, abort')
    process.exit(1)
  }

  const elig = await json('GET', `/tasks/${encodeURIComponent(TASK_ID)}/start-eligibility`, { token })
  if (!assert('start-eligibility HTTP 200', elig.status === 200)) failed += 1
  const d = elig.body.data || {}
  console.log(`    eligible=${d.eligible}`)
  console.log(`    remote=${d.remoteImmediateStartEligible} → ${d.remoteImmediateStartIneligibleReason || '-'}`)
  console.log(`    local=${d.localConfirmStartEligible} → ${d.localConfirmStartIneligibleReason || '-'}`)
  if (!assert('execution status CREATED', d.status === 'CREATED', d.status)) failed += 1
  if (!assert('has executionId (Bridge task)', !!d.executionId)) failed += 1

  const dispatch = await json('POST', `/tasks/${encodeURIComponent(TASK_ID)}/dispatch`, { token })
  if (!assert('dispatch returns 409 for Bridge task', dispatch.status === 409, dispatch.body.message)) failed += 1
  else console.log(`    dispatch message: ${dispatch.body.message}`)

  const start = await json('POST', `/tasks/${encodeURIComponent(TASK_ID)}/start`, {
    token,
    headers: { 'Idempotency-Key': `smoke-${Date.now()}` },
    body: { startMode: 'REMOTE_IMMEDIATE' },
  })
  // 机器人离线时应 409；若在线可能 202
  const startOk = start.status === 409 || start.status === 202 || start.status === 200
  if (!assert(`start responds (${start.status})`, startOk, start.body.message)) failed += 1
  else console.log(`    start message: ${start.body.message || 'accepted'}`)

  if (d.eligible === false && d.remoteImmediateStartIneligibleReason) {
    if (!assert(
      'start 409 matches eligibility reason',
      start.status === 409 && start.body.message === d.remoteImmediateStartIneligibleReason,
      `start=${start.body.message}`,
    )) failed += 1
  }

  const routes = await json('GET', '/routes?page=0&size=5', { token })
  const routeId = routes.body.data?.items?.[0]?.id
  if (routeId) {
    const revisions = await json('GET', `/routes/${encodeURIComponent(routeId)}/revisions`, { token })
    if (!assert('list route revisions', revisions.status === 200 && Array.isArray(revisions.body.data))) failed += 1
    else console.log(`    route ${routeId} revisions: ${revisions.body.data.length}`)
  }

  console.log('')
  console.log(failed ? `FAILED (${failed} checks)` : 'ALL API SMOKE CHECKS PASSED')
  process.exit(failed ? 1 : 0)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
