import axios from 'axios'
import { Message } from 'element-ui'

// 1. 创建 axios 实例
const service = axios.create({
  // 自动根据环境读取 .env 文件中的 VUE_APP_BASE_API
  baseURL: process.env.VUE_APP_BASE_API, 
  // 请求超时时间
  timeout: 60000 
})

// 2. 请求拦截器 (可以在这里统一加 Token 等)
service.interceptors.request.use(
  config => {
    // 比如: config.headers['Authorization'] = getToken()
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)

// 3. 响应拦截器 (可以在这里统一处理错误)
service.interceptors.response.use(
  response => {
    return response
  },
  error => {
    console.log('err' + error)
    Message({
      message: error.message || '请求失败',
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)

export default service