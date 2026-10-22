/** Token 在 localStorage 中的键名（村民 H5） */
const TOKEN_KEY = 'xct_h5_token'
/** 登录用户信息缓存键 */
const USER_KEY = 'xct_h5_user'

/** 读取登录 Token */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/** 保存登录 Token */
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token || '')
}

/** 清除登录态（退出 / 401） */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 读取缓存的用户信息 */
export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 缓存用户信息（登录成功后） */
export function setUser(user) {
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}
