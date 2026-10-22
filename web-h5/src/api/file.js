import { uploadRequest } from '@/utils/request'

/**
 * 上传申报材料图片（multipart）
 *
 * 使用 180 秒超时的上传专用实例：4G/弱网下即使经过客户端压缩，
 * 大图上传仍可能超过默认 30 秒，避免被提前中断而误报失败。
 *
 * @param {File} file 图片文件（建议先经 compressImage 压缩）
 * @param {(percent:number)=>void} [onProgress] 上传进度回调（0-100）
 */
export function uploadFile(file, onProgress) {
  const form = new FormData()
  form.append('file', file)
  return uploadRequest.post('/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      if (!onProgress || !e.total) return
      onProgress(Math.round((e.loaded * 100) / e.total))
    },
  })
}
