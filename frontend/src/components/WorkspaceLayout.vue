<template>
  <div class="ws-layout">
    <TopBar
      :items="navItems"
      :active="activeMenu"
      logo-text="衣柜工作台"
      logo-seal="台"
      home-path="/ai"
      badge="工作台"
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
              <el-dropdown-item command="browse">返回浏览</el-dropdown-item>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </TopBar>

    <main class="ws-main">
      <router-view />
    </main>

    <!-- 左下角小助手：工作台专用文案与快捷键 -->
    <PetGuide variant="workspace" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Suitcase, Picture, TrendCharts, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import TopBar from '@/components/TopBar.vue'
import PetGuide from '@/components/PetGuide.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const navItems = [
  { path: '/workspace/wardrobe', label: '衣物管理', icon: Suitcase },
  { path: '/workspace/outfits', label: '搭配管理', icon: Picture },
  { path: '/workspace/stats', label: '数据统计', icon: TrendCharts }
]

// /workspace/wardrobe/add -> /workspace/wardrobe；/workspace/outfits/edit -> /workspace/outfits
const activeMenu = computed(() => {
  const seg = route.path.split('/')
  const first = seg[2] || 'wardrobe'
  return `/workspace/${first}`
})
const nickname = computed(() => userStore.nickname)

function onCommand(cmd) {
  if (cmd === 'browse' || cmd === 'profile') router.push(cmd === 'profile' ? '/profile' : '/ai')
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.ws-layout { min-height: 100vh; display: flex; flex-direction: column; }
.ws-main { flex: 1; background: transparent; padding: 0; }
.user-name { letter-spacing: .04em; }
</style>
