const { parsePgmBuffer, readBinaryFile } = require('./parse-pgm')

/** 按 map_server 规则将 PGM 灰度映射为 RGBA（free=白, occupied=黑, unknown=灰） */
function pgmToRgba(pgm, yamlMeta) {
  const { width, height, data, maxval } = pgm
  const negate = yamlMeta && yamlMeta.negate ? 1 : 0
  const occ = yamlMeta && typeof yamlMeta.occupied_thresh === 'number' ? yamlMeta.occupied_thresh : 0.65
  const free = yamlMeta && typeof yamlMeta.free_thresh === 'number' ? yamlMeta.free_thresh : 0.196
  const rgba = new Uint8ClampedArray(width * height * 4)
  const scale = maxval > 0 ? 1 / maxval : 1 / 255

  for (let i = 0; i < width * height; i += 1) {
    let v = data[i] * scale
    if (negate) v = 1 - v
    let r
    if (v < free) r = 254
    else if (v > occ) r = 0
    else r = 205
    const off = i * 4
    rgba[off] = r
    rgba[off + 1] = r
    rgba[off + 2] = r
    rgba[off + 3] = 255
  }
  return rgba
}

function pgmToPngBase64(pgm, yamlMeta) {
  if (typeof wx === 'undefined' || !wx.createOffscreenCanvas) {
    throw new Error('当前环境不支持 PGM 直读，请升级基础库或使用 PNG')
  }
  const rgba = pgmToRgba(pgm, yamlMeta)
  const canvas = wx.createOffscreenCanvas({ type: '2d', width: pgm.width, height: pgm.height })
  const ctx = canvas.getContext('2d')
  const imgData = ctx.createImageData(pgm.width, pgm.height)
  imgData.data.set(rgba)
  ctx.putImageData(imgData, 0, 0)
  return canvas.toDataURL('image/png').split(',')[1]
}

function writeBase64Png(filePath, base64) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().writeFile({
      filePath,
      data: base64,
      encoding: 'base64',
      success: () => resolve(filePath),
      fail: (err) => reject(new Error(err.errMsg || '写入 PNG 失败')),
    })
  })
}

function ensureSlamMapDir() {
  const dir = `${wx.env.USER_DATA_PATH}/slam_maps`
  const fs = wx.getFileSystemManager()
  try {
    fs.accessSync(dir)
  } catch {
    fs.mkdirSync(dir, true)
  }
  return dir
}

async function pgmPathToTempPng(pgmPath, yamlMeta, tag) {
  const buffer = await readBinaryFile(pgmPath)
  const pgm = parsePgmBuffer(buffer)
  const base64 = pgmToPngBase64(pgm, yamlMeta)
  const dir = ensureSlamMapDir()
  const filePath = `${dir}/${tag || 'import'}_${Date.now()}.png`
  await writeBase64Png(filePath, base64)
  return { filePath, width: pgm.width, height: pgm.height }
}

async function pgmBufferToTempPng(buffer, yamlMeta, tag) {
  const pgm = parsePgmBuffer(buffer)
  const base64 = pgmToPngBase64(pgm, yamlMeta)
  const dir = ensureSlamMapDir()
  const filePath = `${dir}/${tag || 'import'}_${Date.now()}.png`
  await writeBase64Png(filePath, base64)
  return { filePath, width: pgm.width, height: pgm.height }
}

function readFileBase64(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath,
      encoding: 'base64',
      success: (res) => resolve(res.data),
      fail: (err) => reject(new Error(err.errMsg || '读取文件失败')),
    })
  })
}

module.exports = {
  pgmToRgba,
  pgmToPngBase64,
  pgmPathToTempPng,
  pgmBufferToTempPng,
  writeBase64Png,
  ensureSlamMapDir,
  readFileBase64,
}
