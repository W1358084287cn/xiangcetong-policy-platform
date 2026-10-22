import request from '@/utils/request'

/**
 * 通知管理接口（仅 SYS_ADMIN 系统管理端）
 * 契约：
 *  GET    /admin/notices?page=1&size=10  → { list, total }
 *  POST   /admin/notices                 → { title, content, status, sort }
 *  PUT    /admin/notices/{id}            → 更新（快捷上下线时仅传 { status }）
 *  DELETE /admin/notices/{id}
 * status：ONLINE / OFFLINE；sort 越大越靠前（由后端排序）。
 */

/** 通知分页列表 */
export function fetchNoticeList(params) {
  return request.get('/admin/notices', { params })
}

/** 新增通知 */
export function createNotice(data) {
  return request.post('/admin/notices', data)
}

/** 编辑通知 */
export function updateNotice(id, data) {
  return request.put(`/admin/notices/${id}`, data)
}

/** 删除通知 */
export function deleteNotice(id) {
  return request.delete(`/admin/notices/${id}`)
}
