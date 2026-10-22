import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from './auth'
import router from '@/router'

/** 统一 axios：baseURL=/api，自动带 Bearer Token */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截：附加 Authorization
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截：code===0 返回 data；否则 Toast；401 清登录态跳登录
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      return res
    }
    if (res.code === 0) {
      return res.data
    }
    ElMessage.error(res.message || '请求失败')
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
      ElMessage.error(msg || '登录已失效，请重新登录')
      router.replace('/login')
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
