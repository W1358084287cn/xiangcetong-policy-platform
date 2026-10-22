/** 申请状态中文（新增待发放 / 已发放两个发放环节状态） */
export const APPLY_STATUS_MAP = {
  PENDING_VILLAGE: '待村级审核',
  PENDING_TOWN: '待镇级审核',
  APPROVED: '已通过',
  PENDING_DISBURSE: '待发放',
  DISBURSED: '已发放',
  REJECTED: '已驳回',
}

export function formatApplyStatus(status) {
  return APPLY_STATUS_MAP[status] || status || '—'
}

/** 角色中文 */
export const ROLE_MAP = {
  VILLAGER: '村民',
  VILLAGE_ADMIN: '村级管理员',
  TOWN_ADMIN: '镇级管理员',
  SYS_ADMIN: '系统管理员',
}

export function formatRole(role) {
  return ROLE_MAP[role] || role || '—'
}

/** 政策状态中文 */
export function formatPolicyStatus(status) {
  if (status === 'ONLINE') return '已上架'
  if (status === 'OFFLINE') return '已下架'
  return status || '—'
}
