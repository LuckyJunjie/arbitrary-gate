import axios from 'axios'

// TODO: 配置实际 API Base URL（环境变量注入）
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const api = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：注入 token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一错误处理
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        // TODO: 跳转登录
        console.warn('[API] Unauthorized, redirect to login')
      }
      console.error(`[API] Error ${status}:`, data)
    } else {
      console.error('[API] Network error:', error.message)
    }
    return Promise.reject(error)
  }
)

export default api
