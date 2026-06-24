/** 解析 ROS map_server 常用的 P5 PGM（ASCII / binary） */

function readLine(bytes, start) {
  let i = start
  while (i < bytes.length && bytes[i] !== 0x0a) i += 1
  const line = String.fromCharCode.apply(null, bytes.subarray(start, i))
  return { line: line.trim(), next: i < bytes.length ? i + 1 : i }
}

function skipCommentsAndBlanks(bytes, pos) {
  let p = pos
  while (p < bytes.length) {
    const { line, next } = readLine(bytes, p)
    p = next
    if (!line || line.startsWith('#')) continue
    return { line, next: p }
  }
  throw new Error('PGM 文件格式无效')
}

function parseHeader(bytes) {
  if (bytes.length < 4 || String.fromCharCode(bytes[0], bytes[1]) !== 'P5') {
    throw new Error('仅支持 binary PGM (P5)')
  }
  let pos = 2
  const first = skipCommentsAndBlanks(bytes, pos)
  pos = first.next
  const sizeParts = first.line.split(/\s+/).filter(Boolean)
  if (sizeParts.length < 2) throw new Error('PGM 缺少宽高')
  const width = Number(sizeParts[0])
  const height = Number(sizeParts[1])
  if (!width || !height) throw new Error('PGM 宽高无效')

  const maxLine = skipCommentsAndBlanks(bytes, pos)
  pos = maxLine.next
  const maxval = Number(maxLine.line.split(/\s+/)[0])
  if (!maxval || maxval > 65535) throw new Error('PGM maxval 无效')

  while (pos < bytes.length && (bytes[pos] === 0x0a || bytes[pos] === 0x0d || bytes[pos] === 0x20)) pos += 1
  const expected = width * height * (maxval > 255 ? 2 : 1)
  if (bytes.length - pos < expected) throw new Error('PGM 像素数据不完整')

  const raw = bytes.subarray(pos, pos + expected)
  const data = new Uint8Array(width * height)
  if (maxval <= 255) {
    data.set(raw.subarray(0, width * height))
  } else {
    for (let i = 0; i < width * height; i += 1) {
      data[i] = Math.round((raw[i * 2] * 256 + raw[i * 2 + 1]) / maxval * 255)
    }
  }
  return { width, height, maxval: maxval > 255 ? 255 : maxval, data }
}

function parsePgmBuffer(buffer) {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  return parseHeader(bytes)
}

function readBinaryFile(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath,
      success(res) {
        resolve(res.data)
      },
      fail(err) {
        reject(new Error(err.errMsg || '读取文件失败'))
      },
    })
  })
}

module.exports = { parsePgmBuffer, readBinaryFile }
