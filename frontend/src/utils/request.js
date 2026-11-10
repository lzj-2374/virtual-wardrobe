import axios from 'axios'
import { ElMessage } from 'element-plus'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api'

const service = axios.create({
  baseURL: BASE_URL,
  timeout: 15000
})

// 请求拦截：注入 JWT
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：统一结构处理
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误，请确认后端服务已启动'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/**
 * 统一请求入口：所有请求走真实后端（baseURL 见 .env 的 VITE_API_BASE）。
 * 后端返回统一结构 { code, message, data }，由响应拦截器处理。
 */
export default function request(config) {
  return service(config)
}
