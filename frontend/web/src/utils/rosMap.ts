import type { RosMapState } from '@/types/routeExecutor'

export function round3(value: number): number {
  return Math.round(Number(value) * 1000) / 1000
}

export function parseYaml(text: string): Partial<RosMapState> {
  const config: Record<string, unknown> = {}
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.split('#')[0].trim()
    if (!line) continue
    const match = line.match(/^([A-Za-z_][\w-]*)\s*:\s*(.*)$/)
    if (!match) continue
    const key = match[1]
    let value: unknown = match[2].trim()
    if (
      (typeof value === 'string' && value.startsWith('"') && value.endsWith('"')) ||
      (typeof value === 'string' && value.startsWith("'") && value.endsWith("'"))
    ) {
      value = (value as string).slice(1, -1)
    } else if (typeof value === 'string' && value.startsWith('[') && value.endsWith(']')) {
      value = value
        .slice(1, -1)
        .split(',')
        .map((part) => Number(part.trim()))
    } else if (typeof value === 'string' && /^-?\d+(\.\d+)?$/.test(value)) {
      value = Number(value)
    }
    config[key] = value
  }

  const patch: Partial<RosMapState> = {}
  if (typeof config.resolution === 'number') patch.resolution = config.resolution
  if (Array.isArray(config.origin) && config.origin.length >= 3) {
    patch.origin = [Number(config.origin[0]), Number(config.origin[1]), Number(config.origin[2])]
  }
  if (typeof config.negate === 'number') patch.negate = config.negate
  if (typeof config.image === 'string') patch.image = config.image
  return patch
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
  while (indexRef.index < bytes.length && bytes[indexRef.index] <= 32) indexRef.index += 1
  if (magic !== 'P5' || !width || !height || maxVal <= 0 || maxVal > 255) {
    throw new Error('只支持 8-bit P5 PGM 地图。')
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
