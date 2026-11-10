<template>
  <div v-if="show" class="ambient"></div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
// 仅登录 / 注册页：纯黑底，切合水墨主题
const show = computed(() => ['Login', 'Register'].includes(route.name))
</script>

<style scoped>
.ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  /* 跟随主题：夜间纯黑底 + 一点暖光，日间回到宣纸渐变 */
  background: var(--vw-ambient, #000);
  background-image: var(--vw-ambient-glow, radial-gradient(58% 46% at 50% 42%, rgba(162, 129, 63, .1) 0%, rgba(0, 0, 0, 0) 70%));
  transition: background .28s ease;
}
</style>
