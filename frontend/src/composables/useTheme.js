import { ref, watch } from 'vue'

/**
 * 全局主题（日间 / 夜间）。
 *
 * - 默认夜间；用户选择存 localStorage，刷新不丢。
 * - 同一份状态是模块级单例，TopBar 里的按钮和独立页面的悬浮按钮
 *   操作的是同一个 ref，不会出现「这边切了那边没变」。
 * - html 上同时打 data-theme 和 .dark：
 *     data-theme 给我们自己的 style.css 用；
 *     .dark 是 Element Plus 生态约定，以后想引官方 dark/css-vars.css 直接可用。
 * - index.html 里有一段内联脚本做同样的事，是为了在 CSS 加载前就定好主题，
 *   避免刷新时先闪一下米白再变黑（FOUC）。
 */
const KEY = 'vw_theme'
const VALID = ['dark', 'light']

function readInitial() {
  try {
    const saved = localStorage.getItem(KEY)
    if (VALID.includes(saved)) return saved
  } catch (e) {
    // 隐私模式下 localStorage 可能不可写，按默认主题走
  }
  return 'dark'
}

const theme = ref(readInitial())

function apply(value) {
  const root = document.documentElement
  root.dataset.theme = value
  root.classList.toggle('dark', value === 'dark')
  root.style.colorScheme = value
  try {
    localStorage.setItem(KEY, value)
  } catch (e) {
    // 存不了不影响本次会话
  }
}

watch(theme, apply, { immediate: true })

export function useTheme() {
  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }
  function setTheme(value) {
    if (VALID.includes(value)) theme.value = value
  }
  return { theme, toggleTheme, setTheme }
}
