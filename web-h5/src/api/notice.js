import request from '@/utils/request'

/** 首页滚动字幕：拉取管理员发布的生效通知公告（后端按 sort 倒序，最多 10 条） */
export function getActiveNotices() {
  return request.get('/notice/active')
}
