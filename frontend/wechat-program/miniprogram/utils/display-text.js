/** 去掉 LocateAnything 附注及中英文括号内容 */
function stripParenthetical(text) {
  return String(text || '')
    .replace(/\s*[（(]?\s*LocateAnything\s*[:：][^）)]*[\)）]?/gi, '')
    .replace(/\s*[（(]\s*Loc(?:ateAnything)?[^）)]*[\)）]?/gi, '')
    .replace(/\s*\([^)]*\)/g, '')
    .replace(/\s*（[^）]*）/g, '')
    .replace(/\s*\([^)]*$/, '')
    .replace(/\s*（[^）]*$/, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

/** 业务文案展示：去「告警处置：」前缀，并去掉括号附注 */
function formatBusinessMessage(text) {
  let t = String(text || '').trim()
  if (!t) return ''
  t = t.replace(/^告警处置[：:]\s*/, '')
  return stripParenthetical(t)
}

function formatWorkOrderTitle(title) {
  const t = formatBusinessMessage(title)
  return t || '工单'
}

module.exports = {
  stripParenthetical,
  formatBusinessMessage,
  formatWorkOrderTitle,
}
