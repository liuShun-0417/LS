import request from './request'
export const registerApi = data => request.post('/api/auth/register', data)
export const loginApi = data => request.post('/api/auth/login', data)
export const logoutApi = () => request.post('/api/auth/logout')
export const getCurrentUser = () => request.get('/api/auth/me')