<template>
  <button
    class="theme-toggle"
    :class="{ floating }"
    type="button"
    :title="theme === 'dark' ? '切换到日间' : '切换到夜间'"
    :aria-label="theme === 'dark' ? '切换到日间' : '切换到夜间'"
    @click="toggleTheme"
  >
    <el-icon :size="17"><component :is="theme === 'dark' ? Sunny : Moon" /></el-icon>
    <span v-if="floating" class="tg-label">{{ theme === 'dark' ? '日间' : '夜间' }}</span>
  </button>
</template>

<script setup>
import { Sunny, Moon } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

// floating：独立页面（登录 / 注册 / 落地页）没有顶栏时，改用右下角悬浮按钮
defineProps({ floating: { type: Boolean, default: false } })
const { theme, toggleTheme } = useTheme()
</script>

<style scoped>
.theme-toggle {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0;
  border-radius: 999px;
  border: 1px solid var(--vw-pill-border);
  background: var(--vw-pill-bg);
  color: var(--vw-pill-text);
  cursor: pointer;
  flex-shrink: 0;
  transition: background .2s, color .2s, transform .2s, border-color .2s;
}
.theme-toggle:hover {
  color: var(--vw-gold);
  border-color: var(--vw-gold);
  transform: rotate(12deg);
}
.theme-toggle.floating {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 2000;
  width: auto;
  height: 38px;
  padding: 0 14px;
  background: var(--vw-bar-bg);
  backdrop-filter: blur(var(--vw-glass-blur));
  -webkit-backdrop-filter: blur(var(--vw-glass-blur));
  box-shadow: var(--vw-shadow);
}
.tg-label { font-size: 12px; letter-spacing: .08em; }
</style>
