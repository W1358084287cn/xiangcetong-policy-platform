import request from '@/utils/request'

/** 上架政策列表（管理/兜底；村民主入口请用 match） */
export function fetchPolicyList(params = {}) {
  return request.get('/policy/list', {
    params: { status: 'ONLINE', pageNum: 1, pageSize: 50, ...params },
  })
}

/**
 * 村民政策匹配（WRS）：按个人资料打分后返回可申报政策
 * @param {object} params plantType/category/townName/includeNone/pageNum/pageSize
 */
export function fetchMatchedPolicies(params = {}) {
  return request.get('/policy/match', {
    params: { pageNum: 1, pageSize: 50, ...params },
  })
}

/** 政策详情（需登录） */
export function fetchPolicyDetail(id) {
  return request.get(`/policy/${id}`)
}

/** 收藏政策 */
export function favoritePolicy(id) {
  return request.post(`/policy/${id}/favorite`)
}

/** 取消收藏 */
export function unfavoritePolicy(id) {
  return request.delete(`/policy/${id}/favorite`)
}

/** 模糊搜索：关键词匹配标题或正文 */
export function searchPolicies(keyword, pageNum = 1, pageSize = 5) {
  return request.get('/policy/search', {
    params: { keyword, pageNum, pageSize },
  })
}