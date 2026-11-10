<template>
  <div class="layout">
    <TopBar
      :items="navItems"
      :active="activeMenu"
      logo-text="虚拟衣柜"
      logo-seal="衣"
      home-path="/welcome"
    >
      <template #right>
        <el-dropdown @command="onCommand" trigger="click">
          <span class="user-trigger">
            <el-avatar :size="28">{{ nickname.charAt(0) }}</el-avatar>
            <span class="user-name">{{ nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="workspace">进入工作台</el-dropdown-item>
              <el-dropdown-item v-if="canAdmin" command="admin">进入管理端</el-dropdown-item>
              <el-dropdown-item command="landing">回到首页动画</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </TopBar>

    <main class="layout-main" ref="mainRef">
      <router-view />
    </main>

    <!-- 左下角小助手：引导进入工作台 -->
    <PetGuide variant="browse" />
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MagicStick, Suitcase, Picture, User, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import TopBar from '@/components/TopBar.vue'
import PetGuide from '@/components/PetGuide.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mainRef = ref()

const navItems = [
  { path: '/ai', label: 'AI 穿搭助手', icon: MagicStick },
  { path: '/wardrobe', label: '我的衣柜', icon: Suitcase },
  { path: '/outfits', label: '我的搭配', icon: Picture },
  { path: '/profile', label: '个人中心', icon: User }
]
const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'ai'))
const nickname = computed(() => userStore.nickname)
// 管理员（role=1）才能进入管理端
const canAdmin = computed(() => userStore.userInfo?.role === 1)

// 切换页面时把主内容区滚回顶部
watch(
  () => route.path,
  async () => {
    await nextTick()
    mainRef.value?.scrollTo({ top: 0 })
  }
)

function onCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  if (cmd === 'workspace') router.push('/workspace/wardrobe')
  if (cmd === 'admin') router.push('/admin')
  if (cmd === 'landing') router.push('/welcome')
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.layout-main { flex: 1; background: transparent; padding: 0; overflow-y: auto; }
.user-name { letter-spacing: .04em; }
</style>
