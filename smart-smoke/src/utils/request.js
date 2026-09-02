import axios from 'axios'

// 默认通过当前前端站点访问接口，由 Vite/Nginx 将 /api 转发到 Spring Boot。
// 如需前后端分别部署，仍可通过 VITE_API_BASE_URL 指定后端公网地址。
const apiHost = import.meta.env.VITE_API_BASE_URL || ''

const service = axios.create({
  baseURL: apiHost,
  timeout: 10000
})

service.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  res => {
    return res.data
  },
  err => {
    console.error("请求错误：", err)
    return Promise.reject(err)
  }
)

export default service
