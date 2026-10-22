/**
 * 申请状态 → 中文（后端可能已返回 statusLabel，此处作兜底）。
 * 2026-10 新增「待发放 PENDING_DISBURSE」「已发放 DISBURSED」两个发放阶段状态，
 * 与原有 APPROVED（已通过）并存，用于村民端展示发放进度。
 */
const STATUS_MAP = {
  DRAFT: '草稿',
  PENDING_VILLAGE: '待村级审核',
  PENDING_TOWN: '待镇级审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  PENDING_DISBURSE: '待发放',
  DISBURSED: '已发放',
}

/**
 * 把申请状态码转成中文展示
 * @param {string} status 状态枚举
 * @param {string} [label] 后端给的中文，优先使用
 */
export function formatApplyStatus(status, label) {
  if (label) return label
  return STATUS_MAP[status] || status || '未知'
}
