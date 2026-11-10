import request from '@/utils/request'

export async function login(data) {
  const res = await request({ url: '/user/login', method: 'post', data })
  return res.data
}

export async function register(data) {
  const res = await request({ url: '/user/register', method: 'post', data })
  return res.data
}

export async function getProfile() {
  const res = await request({ url: '/user/profile', method: 'get' })
  return res.data
}

export async function updateProfile(data) {
  const res = await request({ url: '/user/profile', method: 'put', data })
  return res.data
}
