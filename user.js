import request from './request'

// 获取用户列表
export function getUserList(params) {
  return request({
    url: '/user/list',
    method: 'get',
    params
  })
}

// 新增用户
export function addUser(data) {
  return request({
    url: '/user/add',
    method: 'post',
    data
  })
}

// 编辑用户
export function editUser(data) {
  return request({
    url: '/user/edit',
    method: 'post',
    data
  })
}

// 删除用户
export function delUser(id) {
  return request({
    url: '/user/delete',
    method: 'post',
    data: { id }
  })
}
