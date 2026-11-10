<template>
  <div
    ref="rootRef"
    class="pet-guide"
    :class="{ dragging, flipped }"
    :style="guideStyle"
    v-if="visible"
    @pointerdown="onPointerDown"
    @click.capture="onClickCapture"
    @contextmenu.prevent="onContextMenu"
  >
    <!-- 气泡：自我介绍 ↔ 当前页面功能，自动轮换 -->
    <transition name="pet-fade">
      <div v-if="bubbleVisible" class="pet-bubble" @click="onPetClick" title="点一下换句话">
        <span class="pet-speech" v-html="curTip"></span>
        <span class="pet-x" @click.stop="dismiss">×</span>
      </div>
    </transition>

    <!-- 点开后的小面板 -->
    <transition name="pet-fade">
      <div v-if="panelOpen" class="pet-panel">
        <div
          v-for="it in cfg.items"
          :key="it.title"
          class="p-item"
          @click="go(it.path)"
        >
          <span class="p-ico">{{ it.key }}</span>
          <span class="p-text">
            <b>{{ it.title }}</b>
            <small>{{ it.sub }}</small>
          </span>
        </div>
        <div class="p-reset" @click="resetPos">↺ 复原位置</div>
      </div>
    </transition>

    <!-- 角色本体：小衣（日系 Q 版立绘，透明底；两帧轮播实现眨眼） -->
    <div ref="bodyRef" class="pet-body" :class="{ open: panelOpen }" @click="onPetClick" title="左键：聊天 ｜ 右键：菜单 ｜ 按住可拖动">
      <div ref="avatarRef" class="pet-avatar">
        <img :src="blinking ? xiaoyiBlink : xiaoyi" alt="小衣" class="pet-img" draggable="false" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import xiaoyi from '@/assets/xiaoyi.png'
import xiaoyiBlink from '@/assets/xiaoyi-blink.png'

const props = defineProps({
  // browse 浏览态 / workspace 工作台 / admin 管理端
  variant: { type: String, default: 'browse' }
})

const INTRO = '我叫<em>小衣</em>，你的衣柜小管家'

// 每个页面一句话，路由切换后小衣就讲这一页能干什么
const PAGE_TIPS = {
  AiAssistant: '说说场合或天气，我在你的衣服里给你搭一套',
  WardrobeView: '这里只能看不能改，想改就点我去<em>工作台</em>',
  ClothingDetail: '这件衣服的品类、颜色、标签都在这儿',
  OutfitsView: '点开任意一套，能看到它用了哪几件',
  Profile: '身高、尺码填一下，我推荐会更准',
  Wardrobe: '在这儿能加衣服、改衣服，还能批量删',
  ClothingAdd: '传张照片，我帮你把品类和颜色认出来',
  WorkspaceClothing: '改完记得点<em>保存修改</em>，别白改啦',
  Outfits: '新建一套搭配，五个位置各挑一件',
  OutfitEdit: '左边挑单品，右边传封面，4:3 最好看',
  Statistics: '看看你的衣柜构成，哪类偏多一眼就知道',
  AdminDashboard: '整个平台有多少用户、多少衣物，都在这儿',
  AdminUsers: '能停用账号、重置密码，但看不到人家的衣服',
  AdminExport: '导出账号字段和统计，不含任何人的衣物内容'
}

const CONFIG = {
  browse: {
    items: [
      { key: '台', title: '进工作台', sub: '增删改衣物与搭配', path: '/workspace/wardrobe' },
      { key: 'AI', title: '问 AI 穿搭', sub: '基于真实衣柜推荐', path: '/ai' }
    ]
  },
  workspace: {
    items: [
      { key: '＋', title: '添加衣物', sub: '上传图，AI 自动识别', path: '/workspace/wardrobe/add' },
      { key: '数', title: '数据统计', sub: '品类与风格分布', path: '/workspace/stats' },
      { key: '回', title: '返回浏览', sub: '回到 AI 穿搭助手', path: '/ai' }
    ]
  },
  admin: {
    items: [
      { key: '报', title: '数据报表', sub: '平台指标与趋势', path: '/admin/dashboard' },
      { key: '户', title: '用户管理', sub: '启停账号、重置密码', path: '/admin/users' },
      { key: '回', title: '返回用户端', sub: '回到 AI 穿搭助手', path: '/ai' }
    ]
  }
}

const route = useRoute()
const router = useRouter()

const cfg = computed(() => CONFIG[props.variant] || CONFIG.browse)

const visible = ref(true)
const bubbleVisible = ref(false)
const panelOpen = ref(false)
const idx = ref(0)
let timer = null
let blinkTimer = null

// 眨眼：两张图轮播，睁眼 → 闭眼 130ms → 睁眼，间隔 3.4~5.6s 随机
const blinking = ref(false)
function scheduleBlink() {
  const tick = () => {
    blinking.value = true
    setTimeout(() => { blinking.value = false }, 130)
    blinkTimer = setTimeout(tick, 3400 + Math.random() * 2200)
  }
  blinkTimer = setTimeout(tick, 1800)
}

/* ---------------- 拖动 ----------------
   用 Pointer Events 而不是 mousedown/mousemove：鼠标、触屏、手写笔一套代码。
   位置存 localStorage，切换布局（组件重新挂载）后不会弹回原位。

   锚点必须用「左下角」而不是左上角：
   这个容器是 flex column，顺序是 气泡 → 面板 → 小衣本体，小衣在最底下。
   气泡显隐、面板展开都会改变容器高度，用 top 定位时顶边不动、底边就会上下跳，
   表现是「气泡不动、小衣乱跑」。改成 left + bottom 后，小衣固定，气泡向上展开。
   -------------------------------------- */
const POS_KEY = 'vw_xiaoyi_pos_v2'   // v1 存的是 {x, y} 顶边坐标，格式不兼容，换 key 让旧数据自然失效
const rootRef = ref(null)
const bodyRef = ref(null)
const avatarRef = ref(null)
const pos = ref(null)            // { x, bottom }：x=距视口左边，bottom=距视口底边；null 用 CSS 默认左下角
const dragging = ref(false)
const flipped = ref(false)       // 小衣是否水平镜像（在屏幕右半边时转身朝内）
let defaultX = 20                // CSS 默认 left（--vw-aside + 20px），挂载后实测覆盖
let drag = null                  // 一次拖动的现场数据
let suppressClick = false        // 拖完要吃掉紧随其后的那次 click，否则会误触面板/跳转
let unsuppressTimer = null

const guideStyle = computed(() => pos.value
  ? { left: `${pos.value.x}px`, bottom: `${pos.value.bottom}px`, top: 'auto' }
  : null)

function loadPos() {
  try {
    const raw = localStorage.getItem(POS_KEY)
    if (!raw) return null
    const p = JSON.parse(raw)
    return (typeof p?.x === 'number' && typeof p?.bottom === 'number') ? p : null
  } catch (e) {
    return null
  }
}
function savePos() {
  try {
    if (pos.value) localStorage.setItem(POS_KEY, JSON.stringify(pos.value))
  } catch (e) { /* 隐私模式下存不了，不影响本次拖动 */ }
}
// 夹在视口内：窗口变小、气泡/面板展开变高时也不会把内容顶出屏幕
function clamp(x, bottom) {
  const el = rootRef.value
  const w = el?.offsetWidth || 220
  const h = el?.offsetHeight || 240
  const maxX = Math.max(8, window.innerWidth - w - 8)
  // bottom 越大 = 元素整体越靠上；要保证元素顶边不越过视口顶部
  const maxBottom = Math.max(8, window.innerHeight - h - 8)
  return {
    x: Math.min(Math.max(8, x), maxX),
    bottom: Math.min(Math.max(8, bottom), maxBottom)
  }
}
function clampIntoView() {
  if (pos.value) pos.value = clamp(pos.value.x, pos.value.bottom)
}
function onResize() {
  clampIntoView()
  updateFacing(pos.value?.x ?? defaultX)
}

/* 右半屏自动转身：只镜像人物立绘，不翻整个容器，
   否则气泡和菜单里的中文会跟着变成反字。
   用「小衣本体」的中心判断，而不是容器中心——容器宽度由气泡决定，
   人物靠左对齐，两者中心并不重合。
   中线两侧各留 8px 死区，避免拖动经过正中时来回抖。 */
function updateFacing(x) {
  const bodyW = bodyRef.value?.offsetWidth || 90
  const center = x + bodyW / 2
  const half = window.innerWidth / 2
  if (!flipped.value && center > half + 8) flipped.value = true
  else if (flipped.value && center < half - 8) flipped.value = false
}

function onPointerDown(e) {
  if (e.pointerType === 'mouse' && e.button !== 0) return   // 右键交给 contextmenu，不启动拖动
  if (!rootRef.value) return
  const rect = rootRef.value.getBoundingClientRect()
  drag = {
    id: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    originX: rect.left,
    originBottom: window.innerHeight - rect.bottom,
    moved: false
  }
  dragging.value = true
  // 监听挂在 window 上而不是用 setPointerCapture：
  // 指针捕获会把随后的 click 目标强制改成捕获元素，子元素的 @click（开菜单、跳转、
  // 关气泡）就全都收不到了 —— 上一版"单击没反应"就是这么来的。
  // 挂 window 既能拖出元素范围，又完全不改变 click 的派发目标。
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
}

function detachDragListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
}

function onPointerMove(e) {
  if (!drag || e.pointerId !== drag.id) return
  const dx = e.clientX - drag.startX
  const dy = e.clientY - drag.startY
  // 4px 阈值：手抖不算拖动，单纯点击照常触发
  if (!drag.moved && Math.hypot(dx, dy) < 4) return
  drag.moved = true
  // 向下拖 dy>0 → 元素底边下移 → 距视口底边变小，所以是减 dy
  pos.value = clamp(drag.originX + dx, drag.originBottom - dy)
  // 直接拿刚算出的 x，不用等 DOM 更新，拖动中就能实时转身
  updateFacing(pos.value.x)
}

function onPointerUp(e) {
  if (!drag || e.pointerId !== drag.id) return
  if (drag.moved) {
    savePos()
    suppressClick = true
    // 兜底：万一这次没产生 click，别让标志位一直卡着吞掉下一次正常点击
    clearTimeout(unsuppressTimer)
    unsuppressTimer = setTimeout(() => { suppressClick = false }, 250)
  }
  dragging.value = false
  drag = null
  detachDragListeners()
}

// 捕获阶段拦截，能同时挡住子元素（气泡/菜单/角色）的 click
function onClickCapture(e) {
  if (!suppressClick) return
  suppressClick = false
  e.stopPropagation()
  e.preventDefault()
}

function resetPos() {
  pos.value = null
  try { localStorage.removeItem(POS_KEY) } catch (e) { /* ignore */ }
  panelOpen.value = false
  nextTick(() => {
    defaultX = rootRef.value?.getBoundingClientRect().left ?? 20
    updateFacing(defaultX)
  })
}

const pageTip = computed(() => PAGE_TIPS[route.name] || '有不懂的，点我一下就问')
const tips = computed(() => [INTRO, pageTip.value])
const curTip = computed(() => tips.value[idx.value] || INTRO)

function rotate() {
  idx.value = (idx.value + 1) % tips.value.length
}

function startRotate() {
  clearInterval(timer)
  timer = setInterval(() => {
    if (bubbleVisible.value && !panelOpen.value) rotate()
  }, 7000)
}

function dismiss() {
  // 只关当前这一次，不再写 sessionStorage。
  // 原来写了 sessionStorage，同一标签页刷新并不会清掉，气泡会"永久消失"再也回不来；
  // 现在左键点小衣随时能叫出来，没必要再记这个状态。
  bubbleVisible.value = false
}

/* 左键点击反馈 = 挤压回弹（视觉） + 一声短「啵」（听觉）。
   视觉用 Web Animations API，连点可重播，也不跟 pet-body 的浮动动画抢 transform。
   声音用 Web Audio API 现场合成，不需要 WebSocket、不需要后端、也不需要音频文件。 */
let audioCtx = null

function playPokeSound() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    // AudioContext 单例复用：每次点击都 new 会耗尽浏览器允许的上下文数量。
    // 点击本身是用户手势，正好满足浏览器「必须用户交互后才能出声」的自动播放策略。
    if (!audioCtx) audioCtx = new Ctx()
    if (audioCtx.state === 'suspended') audioCtx.resume()

    const t = audioCtx.currentTime
    const tone = (type, from, to, peak, dur, delay) => {
      const osc = audioCtx.createOscillator()
      const gain = audioCtx.createGain()
      osc.type = type
      osc.frequency.setValueAtTime(from, t + delay)
      osc.frequency.exponentialRampToValueAtTime(to, t + delay + dur)
      gain.gain.setValueAtTime(0.0001, t + delay)
      gain.gain.exponentialRampToValueAtTime(peak, t + delay + 0.012)
      gain.gain.exponentialRampToValueAtTime(0.0001, t + delay + dur)
      osc.connect(gain).connect(audioCtx.destination)
      osc.start(t + delay)
      osc.stop(t + delay + dur + 0.02)
    }
    // 主音短促上扬 + 一层很轻的高八度泛音，听起来是「啵」不是「滴」
    tone('sine', 620, 940, 0.085, 0.12, 0)
    tone('sine', 1240, 1560, 0.022, 0.08, 0.02)
  } catch (e) { /* 浏览器不支持或系统禁音时静默跳过，不影响点击功能 */ }
}

function poke() {
  playPokeSound()
  // 减少动态效果只管视觉，声音照常
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  const avatar = avatarRef.value
  if (!avatar) return
  avatar.getAnimations().forEach((a) => a.cancel())
  avatar.animate([
    { transform: 'scale(1, 1)' },
    { transform: 'scale(1.08, 0.9)', offset: 0.3 },   // 先被戳扁
    { transform: 'scale(0.96, 1.06)', offset: 0.62 }, // 再弹起来
    { transform: 'scale(1, 1)' }
  ], { duration: 360, easing: 'cubic-bezier(.34, 1.56, .64, 1)' })
}

// 左键：弹出气泡；气泡已在时再点一下换句话
function onPetClick() {
  poke()
  panelOpen.value = false
  if (!bubbleVisible.value) {
    bubbleVisible.value = true
  } else {
    rotate()
  }
}

// 右键：开 / 关功能菜单，同时别让浏览器自己的右键菜单弹出来
function onContextMenu() {
  panelOpen.value = !panelOpen.value
  bubbleVisible.value = false
}

function go(path) {
  panelOpen.value = false
  router.push(path)
}

onMounted(async () => {
  bubbleVisible.value = true
  startRotate()
  // 预加载闭眼帧，避免第一次眨眼时因为取图慢而整帧丢掉
  new Image().src = xiaoyiBlink
  scheduleBlink()

  // 先让 DOM 按默认位置渲染出来，才能量到宽高做边界收敛
  await nextTick()
  defaultX = rootRef.value?.getBoundingClientRect().left ?? 20
  const saved = loadPos()
  if (saved) pos.value = clamp(saved.x, saved.bottom)
  updateFacing(pos.value?.x ?? defaultX)
  window.addEventListener('resize', onResize)
})

// 面板展开/收起、气泡出现都会改变整体高度，重新夹一次避免底部超屏
watch([panelOpen, bubbleVisible], async () => {
  await nextTick()
  clampIntoView()
})

// 换页面：立刻讲这一页，并从这一条继续轮换
watch(
  () => route.name,
  () => {
    idx.value = 1
    startRotate()
  }
)

onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(blinkTimer)
  clearTimeout(unsuppressTimer)
  window.removeEventListener('resize', onResize)
  detachDragListeners()
  // 切换布局会卸载组件，关掉音频上下文避免反复创建累积
  try {
    audioCtx?.close()
    audioCtx = null
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
/* 左下角常驻。侧栏已改成顶部导航，--vw-aside 在 style.css 里固定为 0px；
   保留这个变量是为了以后若恢复侧栏，只改一处就能让它重新让位。 */
.pet-guide {
  position: fixed;
  left: calc(var(--vw-aside, 0px) + 20px);
  bottom: 18px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  user-select: none;
  cursor: grab;
  touch-action: none;          /* 触屏拖动时不要顺带滚动页面 */
  transition: left .18s ease, bottom .18s ease;
}
/* 跟手拖的时候关掉过渡，否则位置会慢半拍 */
.pet-guide.dragging { cursor: grabbing; transition: none; }
.pet-guide.dragging * { cursor: grabbing !important; }

.pet-bubble {
  position: relative;
  max-width: 214px;
  background: var(--vw-surface);
  border: 1px solid var(--vw-line);
  border-radius: 10px 10px 10px 3px;
  padding: 9px 26px 9px 13px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--vw-ink-2);
  backdrop-filter: blur(var(--vw-glass-blur));
  -webkit-backdrop-filter: blur(var(--vw-glass-blur));
  box-shadow: var(--vw-shadow);
  cursor: pointer;
  transition: box-shadow .2s;
}
.pet-bubble:hover { box-shadow: var(--vw-shadow); border-color: var(--vw-gold); }
.pet-bubble em { font-style: normal; color: var(--vw-gold); font-weight: 600; }
.pet-speech { display: block; }
.pet-x {
  position: absolute;
  top: 3px;
  right: 7px;
  color: var(--vw-ink-mute);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
}
.pet-x:hover { color: var(--vw-gold); }

.pet-panel {
  background: var(--vw-surface);
  border: 1px solid var(--vw-line);
  border-radius: 8px;
  backdrop-filter: blur(var(--vw-glass-blur));
  -webkit-backdrop-filter: blur(var(--vw-glass-blur));
  box-shadow: var(--vw-shadow);
  overflow: hidden;
  width: 208px;
}
.p-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background .2s;
}
.p-item + .p-item { border-top: 1px solid var(--vw-line); }
.p-item:hover { background: var(--vw-surface-2); }
.p-ico {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 3px;
  background: rgba(162, 129, 63, .13);
  color: var(--vw-gold, #a2813f);
  font-size: 11px;
  font-family: var(--vw-serif);
  display: flex;
  align-items: center;
  justify-content: center;
}
.p-text b { display: block; font-size: 13px; color: var(--vw-ink); font-weight: 600; }
.p-text small { display: block; font-size: 11px; color: var(--vw-ink-mute); margin-top: 1px; }
.p-reset {
  padding: 7px 12px;
  border-top: 1px solid var(--vw-line);
  text-align: center;
  font-size: 11px;
  letter-spacing: .06em;
  color: var(--vw-ink-mute);
  cursor: pointer;
  transition: color .2s, background .2s;
}
.p-reset:hover { color: var(--vw-gold); background: var(--vw-surface-2); }

.pet-body {
  position: relative;
  cursor: pointer;
  line-height: 0;
  animation: pet-float 4s ease-in-out infinite;
  transition: transform .24s cubic-bezier(.34, 1.56, .64, 1);
  transform-origin: 50% 96%;
}
/* 单独一层做点击挤压：pet-body 的 transform 被浮动动画占着，
   img 的 transform 被左右翻转占着，互不干扰 */
.pet-avatar {
  display: block;
  transform-origin: 50% 92%;
  will-change: transform;
}
.pet-img {
  position: relative;
  z-index: 1;
  display: block;
  width: auto;
  height: 180px;
  user-select: none;
  pointer-events: none;
  filter: drop-shadow(0 8px 12px rgba(23, 25, 29, .18));
  transition: transform .28s ease;
}
/* 只镜像立绘：父级 .pet-body 上有 pet-float 浮动动画在占用 transform，
   所以翻转必须落在 img 上，另外也避免连气泡/菜单文字一起翻成反字 */
.pet-guide.flipped .pet-img { transform: scaleX(-1); }
/* 落地阴影：让人物有"站"在页面上的感觉 */
.pet-body::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -2px;
  width: 76px;
  height: 10px;
  transform: translateX(-50%);
  background: rgba(23, 25, 29, .13);
  border-radius: 50%;
  filter: blur(4px);
  z-index: 0;
}
.pet-body:hover { transform: translateY(-2px) scale(1.03); }
.pet-body.open { animation: none; transform: scale(1.03); }
@keyframes pet-float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-3px) rotate(-1deg); }
}

.pet-fade-enter-active, .pet-fade-leave-active { transition: opacity .18s, transform .18s; }
.pet-fade-enter-from, .pet-fade-leave-to { opacity: 0; transform: translateY(4px); }

@media (max-width: 768px) {
  .pet-guide { display: none; }
}
</style>
