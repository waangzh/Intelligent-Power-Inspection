function round3(value) {
  return Math.round(Number(value) * 1000) / 1000
}

function mapToPixel(x, y, meta) {
  const ox = meta.origin[0]
  const oy = meta.origin[1]
  const res = meta.resolution
  return {
    x: (x - ox) / res,
    y: meta.height - (y - oy) / res,
  }
}

function pixelToMap(px, py, meta) {
  const ox = meta.origin[0]
  const oy = meta.origin[1]
  const res = meta.resolution
  return {
    x: round3(ox + px * res),
    y: round3(oy + (meta.height - py) * res),
  }
}

function isInside(x, y, meta) {
  if (!meta || !meta.width || !meta.height) return true
  const p = mapToPixel(x, y, meta)
  return p.x >= 0 && p.x <= meta.width && p.y >= 0 && p.y <= meta.height
}

module.exports = {
  round3,
  mapToPixel,
  pixelToMap,
  isInside,
}
