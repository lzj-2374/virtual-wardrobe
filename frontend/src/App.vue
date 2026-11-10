<template>
  <AmbientBackground />
  <router-view />
  <!-- 独立页面（登录 / 注册 / 落地页）没有 TopBar，给一个右下角悬浮主题按钮 -->
  <ThemeToggle v-if="standalone" floating />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AmbientBackground from '@/components/AmbientBackground.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useTheme } from '@/composables/useTheme'

// 触发一次模块加载，确保主题在应用挂载前就写到 <html> 上
useTheme()

const route = useRoute()
const standalone = computed(() => route.meta.standalone === true)
</script>
