import { computed, ref, watch } from 'vue'

/**
 * 左侧菜单栏折叠状态（浏览态 / 工作台 / 管理端三套布局共用）
 * - 展开 210px，折叠 64px（与 Element Plus el-menu 的折叠宽度对齐）
 * - 用 localStorage 记忆，三个界面之间切换保持一致
 * - 同时把宽度写进 CSS 变量 --vw-aside，左下角角色「小衣」靠它跟随
 */
const KEY = 'vw_sidebar_collapsed'

const collapsed = ref(localStorage.getItem(KEY) === '1')
const asideWidth = computed(() => (collapsed.value ? '64px' : '210px'))

watch(
  asideWidth,
  (w) => document.documentElement.style.setProperty('--vw-aside', w),
  { immediate: true }
)

export function useSidebar() {
  function toggle() {
    collapsed.value = !collapsed.value
    localStorage.setItem(KEY, collapsed.value ? '1' : '0')
  }
  return { collapsed, asideWidth, toggle }
}
