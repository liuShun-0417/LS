let serial = 0
export const messageId = () => `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}_${++serial}`
export const messageKey = m => `${m.groupId}:${m.senderId}:${m.clientMessageId}`
export function mergeMessages(rows, incoming) {
  const key=messageKey(incoming), old=rows.find(m=>messageKey(m)===key)
  const rank={ sending:0, pending:1, failed:2, saved:3 }
  // 已保存状态不可被迟到的 pending/failed 覆盖。
  if(old && rank[old.deliveryStatus]>rank[incoming.deliveryStatus]) return rows
  const next=old ? rows.map(m=>messageKey(m)===key ? {...m,...incoming} : m) : [...rows,incoming]
  return next.sort((a,b)=> {
    if(a.id!=null && b.id!=null) return a.id-b.id
    if(a.id!=null) return -1
    if(b.id!=null) return 1
    return (a.sentAt || '').localeCompare(b.sentAt || '')
  })
}