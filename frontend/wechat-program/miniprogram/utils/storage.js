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

module.exports = { loadFromStorage, saveToStorage, removeStorage, uid }
