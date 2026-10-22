import request from '@/utils/request'

/** 获取村民个人资料 */
export function fetchProfile() {
  return request.get('/villager/profile')
}

/** 更新村民个人资料 */
export function updateProfile(data) {
  return request.put('/villager/profile', data)
}

/** 我的收藏政策列表（默认每页 5，可被 params 覆盖） */
export function fetchFavorites(params = {}) {
  return request.get('/villager/favorites', {
    params: { pageNum: 1, pageSize: 5, ...params },
  })
}
