import type { RosMapState } from '@/types/routeExecutor'
import { parseDocument } from 'yaml'

export function round3(value: number): number {
  return Math.round(Number(value) * 1000) / 1000
}

export function parseYaml(text: string): Partial<RosMapState> {
  if (text.length > 1024 * 1024) {
    throw new Error('YAML 文件过大，最大支持 1 MB。')
  }

  const document = parseDocument(text, { prettyErrors: true, strict: true, uniqueKeys: true })
  if (document.errors.length) {
    throw new Error(`YAML 解析失败：${document.errors[0].message}`)
  }

  let value: unknown
  try {
    value = document.toJS({ maxAliasCount: 0 })
  } catch (error) {
    throw new Error(`YAML 解析失败：${error instanceof Error ? error.message : String(error)}`)
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('YAML 根节点必须是地图配置对象。')
  }

  const config = value as Record<string, unknown>
  const image = typeof config.image === 'string' ? config.image.trim() : ''
  const resolution = Number(config.resolution)
  const origin = config.origin
  if (!image) throw new Error('YAML 缺少有效的 image 字段。')
  if (!/\.pgm$/i.test(image)) throw new Error('当前地图编辑器只支持 YAML 引用 PGM 图像。')
  if (!Number.isFinite(resolution) || resolution <= 0) {
    throw new Error('YAML 的 resolution 必须是大于 0 的数字。')
  }
  if (!Array.isArray(origin) || origin.length !== 3 || origin.some((item) => !Number.isFinite(Number(item)))) {
    throw new Error('YAML 的 origin 必须包含 3 个有效数字。')
  }

  const negateValue = config.negate ?? 0
  const negate = typeof negateValue === 'boolean' ? Number(negateValue) : Number(negateValue)
  if (negate !== 0 && negate !== 1) throw new Error('YAML 的 negate 只能是 0、1、false 或 true。')

  for (const key of ['free_thresh', 'occupied_thresh'] as const) {
    if (config[key] === undefined) continue
    const threshold = Number(config[key])
    if (!Number.isFinite(threshold) || threshold < 0 || threshold > 1) {
      throw new Error(`YAML 的 ${key} 必须是 0 到 1 之间的数字。`)
    }
  }
  if (config.free_thresh !== undefined && config.occupied_thresh !== undefined) {
    if (Number(config.free_thresh) >= Number(config.occupied_thresh)) {
      throw new Error('YAML 的 free_thresh 必须小于 occupied_thresh。')
    }
  }
  if (config.mode !== undefined && !['trinary', 'scale', 'raw'].includes(String(config.mode))) {
    throw new Error('YAML 的 mode 只能是 trinary、scale 或 raw。')
  }

  return {
    image,
    resolution,
    origin: [Number(origin[0]), Number(origin[1]), Number(origin[2])],
    negate,
  }
}

export function rosMapImageFileName(image: string): string {
  return image.replace(/\\/g, '/').split('/').filter(Boolean).pop() ?? ''
}

function readAsciiToken(bytes: Uint8Array, indexRef: { index: number }): string {
  while (indexRef.index < bytes.length) {
    const ch = bytes[indexRef.index]
    if (ch === 35) {
      while (indexRef.index < bytes.length && bytes[indexRef.index] !== 10) indexRef.index += 1
    } else if (ch <= 32) {
      indexRef.index += 1
    } else {
      break
    }
  }
  let token = ''
  while (indexRef.index < bytes.length && bytes[indexRef.index] > 32) {
    token += String.fromCharCode(bytes[indexRef.index])
    indexRef.index += 1
  }
  return token
}

export function parsePgm(buffer: ArrayBuffer): Pick<RosMapState, 'width' | 'height' | 'pixels'> {
  const bytes = new Uint8Array(buffer)
  const indexRef = { index: 0 }
  const magic = readAsciiToken(bytes, indexRef)
  const width = Number(readAsciiToken(bytes, indexRef))
  const height = Number(readAsciiToken(bytes, indexRef))
  const maxVal = Number(readAsciiToken(bytes, indexRef))
  if (magic !== 'P5' || !width || !height || maxVal <= 0 || maxVal > 255) {
    throw new Error('只支持 8-bit P5 PGM 地图。')
  }
  if (bytes[indexRef.index] === 13 && bytes[indexRef.index + 1] === 10) {
    indexRef.index += 2
  } else if (bytes[indexRef.index] <= 32) {
    indexRef.index += 1
  } else {
    throw new Error('PGM 文件头与像素数据之间缺少分隔符。')
  }
  const expected = width * height
  const pixels = bytes.slice(indexRef.index, indexRef.index + expected)
  if (pixels.length !== expected) {
    throw new Error('PGM 像素数据长度不完整。')
  }
  return { width, height, pixels }
}

export function mapToPixel(map: RosMapState, x: number, y: number) {
  const [ox, oy] = map.origin
  const res = map.resolution
  return {
    x: (x - ox) / res,
    y: map.height - (y - oy) / res,
  }
}

export function pixelToMap(map: RosMapState, px: number, py: number) {
  const [ox, oy] = map.origin
  const res = map.resolution
  return {
    x: round3(ox + px * res),
    y: round3(oy + (map.height - py) * res),
  }
}

export function isMapCoordinateInside(map: RosMapState, x: number, y: number): boolean {
  if (!map.width || !map.height) return true
  const p = mapToPixel(map, x, y)
  return p.x >= 0 && p.x <= map.width && p.y >= 0 && p.y <= map.height
}

export function createDefaultMapState(): RosMapState {
  return {
    width: 0,
    height: 0,
    pixels: null,
    yamlName: '',
    pgmName: '',
    image: 'my_map.pgm',
    resolution: 0.05,
    origin: [-2.89, -6.37, 0],
    negate: 0,
  }
}

export function rebuildMapBitmap(map: RosMapState, canvas: HTMLCanvasElement): ImageData | null {
  if (!map.pixels || !map.width || !map.height) return null
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  const expected = map.width * map.height
  canvas.width = map.width
  canvas.height = map.height
  const img = ctx.createImageData(map.width, map.height)
  for (let i = 0; i < expected; i += 1) {
    const gray = map.negate ? 255 - map.pixels[i] : map.pixels[i]
    const offset = i * 4
    img.data[offset] = gray
    img.data[offset + 1] = gray
    img.data[offset + 2] = gray
    img.data[offset + 3] = 255
  }
  ctx.putImageData(img, 0, 0)
  return img
}
