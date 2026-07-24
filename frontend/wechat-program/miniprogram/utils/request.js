const apiConfig = require('../config/api')

/**
 * 与 Web 端 http.ts 一致：登录/注册/短信等公开接口不携带 Bearer，避免过期 token 触发 403。
 */
function isPublicAuthPath(url) {
  const path = String(url || '').split('?')[0]
  return (
    path.startsWith('/auth/login') ||
    path.startsWith('/auth/register') ||
    path.startsWith('/auth/sms/') ||
    path.startsWith('/auth/password/reset') ||
    path.startsWith('/auth/refresh')
  )
}

function readSession() {
  return wx.getStorageSync('pi_session') || null
}

function writeSession(session) {
  if (!session) {
    wx.removeStorageSync('pi_session')
    clearSessionApiBase()
  } else {
    wx.setStorageSync('pi_session', session)
    markSessionApiBase()
  }
}

const REFRESH_COOKIE_NAME = 'pi_refresh'
const REFRESH_COOKIE_STORAGE_KEY = 'pi_refresh_cookie'
const API_BASE_STORAGE_KEY = 'pi_api_base_url'
let sessionRefreshCookie = ''

function currentApiBaseUrl() {
  return apiConfig.baseUrl
}

function markSessionApiBase() {
  wx.setStorageSync(API_BASE_STORAGE_KEY, currentApiBaseUrl())
}

function clearSessionApiBase() {
  wx.removeStorageSync(API_BASE_STORAGE_KEY)
}

/** baseUrl 变更后丢弃旧 token，避免 localhost/远程混用导致 401 刷屏 */
function invalidateSessionIfApiBaseChanged() {
  const session = readSession()
  if (!session) return false
  const storedBase = wx.getStorageSync(API_BASE_STORAGE_KEY)
  if (!storedBase || storedBase === currentApiBaseUrl()) return false
  writeSession(null)
  clearRefreshCookie()
  clearSessionApiBase()
  return true
}

function clearRefreshCookie() {
  sessionRefreshCookie = ''
  wx.removeStorageSync(REFRESH_COOKIE_STORAGE_KEY)
}

function storedRefreshCookie() {
  if (sessionRefreshCookie) return sessionRefreshCookie
  const stored = wx.getStorageSync(REFRESH_COOKIE_STORAGE_KEY)
  if (!stored || typeof stored !== 'object' || !stored.cookie) return ''
  if (stored.expiresAt && stored.expiresAt <= Date.now()) {
    wx.removeStorageSync(REFRESH_COOKIE_STORAGE_KEY)
    return ''
  }
  return stored.cookie
}

function responseCookieValues(res) {
  const values = Array.isArray(res?.cookies) ? [...res.cookies] : []
  Object.entries(res?.header || {}).forEach(([name, value]) => {
    if (name.toLowerCase() !== 'set-cookie') return
    if (Array.isArray(value)) values.push(...value)
    else if (value) values.push(value)
  })
  return values.map(String)
}

/**
 * wx.request 不会像浏览器一样维护 Cookie。手动提取后端返回的 pi_refresh：
 * - remember=true 带 Max-Age，持久化到 Storage；
 * - remember=false 不带 Max-Age，仅保存在当前小程序进程内；
 * - Max-Age=0 表示退出登录/凭证撤销。
 */
function captureRefreshCookie(res) {
  for (const raw of responseCookieValues(res)) {
    const match = raw.match(new RegExp(`(?:^|[,\\s])${REFRESH_COOKIE_NAME}=([^;,\\s]*)`, 'i'))
    if (!match) continue

    const value = match[1]
    const maxAgeMatch = raw.match(/;\s*Max-Age=(\d+)/i)
    const maxAge = maxAgeMatch ? Number(maxAgeMatch[1]) : null
    if (!value || maxAge === 0) {
      clearRefreshCookie()
      return
    }

    const cookie = `${REFRESH_COOKIE_NAME}=${value}`
    if (maxAge !== null && Number.isFinite(maxAge) && maxAge > 0) {
      sessionRefreshCookie = ''
      wx.setStorageSync(REFRESH_COOKIE_STORAGE_KEY, {
        cookie,
        expiresAt: Date.now() + maxAge * 1000,
      })
    } else {
      sessionRefreshCookie = cookie
      wx.removeStorageSync(REFRESH_COOKIE_STORAGE_KEY)
    }
    return
  }
}

function cookieHeader(url) {
  return String(url || '').startsWith('/auth/') ? storedRefreshCookie() : ''
}

function syncAppSession(session, options = {}) {
  try {
    const app = getApp()
    if (app && typeof app.applySession === 'function' && session) {
      app.applySession(session, { reloadPages: false, skipBadges: true, ...options })
    } else if (app && typeof app.handleSessionExpired === 'function' && !session) {
      app.handleSessionExpired()
    } else if (app && typeof app.clearUser === 'function' && !session) {
      app.clearUser({ redirect: true })
    }
  } catch {
    // App 尚未初始化时忽略
  }
}

let refreshPromise = null

function tryRefreshSession() {
  if (refreshPromise) return refreshPromise
  const { baseUrl, timeout } = apiConfig
  const refreshCookie = storedRefreshCookie()
  if (!refreshCookie) return Promise.resolve(false)
  refreshPromise = new Promise((resolve) => {
    wx.request({
      url: `${baseUrl}/auth/refresh`,
      method: 'POST',
      timeout,
      header: {
        'Content-Type': 'application/json',
        Cookie: refreshCookie,
      },
      success(res) {
        captureRefreshCookie(res)
        const body = res.data
        const ok = res.statusCode >= 200 && res.statusCode < 300
          && body && typeof body === 'object' && body.code === 0 && body.data?.token
        if (ok) {
          const previous = readSession()
          const next = {
            ...(previous || {}),
            ...body.data,
            expiresAt: body.data.expiresAt ?? previous?.expiresAt,
          }
          writeSession(next)
          // 与 Web 一致：续期后重试原请求；仅当用户/权限发生变化时由 applySession 重载页面。
          syncAppSession(next)
          resolve(true)
        } else {
          resolve(false)
        }
      },
      fail() {
        resolve(false)
      },
      complete() {
        refreshPromise = null
      },
    })
  })
  return refreshPromise
}

/** 启动或发请求前：access token 将过期且有 refresh cookie 时先静默续期，减少 401 日志 */
function ensureSessionFresh() {
  const session = readSession()
  if (!session?.token) return Promise.resolve(false)
  const stillValid = session.expiresAt && Date.now() < session.expiresAt - 30_000
  if (stillValid) return Promise.resolve(true)
  if (!storedRefreshCookie()) return Promise.resolve(false)
  return tryRefreshSession()
}

/**
 * 统一 HTTP 请求 — 与网页端共用后端 /api/v1
 * 响应格式: { code: 0, message: 'ok', data: T }
 */
function request({ url, method = 'GET', data, auth = true, headers = {}, retried = false }) {
  const { baseUrl, timeout } = apiConfig
  const session = readSession()
  const token = session && session.token
  const skipAuth = auth === false || isPublicAuthPath(url)
  const allowsAutomaticRefresh = !String(url || '').startsWith('/auth/logout')
  const refreshCookie = cookieHeader(url)

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl}${url}`,
      method,
      data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(!skipAuth && token ? { Authorization: `Bearer ${token}` } : {}),
        ...(refreshCookie ? { Cookie: refreshCookie } : {}),
        ...headers,
      },
      success(res) {
        captureRefreshCookie(res)
        const body = res.data
        const hasAppEnvelope = body && typeof body === 'object' && 'code' in body
        const isAuthFailure = res.statusCode === 401
          || (res.statusCode === 403 && !hasAppEnvelope && !isPublicAuthPath(url))

        if (isAuthFailure && !skipAuth && allowsAutomaticRefresh && !retried) {
          tryRefreshSession().then((refreshed) => {
            if (refreshed) {
              request({ url, method, data, auth, headers, retried: true }).then(resolve).catch(reject)
            } else {
              writeSession(null)
              clearRefreshCookie()
              syncAppSession(null)
              reject(new Error('登录已过期，请重新登录'))
            }
          })
          return
        }

        if (isAuthFailure && !skipAuth) {
          writeSession(null)
          clearRefreshCookie()
          syncAppSession(null)
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (res.statusCode === 403 && !hasAppEnvelope && isPublicAuthPath(url)) {
          reject(new Error('后端未提供短信接口，请更新代码并重启后端（需含 /auth/sms/send）'))
          return
        }
        if (hasAppEnvelope) {
          if (body.code === 0) {
            resolve(body.data)
          } else {
            const err = new Error(body.message || '请求失败')
            err.statusCode = res.statusCode
            err.apiCode = body.code
            reject(err)
          }
        } else if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body)
        } else {
          const err = new Error(`HTTP ${res.statusCode}`)
          err.statusCode = res.statusCode
          reject(err)
        }
      },
      fail(err) {
        const detail = err?.errMsg || '网络请求失败'
        reject(new Error(`${detail} → ${baseUrl}${url}`))
      },
    })
  })
}

function sanitizeQueryParams(params) {
  if (!params || typeof params !== 'object') return params
  const out = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    out[key] = value
  })
  return out
}

function get(url, params) {
  return request({ url, method: 'GET', data: sanitizeQueryParams(params) })
}

function post(url, data, headers) {
  return request({ url, method: 'POST', data, headers })
}

function put(url, data) {
  return request({ url, method: 'PUT', data })
}

function patch(url, data) {
  return request({ url, method: 'PATCH', data })
}

function del(url, data) {
  return request({ url, method: 'DELETE', data })
}

/**
 * 文件上传 — 与 request 共用 401 refresh；响应体为 { code, data } 时返回 data
 */
function uploadFile({ url, filePath, name = 'file', formData = {}, auth = true, retried = false }) {
  const { baseUrl, timeout } = apiConfig
  const session = readSession()
  const token = session?.token
  const skipAuth = auth === false

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${baseUrl}${url}`,
      filePath,
      name,
      formData,
      timeout,
      header: {
        ...(!skipAuth && token ? { Authorization: `Bearer ${token}` } : {}),
      },
      success(res) {
        let body = res.data
        try {
          body = typeof body === 'string' ? JSON.parse(body) : body
        } catch {
          body = res.data
        }
        const hasAppEnvelope = body && typeof body === 'object' && 'code' in body
        const isAuthFailure = res.statusCode === 401
          || (res.statusCode === 403 && !hasAppEnvelope)

        if (isAuthFailure && !skipAuth && !retried) {
          tryRefreshSession().then((refreshed) => {
            if (refreshed) {
              uploadFile({ url, filePath, name, formData, auth, retried: true }).then(resolve).catch(reject)
            } else {
              writeSession(null)
              clearRefreshCookie()
              syncAppSession(null)
              reject(new Error('登录已过期，请重新登录'))
            }
          })
          return
        }

        if (isAuthFailure && !skipAuth) {
          writeSession(null)
          clearRefreshCookie()
          syncAppSession(null)
          reject(new Error('登录已过期，请重新登录'))
          return
        }

        if (hasAppEnvelope) {
          if (body.code === 0) {
            resolve(body.data)
          } else {
            reject(new Error(body.message || '上传失败'))
          }
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body)
          return
        }
        reject(new Error(`HTTP ${res.statusCode}`))
      },
      fail(err) {
        const detail = err?.errMsg || '网络请求失败'
        reject(new Error(`${detail} → ${baseUrl}${url}`))
      },
    })
  })
}

module.exports = {
  request,
  get,
  post,
  put,
  patch,
  del,
  uploadFile,
  ensureSessionFresh,
  invalidateSessionIfApiBaseChanged,
  markSessionApiBase,
}
