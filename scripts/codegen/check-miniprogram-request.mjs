#!/usr/bin/env node
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const requestPath = path.join(
  repoRoot,
  'frontend/wechat-program/miniprogram/utils/request.js',
)
const requestSource = fs.readFileSync(requestPath, 'utf8')

function createHarness(responder, initialStorage = {}) {
  const storage = new Map(Object.entries(initialStorage))
  const requests = []
  const appEvents = []
  const wx = {
    getStorageSync(key) {
      return storage.get(key)
    },
    setStorageSync(key, value) {
      storage.set(key, value)
    },
    removeStorageSync(key) {
      storage.delete(key)
    },
    request(options) {
      requests.push(options)
      const response = responder(options, requests.length)
      if (response.fail) options.fail?.(response.fail)
      else options.success?.(response)
      options.complete?.(response)
      return {}
    },
  }
  const app = {
    applySession(session, options) {
      appEvents.push({ type: 'apply', session, options })
    },
    handleSessionExpired() {
      appEvents.push({ type: 'expired' })
    },
  }
  const module = { exports: {} }
  const context = vm.createContext({
    console,
    wx,
    getApp: () => app,
    module,
    exports: module.exports,
    require(id) {
      if (id === '../config/api') {
        return { baseUrl: 'http://backend.test/api/v1', timeout: 1000 }
      }
      throw new Error(`Unexpected require: ${id}`)
    },
  })
  new vm.Script(`(function (require, module, exports) {\n${requestSource}\n})`, {
    filename: requestPath,
  }).runInContext(context)(context.require, module, module.exports)
  return { api: module.exports, storage, requests, appEvents }
}

async function checkPersistentLoginCookie() {
  const harness = createHarness((options) => {
    assert.equal(options.url, 'http://backend.test/api/v1/auth/login')
    return {
      statusCode: 200,
      header: {},
      cookies: [
        'pi_refresh=remember-token; Path=/api/v1/auth; Max-Age=3600; HttpOnly; SameSite=Lax',
      ],
      data: { code: 0, data: { token: 'access-1' } },
    }
  })

  await harness.api.post('/auth/login', { username: 'demo' })
  const stored = harness.storage.get('pi_refresh_cookie')
  assert.equal(stored.cookie, 'pi_refresh=remember-token')
  assert.ok(stored.expiresAt > Date.now())
}

async function checkRefreshAndRetry() {
  const harness = createHarness(
    (options, call) => {
      if (call === 1) {
        assert.equal(options.url, 'http://backend.test/api/v1/tasks')
        assert.equal(options.header.Authorization, 'Bearer expired-access')
        return { statusCode: 401, header: {}, data: { error: 'Unauthorized' } }
      }
      if (call === 2) {
        assert.equal(options.url, 'http://backend.test/api/v1/auth/refresh')
        assert.equal(options.header.Cookie, 'pi_refresh=remember-token')
        return {
          statusCode: 200,
          header: {},
          cookies: [
            'pi_refresh=rotated-token; Path=/api/v1/auth; Max-Age=7200; HttpOnly; SameSite=Lax',
          ],
          data: {
            code: 0,
            data: {
              token: 'fresh-access',
              user: { id: 'user-1' },
              permissions: ['task:view'],
            },
          },
        }
      }
      assert.equal(call, 3)
      assert.equal(options.url, 'http://backend.test/api/v1/tasks')
      assert.equal(options.header.Authorization, 'Bearer fresh-access')
      return { statusCode: 200, header: {}, data: { code: 0, data: ['ok'] } }
    },
    {
      pi_session: {
        token: 'expired-access',
        user: { id: 'user-1' },
        permissions: ['task:view'],
      },
      pi_refresh_cookie: {
        cookie: 'pi_refresh=remember-token',
        expiresAt: Date.now() + 60_000,
      },
    },
  )

  assert.deepEqual(await harness.api.get('/tasks'), ['ok'])
  assert.equal(harness.storage.get('pi_session').token, 'fresh-access')
  assert.equal(
    harness.storage.get('pi_refresh_cookie').cookie,
    'pi_refresh=rotated-token',
  )
  assert.equal(harness.appEvents[0].type, 'apply')
  assert.equal(harness.appEvents[0].options.reloadPages, undefined)
}

async function checkSessionCookieAndLogout() {
  let call = 0
  const harness = createHarness((options) => {
    call += 1
    if (call === 1) {
      return {
        statusCode: 200,
        header: {
          'Set-Cookie':
            'pi_refresh=session-token; Path=/api/v1/auth; HttpOnly; SameSite=Lax',
        },
        data: { code: 0, data: { token: 'access-1' } },
      }
    }
    assert.equal(options.header.Cookie, 'pi_refresh=session-token')
    return {
      statusCode: 200,
      header: {
        'set-cookie':
          'pi_refresh=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax',
      },
      data: { code: 0, data: null },
    }
  })

  await harness.api.post('/auth/login', {})
  assert.equal(harness.storage.has('pi_refresh_cookie'), false)
  await harness.api.post('/auth/logout')
  assert.equal(harness.storage.has('pi_refresh_cookie'), false)
}

await checkPersistentLoginCookie()
await checkRefreshAndRetry()
await checkSessionCookieAndLogout()
console.log('小程序请求校验通过：refresh cookie 保存、轮换、401 重试与注销清理正常')
