import request from '@/utils/request'

/** 管理端申请列表（分页） */
export function fetchApplyList(params) {
  return request.get('/admin/apply', { params })
}

/** 村级初审：action = PASS | REJECT */
export function villageAudit(id, data) {
  return request.post(`/admin/apply/${id}/village-audit`, data)
}

/** 镇级终审：action = PASS | REJECT */
export function townAudit(id, data) {
  return request.post(`/admin/apply/${id}/town-audit`, data)
}

/** 审批统计：返回 { pendingCount, todayCount, monthCount } */
export function fetchApplyStats() {
  return request.get('/admin/apply/stats')
}

/** 发放补贴：body { amount }，金额 >0 且最多 2 位小数 */
export function disburse(id, data) {
  return request.post(`/admin/apply/${id}/disburse`, data)
}
