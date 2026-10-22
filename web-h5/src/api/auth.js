import request from '@/utils/request'

/** 村民登录：手机号 + 密码 */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 村民自助注册：手机号 + 密码 + 姓名，成功后自动登录返回 token */
export function register(data) {
  return request.post('/auth/register', data)
}

/** 忘记密码 - 重置密码：手机号 + 新密码，成功后自动登录返回 token */
export function resetPassword(data) {
  return request.post('/auth/reset-password', data)
}

/** 修改密码：旧密码 + 新密码（需登录态） */
export function changePassword(data) {
  return request.post('/auth/password', data)
}