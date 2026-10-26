import store from '../store'
import { expireSession } from './request'
import { createSseParser } from '../utils/sse'
export async function streamMessage(body, { signal, onEvent }) {
  const token = store.state.token
  const controller = new AbortController()
  const abort = () => controller.abort()
  signal.addEventListener('abort', abort, { once: true })
  if (signal.aborted) abort()
  let timer, reader, timedOut = false, completed = false
  const resetTimer = () => {
    clearTimeout(timer)
    timer = setTimeout(() => { timedOut = true; controller.abort() }, 45000)
  }
  resetTimer()
  try {
    const base = (process.env.VUE_APP_API_BASE_URL || '/').replace(/\/$/, '')
    const response = await fetch(`${base}/api/messages`, {
      method: 'POST', signal: controller.signal,
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json', satoken: token },
      body: JSON.stringify(body)
    })
    if (token !== store.state.token) throw new Error('登录账号已变更')
    if (!response.ok) {
      if (response.status === 401) expireSession(token)
      const error = await response.json().catch(() => null)
      throw new Error(error?.message || `请求失败（${response.status}）`)
    }
    if (!response.headers.get('Content-Type')?.includes('text/event-stream') || !response.body) throw new Error('后端未返回 SSE，请检查版本')
    const parse = createSseParser((event, data) => {
      if (completed) return
      if (event === 'error') throw new Error(data.message || '生成失败')
      if (event === 'done') {
        if (data.status !== 'completed') throw new Error('回复未完成')
        completed = true
      }
      onEvent(event, data)
    })
    reader = response.body.getReader()
    const decoder = new TextDecoder()
    while (!completed) {
      const { value, done } = await reader.read()
      if (token !== store.state.token) throw new Error('登录账号已变更')
      resetTimer()
      parse(done ? decoder.decode() : decoder.decode(value, { stream: true }))
      if (done) break
    }
    if (!completed) throw new Error('连接中断，回复未完成')
  } catch (error) {
    if (timedOut) throw new Error('45 秒未收到数据，请重试')
    throw error
  } finally {
    clearTimeout(timer); signal.removeEventListener('abort', abort)
    if (reader) { await reader.cancel().catch(() => {}); reader.releaseLock() }
    controller.abort()
  }
}