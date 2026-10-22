import request from '@/utils/request'

/**
 * AI 配置接口（仅 SYS_ADMIN）
 * GET  /admin/ai/config      → 获取当前配置
 * PUT  /admin/ai/config      → 更新配置
 * POST /admin/ai/config/test → 测试连接
 */

/** 获取 AI 配置 */
export function fetchAiConfig() {
  return request.get('/admin/ai/config')
}

/** 更新 AI 配置 */
export function updateAiConfig(data) {
  return request.put('/admin/ai/config', data)
}

/** 测试大模型连接 */
export function testAiConnection() {
  return request.post('/admin/ai/config/test')
}

/** 查询 DeepSeek 账户余额 */
export function queryBalance() {
  return request.get('/admin/ai/config/balance')
}

/** 更新 DeepSeek 开发平台跳转链接 */
export function updatePlatformUrl(platformUrl) {
  return request.put('/admin/ai/config/platform-url', { platformUrl })
}

/**
 * 知识库管理接口（仅 SYS_ADMIN）
 * GET    /admin/knowledge?page=1&size=10 → 文档列表
 * POST   /admin/knowledge/upload         → 上传文档（multipart form）
 * GET    /admin/knowledge/{id}/chunks    → 文档分块详情
 * DELETE /admin/knowledge/{id}           → 删除文档
 * POST   /admin/knowledge/{id}/reprocess → 重新处理
 */

/** 知识库文档列表 */
export function fetchKnowledgeList(params) {
  return request.get('/admin/knowledge', { params })
}

/** 上传知识文档 */
export function uploadKnowledge(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 文档分块详情 */
export function fetchChunkDetail(id) {
  return request.get(`/admin/knowledge/${id}/chunks`)
}

/** 删除文档 */
export function deleteKnowledge(id) {
  return request.delete(`/admin/knowledge/${id}`)
}

/** 重新处理文档 */
export function reprocessKnowledge(id) {
  return request.post(`/admin/knowledge/${id}/reprocess`)
}

/**
 * AI 对话日志接口（仅 SYS_ADMIN）
 * GET /admin/ai/chat-users → 按用户分组列表（不暴露具体内容）
 * GET /admin/ai/chat-logs/{userId} → 某用户对话详情
 */

/** 查询对话用户分组列表 */
export function fetchChatUsers(params) {
  return request.get('/admin/ai/chat-users', { params })
}

/** 查询某用户的对话详情 */
export function fetchChatLogsByUser(userId, params) {
  return request.get(`/admin/ai/chat-logs/${userId}`, { params })
}

/**
 * 知识库重建接口（仅 SYS_ADMIN）
 * POST /admin/policy/rebuild-knowledge
 */

/** 从政策表全量重建知识库 */
export function rebuildKnowledge() {
  return request.post('/admin/policy/rebuild-knowledge')
}