<template>
  <div class="admin-layout">
    <TopBar
      :items="navItems"
      :active="activeMenu"
      logo-text="虚拟衣柜 · 管理端"
      logo-seal="管"
      home-path="/ai"
      badge="管理员"
      badge-type="danger"
    >
      <template #right>
        <el-dropdown @command="onCommand">
          <span class="user-trigger">
            <el-avatar :size="28">{{ nickname.charAt(0) }}</el-avatar>
            <span class="user-name">{{ nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="home">返回用户端</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </TopBar>

    <main class="admin-main">
      <router-view />
    </main>

    <!-- 左下角小助手：管理端专用文案 -->
    <PetGuide variant="admin" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataAnalysis, UserFilled, Download, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import TopBar from '@/components/TopBar.vue'
import PetGuide from '@/components/PetGuide.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const navItems = [
  { path: '/admin/dashboard', label: '数据报表', icon: DataAnalysis },
  { path: '/admin/users', label: '用户管理', icon: UserFilled },
  { path: '/admin/export', label: '数据导出', icon: Download }
]

const activeMenu = computed(() => '/' + route.path.split('/').slice(1, 3).join('/'))
const nickname = computed(() => userStore.nickname)

function onCommand(cmd) {
  if (cmd === 'home') router.push('/ai')
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; display: flex; flex-direction: column; }
.admin-main { flex: 1; background: transparent; padding: 0; }
.user-name { letter-spacing: .04em; }
</style>
