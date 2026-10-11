import request from './request'

export function listConversations() {
  return request({
    url: '/ai/conversations',
    method: 'get'
  })
}

export function createConversation(title) {
  return request({
    url: '/ai/conversations',
    method: 'post',
    data: { title }
  })
}

export function renameConversation(id, title) {
  return request({
    url: `/ai/conversations/${id}`,
    method: 'patch',
    data: { title }
  })
}

export function deleteConversation(id) {
  return request({
    url: `/ai/conversations/${id}`,
    method: 'delete'
  })
}

export function getMessages(id) {
  return request({
    url: `/ai/conversations/${id}/messages`,
    method: 'get'
  })
}

export function appendMessage(id, role, content, tokenCount) {
  return request({
    url: `/ai/conversations/${id}/messages`,
    method: 'post',
    data: { role, content, tokenCount: tokenCount || 0 }
  })
}

export function analyzeFoodImage(formData) {
  return request({
    url: '/ai/analyze-food',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function analyzeReport(formData) {
  return request({
    url: '/ai/analyze-report',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== Agent 智能体接口 ====================

/**
 * ReAct Agent 非流式问答
 * 返回完整的 Agent 执行过程（思考步骤、工具调用、最终答案）
 */
export function agentReact(userId, message, history) {
  return request({
    url: '/ai/agent/react',
    method: 'post',
    data: { userId, message, history: history || [] }
  })
}

/**
 * ReAct Agent 流式问答
 * 先执行工具调用，再流式输出最终答案（SSE）
 */
export function agentTools() {
  return request({
    url: '/ai/agent/tools',
    method: 'get'
  })
}
