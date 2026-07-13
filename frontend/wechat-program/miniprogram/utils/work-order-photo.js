const { uid, saveToStorage, loadFromStorage } = require('./storage')

const PHOTO_KEY_PREFIX = 'wo_photo_'

function persistPhoto(filePath) {
  return new Promise((resolve, reject) => {
    wx.compressImage({
      src: filePath,
      quality: 70,
      success: (res) => {
        const fs = wx.getFileSystemManager()
        fs.readFile({
          filePath: res.tempFilePath,
          encoding: 'base64',
          success: (readRes) => {
            const id = uid('photo')
            saveToStorage(`${PHOTO_KEY_PREFIX}${id}`, readRes.data)
            resolve(`mock://photo/${id}`)
          },
          fail: reject,
        })
      },
      fail: () => {
        const fs = wx.getFileSystemManager()
        fs.readFile({
          filePath,
          encoding: 'base64',
          success: (readRes) => {
            const id = uid('photo')
            saveToStorage(`${PHOTO_KEY_PREFIX}${id}`, readRes.data)
            resolve(`mock://photo/${id}`)
          },
          fail: reject,
        })
      },
    })
  })
}

function resolvePhotoSrc(url) {
  if (!url) return ''
  if (url.startsWith('mock://photo/')) {
    const id = url.replace('mock://photo/', '')
    const base64 = loadFromStorage(`${PHOTO_KEY_PREFIX}${id}`, '')
    if (!base64) return ''
    return `data:image/jpeg;base64,${base64}`
  }
  return url
}

module.exports = { persistPhoto, resolvePhotoSrc }
