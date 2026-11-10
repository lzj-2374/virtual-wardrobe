<template>
  <div class="bubble-row" :class="role === 'user' ? 'right' : 'left'">
    <el-avatar v-if="role === 'ai'" :size="32" class="avatar">AI</el-avatar>
    <div class="bubble" :class="role">
      <slot />
    </div>
    <el-avatar v-if="role === 'user'" :size="32" class="avatar user">{{ nickname.charAt(0) }}</el-avatar>
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import { computed } from 'vue'

const props = defineProps({
  role: { type: String, default: 'ai' } // 'user' | 'ai'
})
const userStore = useUserStore()
const nickname = computed(() => userStore.nickname)
</script>

<style scoped>
.bubble-row { display: flex; gap: 8px; margin-bottom: 14px; align-items: flex-start; }
.bubble-row.right { flex-direction: row-reverse; }
.avatar { background: #24272c; color: #f4f0e4; flex-shrink: 0; font-family: var(--vw-serif); }
.avatar.user { background: #a2813f; }

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 3px;
  line-height: 1.6;
  font-size: 14px;
  border: 1px solid transparent;     /* 先占位，避免两种气泡因为有无边框差 1px 高度 */
  box-shadow: var(--vw-bubble-shadow);
  transition: background-color .2s ease, border-color .2s ease;
}
/* AI：半透明玻璃。夜间靠更亮的内层 + 更实的描边 + 模糊把轮廓撑出来，
   否则整块气和背景同色，看不出消息边界 */
.bubble.ai {
  background: var(--vw-bubble-ai-bg);
  border-color: var(--vw-bubble-ai-border);
  backdrop-filter: blur(var(--vw-glass-blur));
  -webkit-backdrop-filter: blur(var(--vw-glass-blur));
}
/* 用户：另一个色系（夜间是描金），跟左侧冷白玻璃形成区分，不用读文字就能认出谁说的 */
.bubble.user {
  background: var(--vw-bubble-user-bg);
  border-color: var(--vw-bubble-user-border);
  color: var(--vw-bubble-user-text);
}
</style>
