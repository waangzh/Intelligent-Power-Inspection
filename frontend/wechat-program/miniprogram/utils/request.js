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
  if (!session) wx.removeStorageSync('pi_session')
  else wx.setStorageSync('pi_session', session)
}

function syncAppSession(session, options = {}) {
  try {
    const app = getApp()
    if (app && typeof app.applySession === 'function' && session) {
      app.applySession(session, options)
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
  refreshPromise = new Promise((resolve) => {
    wx.request({
      url: `${baseUrl}/auth/refresh`,
      method: 'POST',
      timeout,
      enableCookie: true,
      header: { 'Content-Type': 'application/json' },
      success(res) {
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
          syncAppSession(next, { reloadPages: true })
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

/**
 * 统一 HTTP 请求 — 与网页端共用后端 /api/v1
 * 响应格式: { code: 0, message: 'ok', data: T }
 *
 * enableCookie: 后端 refresh token 写在 HttpOnly Cookie（pi_refresh）里，
 * 小程序默认不携带 Cookie，必须显式开启才能与 Web 一样自动续期。
 */
function request({ url, method = 'GET', data, auth = true, headers = {}, retried = false }) {
  const { baseUrl, timeout } = apiConfig
  const session = readSession()
  const token = session && session.token
  const skipAuth = auth === false || isPublicAuthPath(url)
  const allowsAutomaticRefresh = !String(url || '').startsWith('/auth/logout')

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl}${url}`,
      method,
      data,
      timeout,
      enableCookie: true,
      header: {
        'Content-Type': 'application/json',
        ...(!skipAuth && token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      success(res) {
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
              syncAppSession(null)
              reject(new Error('登录已过期，请重新登录'))
            }
          })
          return
        }

        if (isAuthFailure) {
          writeSession(null)
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
            reject(new Error(body.message || '请求失败'))
          }
        } else if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body)
        } else {
          reject(new Error(`HTTP ${res.statusCode}`))
        }
      },
      fail() {
        reject(new Error('网络异常，请检查后端服务是否启动'))
      },
    })
  })
}

function get(url, params) {
  return request({ url, method: 'GET', data: params })
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

module.exports = { request, get, post, put, patch, del }
