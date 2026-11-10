import request from '@/utils/request'

export async function getOverview() {
  const res = await request({ url: '/statistics/overview', method: 'get' })
  return res.data
}
