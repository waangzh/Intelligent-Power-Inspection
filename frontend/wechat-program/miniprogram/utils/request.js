const apiConfig = require('../config/api')

/**
 * 统一 HTTP 请求 — 与网页端共用后端 /api/v1
 * 响应格式: { code: 0, message: 'ok', data: T }
 */
function request({ url, method = 'GET', data, auth = true, headers = {} }) {
  const { baseUrl, timeout } = apiConfig
  const session = wx.getStorageSync('pi_session')
  const token = session && session.token

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl}${url}`,
      method,
      data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(auth && token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      success(res) {
        const body = res.data
        // 业务层的权限拒绝（如观察员访问工单）会经 GlobalExceptionHandler 包装成
        // { code, message, data } 返回；而 token 失效/缺失时 Spring Security 直接
        // 在认证层拦截，返回的是框架默认错误体（无 code 字段）。用是否带 code 区分
        // 两种 403，避免把“认证失效”误判为普通业务失败，导致坏 token 被反复携带发出。
        const hasAppEnvelope = body && typeof body === 'object' && 'code' in body
        if (res.statusCode === 401 || (res.statusCode === 403 && !hasAppEnvelope)) {
          wx.removeStorageSync('pi_session')
          reject(new Error('登录已过期，请重新登录'))
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
