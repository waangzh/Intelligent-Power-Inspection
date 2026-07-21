const apiConfig = require('../config/api')

function fileOrigin() {
  return String(apiConfig.baseUrl || '').replace(/\/api\/v1\/?$/, '')
}

function resolvePhotoSrc(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('/model-files/')) return `${fileOrigin()}${url}`
  return url
}

module.exports = { resolvePhotoSrc }
