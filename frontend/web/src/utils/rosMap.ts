import type { RouteMapSnapshot, RosMapState } from '@/types/routeExecutor'

export function round3(value: number): number {
  return Math.round(Number(value) * 1000) / 1000
}

/** 将 YAML 中可能带目录的 image 值归一化为 PGM 文件名。 */
export function rosMapImageFileName(image: string): string {
  return image.trim().replace(/\\/g, '/').split('/').pop() || ''
}

function parseScalar(valueStr: string): unknown {
  const value = valueStr.trim()
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1)
  }
  if (value.startsWith('[') && value.endsWith(']')) {
    return value
      .slice(1, -1)
      .split(',')
      .map((part) => Number(part.trim()))
  }
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value)
  return value
}

/** 解析 ROS map_server 的 YAML（支持单行与多行列表 origin） */
export function parseYaml(text: string): Partial<RosMapState> {
  const config: Record<string, unknown> = {}
  const lines = text.split(/\r?\n/)
  let i = 0

  while (i < lines.length) {
    const rawLine = lines[i]
    const line = rawLine.split('#')[0].trim()
    i += 1
    if (!line) continue

    const match = line.match(/^([A-Za-z_][\w-]*)\s*:\s*(.*)$/)
    if (!match) continue

    const key = match[1]
    const valueStr = match[2].trim()

    if (valueStr === '' || valueStr === '|' || valueStr === '>') {
      const listItems: number[] = []
      while (i < lines.length) {
        const nextLine = lines[i].split('#')[0]
        if (!nextLine.trim()) {
          i += 1
          continue
        }
        const itemMatch = nextLine.match(/^\s*-\s*(-?\d+(?:\.\d+)?)\s*$/)
        if (!itemMatch) break
        listItems.push(Number(itemMatch[1]))
        i += 1
      }
      if (listItems.length) config[key] = listItems
      continue
    }

    config[key] = parseScalar(valueStr)
  }

  const patch: Partial<RosMapState> = {}
  if (typeof config.resolution === 'number') patch.resolution = config.resolution
  if (Array.isArray(config.origin) && config.origin.length >= 3 && config.origin.slice(0, 3).every(Number.isFinite)) {
    const o = config.origin as number[]
    patch.origin = [Number(o[0]), Number(o[1]), Number(o[2])]
  }
  if (typeof config.negate === 'number') patch.negate = config.negate
  if (typeof config.occupied_thresh === 'number') patch.occupiedThresh = config.occupied_thresh
  if (typeof config.free_thresh === 'number') patch.freeThresh = config.free_thresh
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
  return p.x >= 0 && p.x < map.width && p.y >= 0 && p.y < map.height
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
    occupiedThresh: 0.65,
    freeThresh: 0.25,
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

export function encodeMapSnapshot(map: RosMapState): RouteMapSnapshot | undefined {
  if (!map.pixels || !map.width || !map.height) return undefined
  const chunkSize = 0x8000
  const parts: string[] = []
  for (let i = 0; i < map.pixels.length; i += chunkSize) {
    const slice = map.pixels.subarray(i, i + chunkSize)
    parts.push(String.fromCharCode(...slice))
  }
  return {
    width: map.width,
    height: map.height,
    resolution: map.resolution,
    origin: [...map.origin] as [number, number, number],
    negate: map.negate,
    pgm_base64: btoa(parts.join('')),
  }
}

export function decodeMapSnapshot(snapshot: RouteMapSnapshot): Partial<RosMapState> {
  const binary = atob(snapshot.pgm_base64)
  const pixels = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) pixels[i] = binary.charCodeAt(i)
  return {
    width: snapshot.width,
    height: snapshot.height,
    pixels,
    resolution: snapshot.resolution,
    origin: snapshot.origin,
    negate: snapshot.negate,
    image: 'saved_map.pgm',
    yamlName: '',
    pgmName: 'saved_map.pgm',
  }
}

export function rosCoordFromLatLng(pos: { lat: number; lng: number; x?: number; y?: number; yaw?: number }) {
  const x = Number.isFinite(pos.x) ? pos.x! : pos.lng
  const y = Number.isFinite(pos.y) ? pos.y! : pos.lat
  return { x, y, yaw: pos.yaw ?? 0 }
}
