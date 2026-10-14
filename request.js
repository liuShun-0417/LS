import axios from 'axios'

// 统一前缀走环境变量（第 10 课）
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API,
  timeout: 5000
})

// 请求拦截器：自动带上 token
service.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.token = token
  return config
})

// 响应拦截器：剥掉一层 res.data，并按业务码 reject
service.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result && result.code !== 200) {
      return Promise.reject(new Error(result.msg || '请求失败'))
    }
    return result
  },
  (error) => Promise.reject(error)
)

export default service
