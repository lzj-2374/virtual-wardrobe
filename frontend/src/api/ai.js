import request from '@/utils/request'

// 发起 AI 对话，返回搭配推荐卡片
export async function aiChat(data) {
  const res = await request({ url: '/ai/chat', method: 'post', data })
  return res.data
}

export async function pageConversation(params) {
  const res = await request({ url: '/ai/conversation/page', method: 'get', params })
  return res.data
}

export async function getMessages(conversationId, params) {
  const res = await request({ url: `/ai/conversation/${conversationId}/messages`, method: 'get', params })
  return res.data
}

// 提交生图任务，返回 taskId
export async function generateImage(data) {
  const res = await request({ url: '/ai/image/generate', method: 'post', data })
  return res.data
}

// 查询生图任务状态（轮询）
export async function getImageStatus(taskId) {
  const res = await request({ url: `/ai/image/status/${taskId}`, method: 'get' })
  return res.data
}

// 历史效果图：后端只返回「真的出过图」的推荐，按最近更新倒序
export async function getImageHistory(limit = 24) {
  const res = await request({ url: '/ai/image/history', method: 'get', params: { limit } })
  return res.data
}

// 把 AI 推荐结果放入「我的搭配」（幂等，重复调用返回同一 outfitId）
export async function saveRecommendationAsOutfit(id, data) {
  const res = await request({ url: `/ai/recommendation/${id}/to-outfit`, method: 'post', data })
  return res.data
}
