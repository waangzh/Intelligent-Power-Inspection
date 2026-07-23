const { loadFromStorage, saveToStorage } = require('./storage')

const STORAGE_KEY = 'pi_ui_preferences'

const DEFAULTS = {
  showGpsTrack: true,
  notificationsUnreadOnly: false,
}

function getUiPreferences() {
  const stored = loadFromStorage(STORAGE_KEY, null)
  if (!stored || typeof stored !== 'object') return { ...DEFAULTS }
  return { ...DEFAULTS, ...stored }
}

function saveUiPreferences(patch) {
  const next = { ...getUiPreferences(), ...patch }
  saveToStorage(STORAGE_KEY, next)
  return next
}

module.exports = {
  DEFAULTS,
  getUiPreferences,
  saveUiPreferences,
}
