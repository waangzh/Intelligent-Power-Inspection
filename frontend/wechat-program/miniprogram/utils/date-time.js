const TZ = 'Asia/Shanghai'

function parseInstant(iso) {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

/** ISO 时间转北京时间展示，默认含秒 */
function formatDateTime(iso, { withSeconds = true, empty = '-' } = {}) {
  const date = parseInstant(iso)
  if (!date) {
    if (!iso) return empty
    return String(iso).slice(0, 19).replace('T', ' ')
  }
  const opts = {
    hour12: false,
    timeZone: TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }
  if (withSeconds) opts.second = '2-digit'
  return date.toLocaleString('zh-CN', opts)
}

function formatTimeShort(iso, { empty = '-' } = {}) {
  const date = parseInstant(iso)
  if (!date) return empty
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    timeZone: TZ,
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** 列表/卡片常用：北京时间，不含秒 */
function formatDateTimeShort(iso, { empty = '' } = {}) {
  return formatDateTime(iso, { withSeconds: false, empty })
}

module.exports = {
  formatDateTime,
  formatDateTimeShort,
  formatTimeShort,
}
