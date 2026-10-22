import request from '@/utils/request'

/**
 * 管理端登录：账号 + 密码 + 本端角色
 * 契约：POST /api/auth/login，body 为 { username, password, role }，
 * role 为 VILLAGE_ADMIN / TOWN_ADMIN / SYS_ADMIN 之一，成功返回 { token, user }。
 */
export function login(data) {
  return request.post('/auth/login', data)
}
