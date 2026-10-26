export function toTree(rows, parentId = null) {
  return rows.filter(row => row.parentId === parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    .map(row => ({ ...row, children: toTree(rows, row.id) }))
}
// 后端存的是原生 HTML 页面地址，Vue 需要显式映射到已有组件。
export const pageRoutes = {
  '/users.html': '/user', '/roles.html': '/roles', '/menus.html': '/menus',
  '/chat.html': '/chat', '/': '/messages'
}
export function navigation(rows) {
  function convert(node) {
    if (node.type === 'BUTTON') return null
    const children = node.children.map(convert).filter(Boolean)
    if (node.type === 'DIRECTORY') return children.length ? { ...node, children } : null
    const route = pageRoutes[node.path]
    return route ? { ...node, route, children: [] } : null
  }
  return toTree(rows).map(convert).filter(Boolean)
}