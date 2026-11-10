import request from '@/utils/request'

export async function pageOutfit(params) {
  const res = await request({ url: '/outfit/page', method: 'get', params })
  return res.data
}

export async function getOutfit(id) {
  const res = await request({ url: `/outfit/${id}`, method: 'get' })
  return res.data
}

export async function createOutfit(data) {
  const res = await request({ url: '/outfit', method: 'post', data })
  return res.data
}

export async function updateOutfit(id, data) {
  const res = await request({ url: `/outfit/${id}`, method: 'put', data })
  return res.data
}

export async function deleteOutfit(id) {
  const res = await request({ url: `/outfit/${id}`, method: 'delete' })
  return res.data
}
