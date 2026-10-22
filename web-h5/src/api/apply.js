import request from '@/utils/request'

/** 保存/更新申报草稿 */
export function saveDraft(data) {
  return request.post('/apply/draft', data)
}

/** 查询某政策草稿（无草稿时仍可能带 suggestedPrefill） */
export function fetchDraft(policyId) {
  return request.get('/apply/draft', { params: { policyId } })
}

/** 提交草稿进入村审 */
export function submitDraft(id, data) {
  return request.post(`/apply/${id}/submit-draft`, data || {})
}

/** 我的补贴记录（默认每页 5；后端按 created_at 倒序） */
export function fetchMyApplies(params = {}) {
  return request.get('/apply/mine', {
    params: { pageNum: 1, pageSize: 5, ...params },
  })
}

/** 村民查看本人某条申报详情（含 materialData / 审批意见） */
export function fetchApplyDetail(id) {
  return request.get(`/apply/${id}`)
}
