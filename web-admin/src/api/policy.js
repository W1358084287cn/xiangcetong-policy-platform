import request from '@/utils/request'

/** 政策列表（SYS_ADMIN） */
export function fetchPolicyList(params) {
  return request.get('/admin/policy/list', { params })
}

/** 新增政策 */
export function createPolicy(data) {
  return request.post('/admin/policy', data)
}

/** 政策详情 */
export function fetchPolicyDetail(id) {
  return request.get(`/admin/policy/${id}`)
}

/** 编辑政策 */
export function updatePolicy(id, data) {
  return request.put(`/admin/policy/${id}`, data)
}

/** 上下架：status = ONLINE | OFFLINE */
export function updatePolicyStatus(id, status) {
  return request.put(`/admin/policy/${id}/status`, { status })
}
