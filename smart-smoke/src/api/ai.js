import request from '@/utils/request'

// 本地 AI 助手接口。chat 走本地 qwen3:4b，慢时可达 2 分钟，单独放宽超时。
export const getAiHealth = () => request.get('/api/forest/ai/health', { timeout: 15000 })

export const aiChat = data => request.post('/api/forest/ai/chat', data, { timeout: 180000 })

export const listAiConversations = () => request.get('/api/forest/ai/conversations')

export const getAiConversation = id => request.get(`/api/forest/ai/conversations/${id}`)

export const deleteAiConversation = id => request.delete(`/api/forest/ai/conversations/${id}`)

export const listKnowledgeDocuments = () => request.get('/api/forest/ai/knowledge/documents')

export const reindexKnowledge = force => request.post('/api/forest/ai/knowledge/reindex', { force: !!force }, { timeout: 30000 })

export const uploadKnowledgeDocument = file => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/api/forest/ai/knowledge/documents', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}
