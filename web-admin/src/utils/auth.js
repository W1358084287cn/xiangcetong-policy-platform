/** Token 键名（PC 管理端） */
const TOKEN_KEY = 'xct_admin_token'
const USER_KEY = 'xct_admin_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token || '')
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setUser(user) {
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

/** 当前角色 */
export function getRole() {
  return getUser()?.role || ''
}

/** 是否系统管理员 */
export function isSysAdmin() {
  return getRole() === 'SYS_ADMIN'
}

/** 是否村级管理员 */
export function isVillageAdmin() {
  return getRole() === 'VILLAGE_ADMIN'
}

/** 是否镇级管理员 */
export function isTownAdmin() {
  return getRole() === 'TOWN_ADMIN'
}
