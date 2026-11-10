<template>
  <header class="topbar">
    <div class="tb-inner">
      <!-- 左：胶囊 logo -->
      <button class="tb-logo" type="button" @click="router.push(homePath)">
        <span class="seal">{{ logoSeal }}</span>
        <span class="tb-logo-text">{{ logoText }}</span>
      </button>

      <!-- 中：横向胶囊导航（取代原来的左侧菜单栏） -->
      <nav class="tb-nav" aria-label="主导航">
        <button
          v-for="it in items"
          :key="it.path"
          class="tb-pill"
          :class="{ active: active === it.path }"
          type="button"
          @click="router.push(it.path)"
        >
          <el-icon :size="17"><component :is="it.icon" /></el-icon>
          <span>{{ it.label }}</span>
        </button>
      </nav>

      <!-- 右：主题切换 / 身份标记 / 用户菜单（由各布局通过 slot 提供） -->
      <div class="tb-right">
        <ThemeToggle />
        <el-tag v-if="badge" :type="badgeType" effect="plain" size="default" class="tb-badge">{{ badge }}</el-tag>
        <slot name="right" />
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import ThemeToggle from '@/components/ThemeToggle.vue'

defineProps({
  items: { type: Array, default: () => [] },
  active: { type: String, default: '' },
  logoText: { type: String, default: '虚拟衣柜' },
  logoSeal: { type: String, default: '衣' },
  homePath: { type: String, default: '/welcome' },
  badge: { type: String, default: '' },
  badgeType: { type: String, default: 'info' }
})

const router = useRouter()
</script>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--vw-bar-bg);
  backdrop-filter: blur(var(--vw-glass-blur));
  -webkit-backdrop-filter: blur(var(--vw-glass-blur));
  border-bottom: 1px solid var(--vw-bar-border);
  transition: background .28s ease, border-color .28s ease;
}
.tb-inner {
  max-width: 1440px;
  margin: 0 auto;
  height: 72px;
  padding: 0 22px;
  /* 三栏 1fr / auto / 1fr：中间导航按内容宽，两侧均分剩余空间，
     这样无论 logo 和用户区多宽，胶囊组都真正居中（不是靠 margin 硬推） */
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 18px;
}

/* logo 胶囊 */
.tb-logo {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
  height: 42px;
  padding: 0 17px 0 9px;
  border-radius: 999px;
  border: 1px solid var(--vw-pill-border);
  background: var(--vw-pill-bg);
  color: var(--vw-ink);
  font-family: var(--vw-serif);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: .1em;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color .2s, color .2s;
}
.tb-logo:hover { border-color: var(--vw-gold); color: var(--vw-gold); }
.tb-logo .seal { width: 27px; height: 27px; font-size: 14px; border-radius: 50%; }

/* 中间胶囊组 */
.tb-nav {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px;
  border-radius: 999px;
  border: 1px solid var(--vw-pill-border);
  background: var(--vw-pill-bg);
  overflow-x: auto;
  scrollbar-width: none;
}
.tb-nav::-webkit-scrollbar { display: none; }
.tb-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--vw-pill-text);
  font-size: 15px;
  letter-spacing: .05em;
  white-space: nowrap;
  cursor: pointer;
  transition: background .2s, color .2s;
}
.tb-pill:hover { color: var(--vw-gold); }
.tb-pill.active {
  background: var(--vw-pill-active);
  color: var(--vw-pill-active-text);
  font-weight: 600;
}

.tb-right { justify-self: end; display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.tb-badge :deep(.el-tag__content) { letter-spacing: .1em; }

/* 用户菜单（slot 里用这个类） */
.tb-right :deep(.user-trigger) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px 0 5px;
  border-radius: 999px;
  border: 1px solid var(--vw-pill-border);
  background: var(--vw-pill-bg);
  color: var(--vw-ink);
  cursor: pointer;
  outline: none;
  font-size: 14px;
}
.tb-right :deep(.el-avatar) { width: 28px; height: 28px; background: var(--vw-surface-2); color: var(--vw-ink); font-family: var(--vw-serif); }

@media (max-width: 1200px) {
  /* 窄屏放不下三栏居中，退回自然排布，导航自己横向滚动 */
  .tb-inner { display: flex; }
  .tb-right { margin-left: auto; }
  .tb-nav { min-width: 0; }
}
@media (max-width: 900px) {
  .tb-logo-text { display: none; }
  .tb-logo { padding-right: 8px; }
  .tb-inner { gap: 10px; padding: 0 12px; height: 64px; }
  .tb-pill { padding: 0 12px; font-size: 14px; }
  .tb-right { gap: 8px; }
}
</style>
