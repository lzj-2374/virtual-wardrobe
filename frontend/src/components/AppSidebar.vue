<template>
  <div class="app-sidebar" :class="{ collapsed }">
    <!-- 点左上角 logo：回到落地页 -->
    <div class="logo" @click="goLanding" :title="collapsed ? '虚拟衣柜' : ''">
      <span class="seal">衣</span>
      <span v-if="!collapsed" class="logo-text">虚拟衣柜</span>
    </div>

    <el-menu
      :default-active="active"
      :collapse="collapsed"
      :collapse-transition="false"
      router
      class="side-menu"
      background-color="#14161a"
      text-color="#a9a69d"
      active-text-color="#e6d9bd"
    >
      <el-menu-item index="/ai">
        <el-icon><MagicStick /></el-icon>
        <template #title>AI 穿搭助手</template>
      </el-menu-item>
      <el-menu-item index="/wardrobe">
        <el-icon><Suitcase /></el-icon>
        <template #title>我的衣柜</template>
      </el-menu-item>
      <el-menu-item index="/outfits">
        <el-icon><Picture /></el-icon>
        <template #title>我的搭配</template>
      </el-menu-item>
      <el-menu-item index="/profile">
        <el-icon><User /></el-icon>
        <template #title>个人中心</template>
      </el-menu-item>
    </el-menu>

    <!-- 折叠开关 -->
    <div class="side-collapse" @click="emit('toggle')" :title="collapsed ? '展开侧栏' : '收起侧栏'">
      <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
      <span v-if="!collapsed">收起侧栏</span>
    </div>

    <div class="side-foot">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Suitcase, Picture, MagicStick, User, Fold, Expand } from '@element-plus/icons-vue'

defineProps({
  active: { type: String, default: '/ai' },
  collapsed: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle'])
const router = useRouter()

function goLanding() {
  router.push('/welcome')
}
</script>

<style scoped>
.app-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #14161a;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 62px;
  padding: 0 20px;
  color: #f3efe4;
  font-family: var(--vw-serif);
  font-weight: 600;
  font-size: 19px;
  letter-spacing: .16em;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255, 255, 255, .07);
  cursor: pointer;
  transition: color .25s;
}
.logo:hover { color: #e6d9bd; }
.logo .seal { width: 24px; height: 24px; font-size: 14px; }
.side-menu { border-right: none; padding-top: 8px; flex: 1; overflow-y: auto; }
.side-menu :deep(.el-menu-item) {
  height: 46px;
  margin: 2px 10px;
  border-radius: 2px;
  letter-spacing: .08em;
}
.side-menu :deep(.el-menu-item:hover) { background: rgba(255, 255, 255, .05); }
.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(162, 129, 63, .16);
  color: #e6d9bd;
  font-weight: 600;
}
/* 选中项左侧的金色细线 */
.side-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 11px;
  bottom: 11px;
  width: 2px;
  background: var(--vw-gold-2);
}
.side-foot { padding: 12px 0 14px; border-top: 1px solid rgba(255, 255, 255, .06); }

/* 折叠开关：和底部「返回浏览」同一套视觉，常驻在最下面 */
.side-collapse {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #7d7a72;
  padding: 13px 20px;
  cursor: pointer;
  font-size: 13px;
  letter-spacing: .06em;
  border-top: 1px solid rgba(255, 255, 255, .06);
  transition: color .2s, background .2s;
}
.side-collapse:hover { color: #e6d9bd; background: rgba(255, 255, 255, .04); }

/* ---------- 折叠态（64px 图标栏） ---------- */
.app-sidebar.collapsed .logo { padding: 0; justify-content: center; }
.app-sidebar.collapsed .side-collapse { justify-content: center; padding: 13px 0; }
.app-sidebar.collapsed .side-menu :deep(.el-menu-item) { margin: 2px 8px; }
.app-sidebar.collapsed .side-menu :deep(.el-menu-item span) { display: none; }
.app-sidebar.collapsed .side-foot { padding: 10px 0 12px; }
</style>
