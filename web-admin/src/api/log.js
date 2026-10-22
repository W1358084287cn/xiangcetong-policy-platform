import request from '@/utils/request'

/** 操作日志列表（SYS_ADMIN） */
export function fetchLogList(params) {
  return request.get('/admin/logs', { params })
}
