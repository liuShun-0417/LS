import request from './request'
export const getGroups = (afterId = 0) => request.get('/api/groups', { params: { afterId } })
export const createGroup = name => request.post('/api/groups', { name })
export const joinGroup = id => request.post(`/api/groups/${id}/join`)
export const getHistory = (id, afterId = 0) => request.get(`/api/groups/${id}/messages`, { params: { afterId } })