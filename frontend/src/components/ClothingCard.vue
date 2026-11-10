<template>
  <el-card
    shadow="hover"
    class="clothing-card"
    :class="{ 'is-selected': selected, 'is-selectable': selectable }"
    body-style="padding:0"
    @click="$emit('click', clothing)"
  >
    <div class="thumb">
      <el-image v-if="clothing.imageUrl" :src="clothing.imageUrl" fit="contain" class="thumb-img" />
      <div v-else class="thumb-placeholder" :style="{ background: placeholderColor }">
        <span>{{ categoryName }}</span>
      </div>
      <!-- 批量模式：左上角勾选标记 -->
      <span v-if="selectable" class="pick" :class="{ on: selected }">{{ selected ? '✓' : '' }}</span>

      <!-- 悬停详情层：标签胶囊 + 前往按钮 + 底部信息条。
           批量选择时隐藏，否则会挡住点选；编辑/删除按钮靠 z-index 留在这层之上。 -->
      <div v-if="!selectable" class="hover-mask">
        <!-- 名称原本印在图片下方，现在收进悬浮层；颜色圆点也跟着搬进来 -->
        <div class="hm-name" :title="clothing.name">
          <span class="color-dot" :style="{ background: dotColor }"></span>
          <span class="hm-name-text">{{ clothing.name || '未命名' }}</span>
        </div>
        <div class="hm-tags">
          <span v-for="(t, i) in overlayTags" :key="t + i" class="hm-tag">{{ t }}</span>
        </div>
        <button class="hm-go" type="button" @click.stop="$emit('click', clothing)">
          <span>查看详情</span>
          <el-icon :size="13"><Promotion /></el-icon>
        </button>
        <div class="hm-meta">
          <span><el-icon><Calendar /></el-icon>{{ createdDate || '未记录' }}</span>
          <span><el-icon><Box /></el-icon>{{ clothing.status === 0 ? '已归档' : '在库' }}</span>
        </div>
      </div>

      <div v-if="actions && !selectable" class="acts">
        <button type="button" class="act" title="编辑" @click.stop="$emit('edit', clothing.id)">
          <el-icon><Edit /></el-icon>
        </button>
        <button type="button" class="act act-danger" title="删除" @click.stop="$emit('delete', clothing)">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Edit, Delete, Promotion, Calendar, Box } from '@element-plus/icons-vue'

const props = defineProps({
  clothing: { type: Object, required: true },
  // 是否显示悬浮操作按钮（列表页开启，首页最近添加不显示）
  actions: { type: Boolean, default: false },
  // 批量模式：显示勾选圈、隐藏悬浮操作
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})
defineEmits(['click', 'edit', 'delete'])

const CATS = ['', '上衣', '裤子', '外套', '套装', '鞋包配饰']
const categoryName = computed(() => CATS[props.clothing.category] || '其他')
const styleTags = computed(() => (props.clothing.tags || []).filter((t) => t.tagType === 1).map((t) => t.tagValue))

// 悬停层用的信息胶囊。列表接口不会带 tags（tags 是详情接口才补的），
// 所以这里不能只依赖标签：品类 / 子品类 / 颜色 /  season 都得能独立撑起内容。
const seasonText = computed(() => {
  const s = props.clothing.season
  if (!s) return ''
  if (s === 15) return '四季'
  const names = []
  if (s & 1) names.push('春')
  if (s & 2) names.push('夏')
  if (s & 4) names.push('秋')
  if (s & 8) names.push('冬')
  return names.join('/')
})
const overlayTags = computed(() => {
  const c = props.clothing
  const out = []
  // 逐个加、顺手去重：category 是「外套」、subCategory 也可能填「外套」，
  // 不去重就会出现两个一样的胶囊（截图里那个重复的「外套」）
  const push = (v) => {
    if (v && !out.includes(v)) out.push(v)
  }
  push(categoryName.value)
  push(c.subCategory)
  push(c.color)
  push(seasonText.value)
  styleTags.value.slice(0, 2).forEach(push)
  return out.slice(0, 5)
})
const createdDate = computed(() => {
  const v = props.clothing.createTime
  return v ? String(v).slice(0, 10) : ''
})

// 水墨灰阶占位色，避免彩色块破坏整体调子
const palette = ['#e8e2d4', '#dcdcd8', '#e3dfe6', '#dfe5df', '#ece6d3', '#e5dee1']
const placeholderColor = computed(() => palette[props.clothing.category % palette.length])
// 颜色字典。新增衣物页的 color 是 el-input 自由文本，取值不受约束，
// 所以写死字典一定会漏。这里做两级匹配：
//   ① 精确命中（蓝 / 白色 / 白 …）
//   ② 没命中且是「单一颜色」时，按关键字包含匹配（浅蓝 → 蓝、藏青 → 蓝）
// 组合色（灰+蓝、灰加蓝）刻意不参与第 ② 级，保持原来的浅灰兜底：
// 把混色硬标成其中一种，看起来反而像数据错了。
// 长关键字要排在短关键字前面，否则「浅蓝」会先被「蓝」抢走。
const COLOR_MAP = {
  白色: '#fff', 米白: '#f5f0e6', 米: '#e8dcc0', 杏: '#e8d4b0', 白: '#fff',
  黑: '#333', 黑色: '#333',
  灰色: '#9aa0a6', 灰: '#9aa0a6', 银: '#b8bcc0',
  蓝色: '#5b8def', 浅蓝: '#9dc3f7', 深蓝: '#3b5998', 藏青: '#2f4b7c', 蓝: '#5b8def',
  紫色: '#8b5cf6', 紫: '#8b5cf6',
  粉色: '#f7b8cd', 粉: '#f7b8cd', 酒红: '#8b3a3a', 红色: '#d9534f', 红: '#d9534f',
  棕色: '#a9774f', 棕: '#a9774f', 咖: '#8b6b4a',
  绿色: '#7b9b6b', 军绿: '#6b705c', 绿: '#7b9b6b',
  黄: '#e0b64f', 橙: '#e08a3c', 卡其: '#c3b091'
}
const COLOR_KEYS = Object.keys(COLOR_MAP).sort((a, b) => b.length - a.length)
const COMPOSITE_COLOR = /[+＋、,，/&]|加|混|拼/
const dotColor = computed(() => {
  const raw = (props.clothing.color || '').trim()
  if (!raw) return '#ccc'
  if (COLOR_MAP[raw]) return COLOR_MAP[raw]
  if (COMPOSITE_COLOR.test(raw)) return '#ccc'      // 组合色：保持原样
  const hit = COLOR_KEYS.find((k) => raw.includes(k))
  return hit ? COLOR_MAP[hit] : '#ccc'
})
</script>

<style scoped>
.clothing-card { cursor: pointer; overflow: hidden; border-radius: 3px; transition: transform .18s, box-shadow .18s; }
.clothing-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(80, 60, 160, .12); }
/* 与搭配卡片统一成 4:3 横向图，宽度大于高度；
   contain 保证上传的整件衣服都在画面里，不会被裁掉下摆或袖子 */
.thumb { position: relative; width: 100%; aspect-ratio: 4 / 3; background: var(--vw-img-bg); overflow: hidden; }
.thumb-img { width: 100%; height: 100%; background: var(--vw-img-bg); }
.thumb-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: var(--vw-ink-mute); font-weight: 600; }

/* ---------- 悬停详情层 ---------- */
.hover-mask {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 8px 24px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(10, 12, 16, .30) 0%, rgba(10, 12, 16, .55) 55%, rgba(10, 12, 16, .74) 100%);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  opacity: 0;
  pointer-events: none;
  transition: opacity .25s ease;
}
.clothing-card:hover .hover-mask { opacity: 1; pointer-events: auto; }

.hm-tags { display: flex; flex-wrap: wrap; justify-content: center; gap: 5px; max-width: 100%; }
.hm-tag {
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, .5);
  background: rgba(255, 255, 255, .09);
  backdrop-filter: blur(6px);
  color: #fff;
  font-size: 10.5px;
  letter-spacing: .02em;
  white-space: nowrap;
}
.hm-go {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex-shrink: 0;
  max-width: 100%;
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, .85);
  background: rgba(12, 14, 18, .8);
  color: #fff;
  font-size: 12px;
  letter-spacing: .06em;
  white-space: nowrap;      /* 卡片窄时也不允许「查看详情」折成两行 */
  cursor: pointer;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .10), 0 0 14px rgba(255, 255, 255, .18);
  transition: transform .2s, background .2s, box-shadow .2s;
}
.hm-go:hover {
  transform: translateY(-2px);
  background: rgba(0, 0, 0, .9);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .2), 0 0 26px rgba(255, 255, 255, .38);
}
.hm-meta {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 8px;
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  color: rgba(255, 255, 255, .86);
  font-size: 10.5px;
}
.hm-meta span { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }

/* 批量模式 */
.clothing-card.is-selectable { cursor: pointer; }
.clothing-card.is-selectable:hover { transform: none; box-shadow: 0 6px 18px rgba(23, 25, 29, .1); }
.clothing-card.is-selected { box-shadow: inset 0 0 0 2px var(--vw-gold, #a2813f); }
.clothing-card.is-selected .thumb-img,
.clothing-card.is-selected .thumb-placeholder { opacity: .72; }
.pick {
  position: absolute;
  top: 8px;
  z-index: 4;
  left: 8px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid rgba(250, 248, 242, .9);
  background: rgba(23, 25, 29, .35);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  transition: background .18s, border-color .18s;
}
.pick.on { background: var(--vw-gold, #a2813f); border-color: var(--vw-gold, #a2813f); }
.acts {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 5;          /* 压在悬停详情层之上，编辑/删除仍然看得见、点得到 */
  display: flex;
  gap: 6px;
  opacity: 0;
  transform: translateY(-4px);
  transition: opacity .18s, transform .18s;
}
.clothing-card:hover .acts { opacity: 1; transform: translateY(0); }
.act {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 4px;
  /* 深色底 + 浅色图标：原来浅米底配 var(--vw-ink-2)，夜间 ink-2 翻成浅色后
     变成「浅底浅字」，按钮看着像消失了。这里固定成暗色玻璃，两套主题都清楚。 */
  border: 1px solid rgba(255, 255, 255, .28);
  background: rgba(18, 20, 24, .86);
  color: #f2efe7;
  font-size: 13px;
  cursor: pointer;
  transition: background .18s, color .18s, border-color .18s;
}
.act:hover { background: var(--vw-gold); border-color: var(--vw-gold); color: #1b1d21; }
.act-danger:hover { background: var(--vw-seal); border-color: var(--vw-seal); color: #fff; }

/* 卡片现在是纯图片，名称只在悬浮层出现。长名字省略，完整值放在 title 上。 */
.hm-name {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  max-width: 100%;
  color: #fff;
  font-family: var(--vw-serif);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: .04em;
  text-shadow: 0 1px 6px rgba(0, 0, 0, .6);
}
.hm-name-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 白 / 米白这类浅色圆点在暗罩上要靠描边才看得出轮廓 */
.color-dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, .7);
}
</style>
