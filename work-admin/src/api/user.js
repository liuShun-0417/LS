import request from './request'
export const getUserList = (page = 1, keyword = '') =>
  request.get('/api/users', { params: { page, keyword } })
export const getUser = id => request.get(`/api/users/${id}`)
export const createUser = data => request.post('/api/users', data)
export const updateUser = (id, data) => request.put(`/api/users/${id}`, data)
export const deleteUser = id => request.delete(`/api/users/${id}`)