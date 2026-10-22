/**
 * 图片压缩工具：手机上拍照原图通常 3-10MB，直接用 4G 上传很慢。
 *
 * 策略（按优先级）：
 *  1) 按「最长边」缩放到 maxEdge（默认 1600px）——证件照/材料照辨清文字足够；
 *  2) 无论尺寸是否超标，只要「原文件体积」超过 maxBytes（默认 400KB），
 *     一律重编码为 JPEG（quality 由大变小自适应），避免 PNG 大图原样上传；
 *  3) 若重编码后反而更大（小图/已高度压缩），则回退原文件，绝不劣化；
 *  4) 任何异常（解码失败、canvas 不可用）都安全回退原文件，不阻塞上传。
 *
 * 目标：把典型 3-8MB 手机照压到 100-400KB，上传耗时下降 10-30 倍。
 *
 * @param {File} file 原始图片文件
 * @param {object} [opts]
 * @param {number} [opts.maxEdge=1600]   最长边上限（宽或高）
 * @param {number} [opts.maxWidth]       兼容旧参数：最大宽度（优先于 maxEdge）
 * @param {number} [opts.maxHeight]      兼容旧参数：最大高度（优先于 maxEdge）
 * @param {number} [opts.quality=0.8]    初始 JPEG 质量
 * @param {number} [opts.minQuality=0.5] 自适应降质下限
 * @param {number} [opts.maxBytes=409600] 体积阈值（400KB）：超过则强制重编码
 * @returns {Promise<File>} 压缩后的 File（失败时为原文件）
 */
export function compressImage(file, opts = {}) {
  const maxEdge = opts.maxEdge || 1600
  const quality = opts.quality || 0.8
  const minQuality = opts.minQuality || 0.5
  const maxBytes = opts.maxBytes || 400 * 1024

  // 兼容旧的 maxWidth / maxHeight 写法
  const maxWidth = opts.maxWidth || maxEdge
  const maxHeight = opts.maxHeight || maxEdge

  return new Promise((resolve) => {
    // 非图片、或不是浏览器可解码的类型：原样返回
    if (!file || !file.type || !file.type.startsWith('image/')) {
      resolve(file)
      return
    }
    // GIF 有动画，重编码会丢帧，跳过
    if (file.type === 'image/gif') {
      resolve(file)
      return
    }
    // 已经很小（< maxBytes/2）且是 JPEG：无需压缩，省一次解码开销
    if (file.type === 'image/jpeg' && file.size <= maxBytes / 2) {
      resolve(file)
      return
    }

    let url
    const img = new Image()
    // 允许跨域图片走 canvas（本地上传场景同源，通常无影响）
    img.crossOrigin = 'anonymous'

    /** 统一收尾：释放 objectURL 后返回结果 */
    const done = (out) => {
      if (url) {
        try {
          URL.revokeObjectURL(url)
        } catch {
          /* 忽略 */
        }
      }
      resolve(out || file)
    }

    img.onload = () => {
      try {
        const ow = img.naturalWidth || img.width
        const oh = img.naturalHeight || img.height
        if (!ow || !oh) {
          done(file)
          return
        }

        // 1) 计算缩放比例
        const ratio = Math.min(maxWidth / ow, maxHeight / oh, 1)
        const tw = Math.max(1, Math.round(ow * ratio))
        const th = Math.max(1, Math.round(oh * ratio))

        // 2) 是否需要重编码：尺寸超限 或 体积超阈值 或 非 JPEG（PNG/WEBP 往往很大）
        const needResize = ratio < 1
        const needReencode = file.size > maxBytes || file.type !== 'image/jpeg'
        if (!needResize && !needReencode) {
          done(file)
          return
        }

        const canvas = document.createElement('canvas')
        canvas.width = tw
        canvas.height = th
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          done(file)
          return
        }
        // 白色垫底：PNG 透明区域转 JPEG 不会变黑
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, tw, th)
        ctx.drawImage(img, 0, 0, tw, th)

        // 3) 自适应质量：目标体积 maxBytes，最多往下降 3 档
        const tryQualities = [quality]
        const step = (quality - minQuality) / 3
        for (let i = 1; i <= 3 && step > 0; i++) {
          tryQualities.push(Number((quality - step * i).toFixed(2)))
        }

        const emit = (qIdx) => {
          if (qIdx >= tryQualities.length) {
            done(file)
            return
          }
          const q = tryQualities[qIdx]
          canvas.toBlob(
            (blob) => {
              if (!blob) {
                done(file)
                return
              }
              // 压完反而更大 → 无论原文件多大都保原图，绝不劣化上传体验
              if (blob.size >= file.size) {
                done(file)
                return
              }
              // 体积已达目标，或已到最低质量档：采用
              if (blob.size <= maxBytes || qIdx === tryQualities.length - 1) {
                done(
                  new File([blob], renameToJpg(file.name), {
                    type: 'image/jpeg',
                    lastModified: Date.now(),
                  })
                )
                return
              }
              emit(qIdx + 1)
            },
            'image/jpeg',
            q
          )
        }
        emit(0)
      } catch {
        done(file)
      }
    }

    img.onerror = () => done(file)

    try {
      url = URL.createObjectURL(file)
      img.src = url
    } catch {
      done(file)
    }
  })
}

/** 把文件名扩展名统一改成 .jpg（重编码后实际就是 JPEG） */
function renameToJpg(name) {
  if (!name) return 'photo.jpg'
  return name.replace(/\.[^.]+$/, '') + '.jpg'
}
