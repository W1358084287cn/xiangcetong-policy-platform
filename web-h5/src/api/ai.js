import request from '@/utils/request'
import { aiRequest } from '@/utils/request'

/**
 * 通用 SSE 流式请求
 *
 * 注意两处关键点：
 *  1) Token 必须取 `xct_h5_token`（与 utils/auth 一致），旧代码取 'token' 会导致 401；
 *  2) 用 AbortController 设 90 秒超时——大模型 + 联网搜索链路偶发长时间无响应，
 *     无超时会让页面无限等待。
 */
function sseFetch(url, data) {
  const token = localStorage.getItem('xct_h5_token')
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 90000)
  return fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  }).finally(() => clearTimeout(timeoutId))
}

/**
 * H5 村民端 AI 对话接口
 * POST /api/ai/chat         → 同步对话
 * POST /api/ai/chat/stream  → SSE 流式对话
 */

/** AI 对话（同步） */
export function aiChat(data) {
  return aiRequest.post('/ai/chat', data)
}

/** AI 对话（流式，返回 ReadableStream） */
export function aiChatStream(data) {
  return sseFetch('/api/ai/chat/stream', data)
}

// ==================== AI 管家 ====================

/** 获取欢迎页摘要 */
export function agentWelcome() {
  return aiRequest.post('/ai/agent/welcome')
}

/** Agent 同步对话 */
export function agentChat(data) {
  return aiRequest.post('/ai/agent/chat', data)
}

/** Agent SSE 流式对话（返回 fetch Response） */
export function agentChatStream(data) {
  return sseFetch('/api/ai/agent/chat/stream', data)
}

// ==================== 对话 CRUD ====================

/** 新建对话 */
export function createConversation() {
  return request.post('/ai/conversations')
}

/** 我的对话列表 */
export function listConversations() {
  return request.get('/ai/conversations')
}

/** 对话详情（含消息分页） */
export function getConversation(id, page = 1, size = 50) {
  return request.get('/ai/conversations/' + id, { params: { page, size } })
}

/** 重命名对话 */
export function renameConversation(id, title) {
  return request.put('/ai/conversations/' + id, { title })
}

/** 切换置顶 */
export function togglePinConversation(id) {
  return request.put('/ai/conversations/' + id + '/pin')
}

/** 删除对话 */
export function deleteConversation(id) {
  return request.delete('/ai/conversations/' + id)
}