<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名 / 昵称"
        clearable
        :prefix-icon="Search"
        style="width: 260px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="statusFilter" placeholder="账号状态" clearable style="width: 140px" @change="onSearch">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <span class="text-muted result-count">共 {{ total }} 个账号</span>
    </div>

    <el-table v-loading="loading" :data="list" stripe class="mt" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="130" />
      <el-table-column label="性别" width="80">
        <template #default="{ row }">{{ row.gender === 2 ? '女' : row.gender === 1 ? '男' : '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.role === 1 ? 'danger' : 'info'" size="small" effect="plain">
            {{ row.role === 1 ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号状态" width="140">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
            :loading="row._loading"
            @change="onToggleStatus(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" min-width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="onReset(row)">重置密码</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无用户数据（需连接后端 /admin/user/page）" :image-size="90" />
      </template>
    </el-table>

    <div class="pager" v-if="total > pageSize">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, updateUserStatus, resetUserPassword } from '@/api/admin'

const keyword = ref('')
const statusFilter = ref('')
const list = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value !== '') params.status = statusFilter.value
    const res = await pageUsers(params)
    list.value = (res.records || []).map((u) => ({ ...u, _loading: false }))
    total.value = res.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  currentPage.value = 1
  load()
}
function onPageChange(p) {
  currentPage.value = p
  load()
}
function onSizeChange(s) {
  pageSize.value = s
  currentPage.value = 1
  load()
}

// 启用 / 停用：先乐观更新，失败则回滚
async function onToggleStatus(row) {
  const next = row.status
  row._loading = true
  try {
    await updateUserStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用该账号' : '已停用该账号')
  } catch (e) {
    row.status = next === 1 ? 0 : 1
  } finally {
    row._loading = false
  }
}

async function onReset(row) {
  try {
    await ElMessageBox.confirm(`确认重置用户「${row.nickname || row.username}」的密码？`, '提示', { type: 'warning' })
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置')
  } catch (e) {
    // 取消或失败
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.result-count { font-size: 13px; }
.mt { margin-top: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
