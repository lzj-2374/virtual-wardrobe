import request from '@/utils/request'

// ===== 用户管理 =====
// 分页查询用户
export async function pageUsers(params) {
  const res = await request({ url: '/admin/user/page', method: 'get', params })
  return res.data
}

// 启用 / 停用账号（status：1 启用，0 停用）
export async function updateUserStatus(id, status) {
  const res = await request({ url: `/admin/user/${id}/status`, method: 'put', data: { status } })
  return res.data
}

// 重置用户密码
export async function resetUserPassword(id) {
  const res = await request({ url: `/admin/user/${id}/reset-password`, method: 'post' })
  return res.data
}

// ===== 数据报表 =====
export async function getAdminOverview() {
  const res = await request({ url: '/admin/statistics/overview', method: 'get' })
  return res.data
}

// ===== 数据导出 =====
// type：users 用户 / category-stats 品类分布 / scene-stats 场景分布
// 只导出「账号管理必需字段」与「聚合统计」，不导出任何人的衣物明细
export async function fetchExportRows(type) {
  const res = await request({ url: `/admin/export/${type}`, method: 'get' })
  return res.data
}
