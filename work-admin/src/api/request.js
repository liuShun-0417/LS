import axios from 'axios'
import { ElMessage } from 'element-plus'
import store from '../store'
let unauthorizedHandler = () => {}
export function setUnauthorizedHandler(handler) { unauthorizedHandler = handler }
export function expireSession(token) {
  if (token !== store.state.token) return
  store.commit('clearUserInfo'); unauthorizedHandler()
}
const request = axios.create({ baseURL: process.env.VUE_APP_API_BASE_URL || '/', timeout: 10000 })
request.interceptors.request.use(config => {
  config.sessionToken = store.state.token
  if (!config.skipAuth && config.sessionToken) config.headers.satoken = config.sessionToken
  return config
})
request.interceptors.response.use(response => {
  if (!response.config.skipAuth && response.config.sessionToken !== store.state.token) {
    return Promise.reject(new Error('已忽略旧账号的响应'))
  }
  return response.data
}, error => {
  const config = error.config || {}
  if (!config.skipAuth && config.sessionToken !== store.state.token) return Promise.reject(error)
  let body = error.response?.data
  if (typeof body === 'string') { try { body = JSON.parse(body) } catch { body = null } }
  error.userMessage = body?.message || (error.code === 'ECONNABORTED' ? '请求超时' : '')
    || (!error.response ? '网络连接失败，请检查代理与后端' : `请求失败（${error.response.status}）`)
  if (error.response?.status === 401 && !config.skipAuth) expireSession(config.sessionToken)
  else if (!config.silentError) ElMessage.error(error.userMessage)
  return Promise.reject(error)
})
export default request