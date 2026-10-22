import axios from 'axios'
import { showToast } from 'vant'
import { getToken, clearAuth } from './auth'
import router from '@/router'

/** 统一 axios 实例：baseURL=/api，自动带 Token */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

/** AI 对话专用实例（链路较长：联网搜索 + 大模型，需要更长超时） */
export const aiRequest = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

/** 文件上传专用实例：4G/弱网下大图上传可能较久，超时放宽到 180 秒 */
export const uploadRequest = axios.create({
  baseURL: '/api',
  timeout: 180000,
})

/** 给一个 axios 实例挂载通用拦截器 */
function setupInterceptors(instance) {
  instance.interceptors.request.use((config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const res = response.data
      if (res == null || typeof res !== 'object' || !('code' in res)) {
        return res
      }
      if (res.code === 0) {
        return res.data
      }
      showToast(res.message || '请求失败')
      if (res.code === 40101) {
        clearAuth()
        router.replace('/login')
      }
      return Promise.reject(res)
    },
    (error) => {
      const status = error.response?.status
      const data = error.response?.data
      const msg = data?.message || error.message || '网络异常'
      if (status === 401 || data?.code === 40101) {
        clearAuth()
        showToast(msg || '登录已失效，请重新登录')
        router.replace('/login')
      } else {
        showToast(msg)
      }
      return Promise.reject(error)
    }
  )
}

setupInterceptors(request)
setupInterceptors(aiRequest)
setupInterceptors(uploadRequest)

// 上传实例的超时文案单独优化（默认文案是「网络异常」，对上传场景不够明确）
uploadRequest.interceptors.response.use(
  (response) => response,
  (error) => {
    const isTimeout = error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')
    if (isTimeout) {
      showToast('上传超时，请检查网络后重试')
    }
    return Promise.reject(error)
  }
)

export default request