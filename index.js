import Mock from 'mockjs'

// 可变数组：Mock 必须维护一份可变数据，否则"保存成功"后重新查询又回到初始值。
// 数据仅在当前页面内存中存在，刷新浏览器会重置。
let users = [
  { id: 1, username: '张三', phone: '13800138000', role: 'admin' },
  { id: 2, username: '李四', phone: '13900139000', role: 'user' },
  { id: 3, username: '王五', phone: '13700137000', role: 'user' }
]
let nextId = 4

function readBody(options) {
  try {
    return JSON.parse(options.body || '{}')
  } catch {
    return {}
  }
}

// 列表：带查询参数，必须用正则匹配（字符串精确匹配会匹配不上）
Mock.mock(/\/api\/user\/list(?:\?.*)?$/, 'get', (options) => {
  const url = new URL(options.url, window.location.origin)
  const keyword = (url.searchParams.get('keyword') || '').trim()
  return {
    code: 200,
    data: users
      .filter((user) => user.username.includes(keyword))
      .map((user) => ({ ...user }))
  }
})

// 新增
Mock.mock('/api/user/add', 'post', (options) => {
  const body = readBody(options)
  if (!body.username || !body.username.trim()) {
    return { code: 400, msg: '用户名不能为空' }
  }
  users.push({
    id: nextId++,
    username: body.username.trim(),
    phone: body.phone || '',
    role: body.role || 'user'
  })
  return { code: 200, msg: '新增成功' }
})

// 编辑
Mock.mock('/api/user/edit', 'post', (options) => {
  const body = readBody(options)
  const index = users.findIndex((user) => user.id === Number(body.id))
  if (index === -1) return { code: 404, msg: '用户不存在' }
  users[index] = { ...users[index], ...body, id: users[index].id }
  return { code: 200, msg: '编辑成功' }
})

// 删除
Mock.mock('/api/user/delete', 'post', (options) => {
  const { id } = readBody(options)
  const before = users.length
  users = users.filter((user) => user.id !== Number(id))
  return before === users.length
    ? { code: 404, msg: '用户不存在' }
    : { code: 200, msg: '删除成功' }
})
