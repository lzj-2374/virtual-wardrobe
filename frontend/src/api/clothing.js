import request from '@/utils/request'

export async function pageClothing(params) {
  const res = await request({ url: '/clothing/page', method: 'get', params })
  return res.data
}

export async function getClothing(id) {
  const res = await request({ url: `/clothing/${id}`, method: 'get' })
  return res.data
}

export async function createClothing(data) {
  const res = await request({ url: '/clothing', method: 'post', data })
  return res.data
}

export async function updateClothing(id, data) {
  const res = await request({ url: `/clothing/${id}`, method: 'put', data })
  return res.data
}

export async function deleteClothing(id) {
  const res = await request({ url: `/clothing/${id}`, method: 'delete' })
  return res.data
}

// 批量删除（一次请求，后端在一个事务里处理并级联清理 outfit_item）
export async function deleteClothingBatch(ids) {
  const res = await request({ url: '/clothing/batch', method: 'delete', data: { ids } })
  return res.data
}

// AI 识别图片属性，返回建议值供用户确认修改
export async function recognizeClothing(data) {
  const res = await request({ url: '/clothing/recognize', method: 'post', data })
  return res.data
}
