function loadFromStorage(key, fallback) {
  try {
    const raw = wx.getStorageSync(key)
    if (raw === '' || raw === undefined || raw === null) return fallback
    return raw
  } catch {
    return fallback
  }
}

function saveToStorage(key, value) {
  wx.setStorageSync(key, value)
}

function removeStorage(key) {
  wx.removeStorageSync(key)
}

function uid(prefix = 'id') {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

const AVATAR_COLORS = ['#1768f2', '#12b76a', '#ff8a00', '#f04438', '#6f8099', '#626aef']

function getAvatarColor(s) {
  let hash = 0
  for (let i = 0; i < s.length; i++) hash = s.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

function getInitials(name) {
  const t = (name || '').trim()
  if (!t) return '?'
  if (/[\u4e00-\u9fff]/.test(t)) return t.slice(0, 1)
  return t.slice(0, 2).toUpperCase()
}

function generateDefaultAvatar(displayName, seedStr) {
  return { color: getAvatarColor(seedStr), initials: getInitials(displayName) }
}

function validateUsername(username) {
  if (!username || username.length < 4 || username.length > 20) return '用户名长度为 4～20 位'
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return '用户名只能包含字母、数字和下划线'
  return null
}

function validatePassword(password) {
  if (!password || password.length < 8) return '密码至少 8 位'
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) return '密码需包含字母和数字'
  return null
}

module.exports = {
  loadFromStorage,
  saveToStorage,
  removeStorage,
  uid,
  validateUsername,
  validatePassword,
  generateDefaultAvatar,
}
