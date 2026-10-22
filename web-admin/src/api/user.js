import request from '@/utils/request'

/** 用户列表（SYS_ADMIN） */
export function fetchUserList(params) {
  return request.get('/admin/users', { params })
}

/** 创建用户（四角色 + 村镇编码） */
export function createUser(data) {
  return request.post('/admin/users', data)
}

/** 删除用户 */
export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}