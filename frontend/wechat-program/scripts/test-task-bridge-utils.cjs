/**
 * Node 侧单元测试：task-bridge / task-start 纯逻辑
 * 运行：node frontend/wechat-program/scripts/test-task-bridge-utils.cjs
 */
const path = require('node:path')
const fs = require('node:fs')

function loadMiniprogramModule(relativePath) {
  const filePath = path.join(__dirname, '..', 'miniprogram', relativePath)
  const module = { exports: {} }
  // 小程序 utils 为 CommonJS；绕过仓库根 package.json 的 "type":"module"
  const fn = new Function('module', 'exports', 'require', '__dirname', '__filename', fs.readFileSync(filePath, 'utf8'))
  fn(module, module.exports, require, path.dirname(filePath), filePath)
  return module.exports
}

const bridge = loadMiniprogramModule('utils/task-bridge.js')
const start = loadMiniprogramModule('utils/task-start.js')

let passed = 0
let failed = 0

function assert(name, condition) {
  if (condition) {
    passed += 1
    console.log(`  ✓ ${name}`)
  } else {
    failed += 1
    console.error(`  ✗ ${name}`)
  }
}

function assertEq(name, actual, expected) {
  assert(name, actual === expected)
}

function assertThrows(name, fn, code) {
  try {
    fn()
    failed += 1
    console.error(`  ✗ ${name} (expected throw)`)
  } catch (err) {
    const ok = code ? err.code === code : true
    if (ok) {
      passed += 1
      console.log(`  ✓ ${name}`)
    } else {
      failed += 1
      console.error(`  ✗ ${name} (wrong error: ${err.code})`)
    }
  }
}

console.log('task-bridge')
{
  const hash1 = '1'.repeat(64)
  const hashB = 'b'.repeat(64)
  const revisions = [1, 2].map((revisionNo) => ({
    id: `rev-${revisionNo}`,
    routeId: 'route-1',
    revisionNo,
    contentSha256: `${revisionNo}`.repeat(64),
    mapAssetId: 'map-1',
    mapImageSha256: hashB,
    validationReport: { valid: true },
  }))
  const deployments = [{
    id: 'dep-1',
    routeRevisionId: 'rev-1',
    robotId: 'robot-1',
    state: 'READY_FOR_ROBOT',
    routeContentSha256: hash1,
    mapImageSha256: hashB,
  }]

  assertEq('latestReadyRevision picks rev-1', bridge.latestReadyRevision(revisions, deployments, 'robot-1')?.id, 'rev-1')
  assertEq(
    'latestReadyRevision rejects hash mismatch',
    bridge.latestReadyRevision(revisions, [{ ...deployments[0], routeContentSha256: 'x'.repeat(64) }], 'robot-1'),
    undefined,
  )
  assertEq('isBridgeTask with executionId', bridge.isBridgeTask({ executionId: 'e1' }), true)
  assertEq('isBridgeTask simulation', bridge.isBridgeTask({ routeId: 'r1' }), false)
  assertEq('launchButtonLabel bridge', bridge.launchButtonLabel({ executionId: 'e1' }), '启动')
  assertEq('launchButtonLabel sim', bridge.launchButtonLabel({ routeId: 'r1' }), '下发')
  assertEq('resolveTaskStatus prefers execution', bridge.resolveTaskStatus({ status: 'CREATED' }, { status: 'RUNNING' }), 'RUNNING')
  assertEq('canLaunchTask CREATED', bridge.canLaunchTask({}, { status: 'CREATED' }), true)
  assertEq('canLaunchTask RUNNING', bridge.canLaunchTask({}, { status: 'RUNNING' }), false)
  assertEq('isActiveTask RUNNING', bridge.isActiveTask({}, { status: 'RUNNING' }), true)
  assertEq('readyRevisionLabel', bridge.readyRevisionLabel({ revisionNo: 1, id: 'rev-x' }), '第 1 版（rev-x）')
}

console.log('task-start')
{
  const eligible = {
    remoteImmediateStartEligible: true,
    localConfirmStartEligible: false,
    remoteImmediateStartIneligibleReason: null,
    localConfirmStartIneligibleReason: '机器人当前离线',
  }
  assertEq('isStartModeEligible remote', start.isStartModeEligible(eligible, 'REMOTE_IMMEDIATE'), true)
  assertEq('isStartModeEligible local', start.isStartModeEligible(eligible, 'LOCAL_CONFIRM'), false)
  assertEq('startModeReason local', start.startModeReason(eligible, 'LOCAL_CONFIRM'), '机器人当前离线')

  const options = start.buildStartModeOptions(eligible, { canStartRemote: true, canStartLocal: true })
  assertEq('buildStartModeOptions count', options.length, 2)
  assertEq('remote option eligible', options[0].eligible, true)
  assertEq('local option ineligible', options[1].eligible, false)

  start.assertStartEligible(eligible, 'REMOTE_IMMEDIATE')
  assertThrows('assertStartEligible blocks local', () => start.assertStartEligible(eligible, 'LOCAL_CONFIRM'), 'START_INELIGIBLE')

  const blocked = start.formatStartBlockMessage(eligible, options.filter((o) => !o.eligible))
  assert('formatStartBlockMessage mentions offline', blocked.includes('机器人当前离线'))
}

console.log('')
console.log(`Result: ${passed} passed, ${failed} failed`)
process.exit(failed ? 1 : 0)
