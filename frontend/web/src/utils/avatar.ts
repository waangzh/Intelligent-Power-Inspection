const AVATAR_COLORS = ['#1a5fb4', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#626aef']

export function getAvatarColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

export function getInitials(displayName: string): string {
  const trimmed = displayName.trim()
  if (!trimmed) return '?'
  if (/[\u4e00-\u9fff]/.test(trimmed)) return trimmed.slice(0, 1)
  const parts = trimmed.split(/\s+/)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return trimmed.slice(0, 2).toUpperCase()
}

export function generateDefaultAvatar(displayName: string, seed: string): string {
  const initials = getInitials(displayName)
  const color = getAvatarColor(seed)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
    <rect width="128" height="128" rx="64" fill="${color}"/>
    <text x="64" y="64" dy="0.35em" text-anchor="middle" fill="#fff" font-size="48" font-family="system-ui,sans-serif" font-weight="600">${initials}</text>
  </svg>`
  return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svg)))}`
}

export const AVATAR_MAX_SIZE = 2 * 1024 * 1024
export const AVATAR_ACCEPT = 'image/jpeg,image/png,image/webp'

export function validateAvatarFile(file: File): string | null {
  if (!AVATAR_ACCEPT.split(',').includes(file.type)) {
    return '仅支持 JPG、PNG、WebP 格式'
  }
  if (file.size > AVATAR_MAX_SIZE) {
    return '图片大小不能超过 2MB'
  }
  return null
}

export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}
