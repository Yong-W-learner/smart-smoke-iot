import request from '@/utils/request'

// 全部账号列表（不含密码）
export function getUserList() {
  return request({
    url: '/api/user/list',
    method: 'get'
  })
}

// 新增小区管理员
export function addAdmin(data) {
  return request({
    url: '/api/user/add',
    method: 'post',
    data
  })
}

// 删除账号
export function deleteUser(id) {
  return request({
    url: `/api/user/${id}`,
    method: 'delete'
  })
}
