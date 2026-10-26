// 缓存半条事件；一次网络 read() 可能有半条、整条或多条 SSE 事件。
export function createSseParser(onEvent) {
  let buffer = ''
  return text => {
    buffer += text
    let boundary
    while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
      const frame = buffer.slice(0, boundary.index)
      buffer = buffer.slice(boundary.index + boundary[0].length)
      let event = 'message'
      const data = []
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
      }
      if (data.length) onEvent(event, JSON.parse(data.join('\n')))
      // 只有冒号注释的心跳帧不触发业务回调。
    }
    if (buffer.length > 2 * 1024 * 1024) throw new Error('流式数据格式异常')
  }
}
export function buildHistory(turns, question) {
  const result = []
  let size = question.length
  const completed = turns.filter(t => t.status === 'completed').slice(-20)
  for (const turn of completed.reverse()) {
    const length = turn.question.length + turn.answer.length
    if (turn.answer.length > 32000 || size + length > 64000) break
    result.unshift({ role: 'user', content: turn.question }, { role: 'assistant', content: turn.answer })
    size += length
  }
  return result
}