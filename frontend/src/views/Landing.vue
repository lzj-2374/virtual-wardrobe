<template>
  <div class="landing" @wheel="onWheel" @touchstart="onTouchStart" @touchend="onTouchEnd">
    <video
      class="landing__video"
      src="/hero-ink.mp4"
      poster="/hero-ink-poster.jpg"
      autoplay
      muted
      loop
      playsinline
      preload="auto"
    ></video>
    <div class="landing__veil"></div>

    <div class="landing__inner">
      <div class="landing__kicker">
        <span class="seal">衣</span>
        <span>VIRTUAL WARDROBE</span>
      </div>
      <h1 class="landing__title">虚拟衣柜</h1>
      <div class="landing__rule"></div>
      <p class="landing__sub">以你的真实衣柜为起点<br />AI 识别 · 智能搭配 · 上身预览</p>
      <button class="landing__btn" @click="enter">进入系统</button>
    </div>

    <div class="landing__hint" @click="enter">
      <span class="landing__hint-line"></span>
      <span>向下探索</span>
      <span class="landing__arrow">⌄</span>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
let entering = false

// 进入系统：切到带侧边栏的应用页
function enter() {
  if (entering) return
  entering = true
  router.push('/ai')
}

// 滚轮 / 下滑也能进入
function onWheel(e) {
  if (e.deltaY > 8) enter()
}

let startY = 0
function onTouchStart(e) {
  startY = e.touches[0].clientY
}
function onTouchEnd(e) {
  const dy = startY - e.changedTouches[0].clientY
  if (dy > 40) enter()
}
</script>

<style scoped>
.landing {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: #0b0c0e;
}
.landing__video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 38%;
  transform: scale(1.04);
}
/* 左侧压深保证文字干净，中间偏右留出人物 */
.landing__veil {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 9, 11, .94) 0%, rgba(8, 9, 11, .84) 26%, rgba(8, 9, 11, .38) 52%, rgba(8, 9, 11, .06) 72%, rgba(8, 9, 11, .35) 100%),
    linear-gradient(180deg, rgba(8, 9, 11, .5) 0%, rgba(8, 9, 11, 0) 38%, rgba(8, 9, 11, .72) 100%);
}
.landing__inner {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  padding: 7vh 24px 0 7vw;
  max-width: 660px;
}
.landing__kicker {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  letter-spacing: .3em;
  color: rgba(238, 231, 214, .6);
  animation: riseIn 1s ease both;
}
.landing__kicker .seal { width: 22px; height: 22px; font-size: 13px; }
.landing__title {
  /* 行楷：贴近视频里的毛笔题字 */
  font-family: 'STXingkai', '华文行楷', 'STKaiti', 'KaiTi', '楷体', serif;
  font-size: clamp(46px, 6.2vw, 86px);
  font-weight: 400;
  letter-spacing: .1em;
  line-height: 1.16;
  margin: 16px 0 0;
  color: #f7f3e8;
  text-shadow: 0 10px 44px rgba(0, 0, 0, .8), 0 0 1px rgba(0, 0, 0, .4);
  animation: riseIn 1.1s .08s ease both;
}
.landing__rule {
  width: 118px;
  height: 1px;
  margin: 24px 0 22px;
  background: linear-gradient(90deg, rgba(201, 173, 114, .9), rgba(201, 173, 114, 0));
  animation: ruleIn 1.2s .3s ease both;
}
.landing__sub {
  font-size: 13.5px;
  line-height: 2.1;
  letter-spacing: .13em;
  color: rgba(238, 231, 214, .72);
  margin: 0;
  animation: riseIn 1.1s .34s ease both;
}
.landing__btn {
  margin-top: 36px;
  padding: 11px 36px;
  font-family: var(--vw-serif);
  font-size: 14px;
  letter-spacing: .28em;
  text-indent: .28em;
  color: #f4f0e4;
  background: rgba(201, 173, 114, .06);
  border: 1px solid rgba(201, 173, 114, .5);
  cursor: pointer;
  transition: all .35s ease;
  animation: riseIn 1.1s .46s ease both;
}
.landing__btn:hover {
  background: rgba(201, 173, 114, .16);
  border-color: rgba(201, 173, 114, .95);
  color: #fff;
}
.landing__hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 28px;
  width: max-content;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  color: rgba(238, 231, 214, .45);
  cursor: pointer;
  font-family: var(--vw-serif);
  font-size: 11px;
  letter-spacing: .34em;
  text-indent: .34em;
  transition: color .3s;
  animation: fadeUp 1.2s .7s ease both;
}
.landing__hint:hover { color: rgba(201, 173, 114, .9); }
.landing__hint-line {
  width: 1px;
  height: 26px;
  background: linear-gradient(180deg, rgba(201, 173, 114, 0), rgba(201, 173, 114, .7));
}
.landing__arrow {
  font-size: 15px;
  line-height: 1;
  animation: arrowBounce 2s ease-in-out infinite;
}

@keyframes riseIn {
  from { opacity: 0; transform: translateY(22px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes ruleIn {
  from { opacity: 0; transform: scaleX(0); transform-origin: left; }
  to { opacity: 1; transform: scaleX(1); }
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes arrowBounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(4px); }
}

@media (max-width: 820px) {
  .landing__inner { align-items: center; text-align: center; padding: 7vh 24px 0; max-width: none; }
  .landing__veil {
    background: linear-gradient(180deg, rgba(8, 9, 11, .8) 0%, rgba(8, 9, 11, .58) 45%, rgba(8, 9, 11, .9) 100%);
  }
}
</style>
