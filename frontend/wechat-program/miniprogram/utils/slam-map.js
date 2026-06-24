const api = require('../services/index')
const { parseMapYaml } = require('./parse-map-yaml')
const { pgmPathToTempPng, writeBase64Png, ensureSlamMapDir } = require('./pgm-to-image')

const cache = {}

function fetchText(url) {
  if (/^https?:\/\//i.test(url)) {
    return new Promise((resolve, reject) => {
      wx.request({
        url,
        method: 'GET',
        success(res) {
          if (res.statusCode >= 200 && res.statusCode < 300 && res.data != null) {
            resolve(typeof res.data === 'string' ? res.data : String(res.data))
          } else {
            reject(new Error(`加载 yaml 失败 (${res.statusCode})`))
          }
        },
        fail(err) {
          reject(new Error(err.errMsg || '加载 yaml 失败'))
        },
      })
    })
  }
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath: url,
      encoding: 'utf8',
      success(res) {
        resolve(res.data)
      },
      fail(err) {
        reject(new Error(err.errMsg || '加载 yaml 失败'))
      },
    })
  })
}

function getImageSize(src) {
  return new Promise((resolve, reject) => {
    wx.getImageInfo({
      src,
      success(res) {
        resolve({ width: res.width, height: res.height })
      },
      fail(err) {
        reject(new Error(err.errMsg || '读取地图 PNG 尺寸失败'))
      },
    })
  })
}

async function resolveImagePath(imagePath, yamlMeta, tag) {
  if (/\.pgm$/i.test(imagePath)) {
    return pgmPathToTempPng(imagePath, yamlMeta, tag)
  }
  const size = await getImageSize(imagePath)
  return { filePath: imagePath, width: size.width, height: size.height }
}

function buildMeta(yamlMeta, yamlText, yamlUrl, image, source) {
  return {
    resolution: yamlMeta.resolution,
    origin: yamlMeta.origin,
    negate: yamlMeta.negate,
    occupied_thresh: yamlMeta.occupied_thresh,
    free_thresh: yamlMeta.free_thresh,
    frame_id: yamlMeta.frame_id,
    width: image.width,
    height: image.height,
    imagePath: image.filePath,
    imageName: image.name || yamlMeta.image || '',
    yamlUrl: yamlUrl || '',
    yamlText: yamlText || '',
    source: source || 'site',
  }
}

/** 按站点拉 yaml + png/pgm，解析 yaml、渲染栅格（与 route_map_tool / 机器人共用资源） */
async function loadSlamMapForSite(siteId) {
  const id = siteId || 'site_001'
  if (cache[id]) return cache[id]

  const assets = await api.getSiteSlamMap(id)
  let yamlText
  if (assets.yamlText) {
    yamlText = assets.yamlText
  } else {
    yamlText = await fetchText(assets.yamlUrl)
  }
  const yamlMeta = parseMapYaml(yamlText)

  let image
  if (assets.pngPath && assets.width && assets.height) {
    image = { filePath: assets.pngPath, width: assets.width, height: assets.height }
  } else if (assets.pngBase64) {
    ensureSlamMapDir()
    const pngPath = `${wx.env.USER_DATA_PATH}/slam_maps/${id}.png`
    await writeBase64Png(pngPath, assets.pngBase64)
    const size = await getImageSize(pngPath)
    image = { filePath: pngPath, width: size.width, height: size.height }
  } else if (assets.pngUrl) {
    image = await resolveImagePath(assets.pngUrl, yamlMeta, id)
  } else {
    throw new Error('站点地图缺少 PNG/PGM 资源')
  }

  const meta = buildMeta(yamlMeta, yamlText, assets.yamlUrl || '', image, assets.source || 'site')
  cache[id] = meta
  return meta
}

/** 从聊天记录等本地临时路径组装 SLAM 地图（yaml 文本 + png/pgm 路径） */
async function buildSlamMapFromLocal(yamlText, imagePath, imageName) {
  const yamlMeta = parseMapYaml(yamlText)
  const image = await resolveImagePath(imagePath, yamlMeta, 'import')
  if (imageName) image.name = imageName
  return buildMeta(yamlMeta, yamlText, '', image, 'import')
}

function clearSlamMapCache(siteId) {
  if (siteId) delete cache[siteId]
  else Object.keys(cache).forEach((key) => delete cache[key])
}

module.exports = { loadSlamMapForSite, buildSlamMapFromLocal, clearSlamMapCache, parseMapYaml }
