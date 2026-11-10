<template>
  <el-card shadow="hover" class="outfit-card" body-style="padding:0" @click="$emit('click', outfit.id)">
    <div class="cover">
      <!-- fit 用 contain 而不是 cover：AI 出的效果图是 3:4 竖图，早先框是 4:3 + cover，
           等于把头和腿一起削掉，"我的搭配里图片不完整"就是这么来的。
           现在框和出图同比例，contain 能保证图一定是完整的；
           万一以后有人手动传了张横图当封面，最多是上下留白，不会被切。 -->
      <el-image v-if="outfit.coverUrl" :src="outfit.coverUrl" fit="contain" class="cover-img" />
      <div v-else class="cover-placeholder">
        <span>👗</span>
        <small class="text-muted">{{ sourceText }}</small>
      </div>

      <!-- 悬停详情层，与衣物卡片同一套交互 -->
      <div class="hover-mask">
        <!-- 名称 / 说明原本印在图片下方，和衣物卡片一样收进悬浮层 -->
        <div class="hm-name" :title="outfit.name">{{ outfit.name || '未命名搭配' }}</div>
        <div v-if="outfit.description" class="hm-desc" :title="outfit.description">{{ outfit.description }}</div>
        <div class="hm-tags">
          <span v-for="(t, i) in overlayTags" :key="t + i" class="hm-tag">{{ t }}</span>
        </div>
        <button class="hm-go" type="button" @click.stop="$emit('click', outfit.id)">
          <span>查看搭配</span>
          <el-icon :size="13"><Promotion /></el-icon>
        </button>
        <div class="hm-meta">
          <span><el-icon><Calendar /></el-icon>{{ createdDate || '未记录' }}</span>
          <span><el-icon><Box /></el-icon>{{ itemCount }} 件单品</span>
        </div>
      </div>

      <div v-if="actions" class="acts">
        <button type="button" class="act" title="编辑" @click.stop="$emit('edit', outfit.id)">
          <el-icon><Edit /></el-icon>
        </button>
        <button type="button" class="act act-danger" title="删除" @click.stop="$emit('delete', outfit)">
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
  outfit: { type: Object, required: true },
  // 是否显示悬浮操作按钮
  actions: { type: Boolean, default: false }
})
defineEmits(['click', 'edit', 'delete'])

const sourceText = computed(() => props.outfit.source === 2 ? 'AI 推荐' : '手动搭配')
const itemCount = computed(() => props.outfit.items?.length || 0)
const seasonText = computed(() => {
  const s = props.outfit.season
  if (!s) return ''
  if (s === 15) return '四季'
  const names = []
  if (s & 1) names.push('春')
  if (s & 2) names.push('夏')
  if (s & 4) names.push('秋')
  if (s & 8) names.push('冬')
  return names.join('/')
})
// 只用实体里真实存在的字段，不塞「收藏数 / 下载量」这类没有的功能
const overlayTags = computed(() => {
  const out = []
  const push = (v) => { if (v && !out.includes(v)) out.push(v) }
  push(props.outfit.scene || '未设场景')
  push(sourceText.value)
  push(seasonText.value)
  return out
})
const createdDate = computed(() => {
  const v = props.outfit.createTime
  return v ? String(v).slice(0, 10) : ''
})
</script>

<style scoped>
.outfit-card { cursor: pointer; overflow: hidden; border-radius: 3px; }
/* 3:4 和生图比例一致。原来是 4:3 的横框，竖版效果图进去必然被裁 */
.cover { position: relative; width: 100%; aspect-ratio: 3 / 4; background: var(--vw-img-bg); }
.cover-img { width: 100%; height: 100%; }
.cover-placeholder { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; background: linear-gradient(135deg, var(--vw-surface-2), var(--vw-surface-3)); font-size: 32px; }

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
  padding: 10px 8px 26px;
  background: linear-gradient(180deg, rgba(10, 12, 16, .30) 0%, rgba(10, 12, 16, .55) 55%, rgba(10, 12, 16, .74) 100%);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  opacity: 0;
  pointer-events: none;
  transition: opacity .25s ease;
}
.outfit-card:hover .hover-mask { opacity: 1; pointer-events: auto; }

/* 纯图片卡片：名称与说明只在悬浮层出现，长文本省略、完整值放 title */
.hm-name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #fff;
  font-family: var(--vw-serif);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: .06em;
  text-shadow: 0 1px 6px rgba(0, 0, 0, .6);
}
.hm-desc {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(255, 255, 255, .8);
  font-size: 10.5px;
  line-height: 1.4;
}
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
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, .85);
  background: rgba(12, 14, 18, .8);
  color: #fff;
  font-size: 12px;
  letter-spacing: .06em;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .10), 0 0 14px rgba(255, 255, 255, .18);
  transition: transform .2s, background .2s, box-shadow .2s;
}
.hm-go:hover {
  transform: translateY(-2px);
  background: rgba(0, 0, 0, .9);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .2), 0 0 22px rgba(255, 255, 255, .34);
}
.hm-meta {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 8px;
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  color: rgba(255, 255, 255, .86);
  font-size: 10.5px;
}
.hm-meta span { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }

.acts {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 5;          /* 压在悬停层之上 */
  display: flex;
  gap: 6px;
  opacity: 0;
  transform: translateY(-4px);
  transition: opacity .18s, transform .18s;
}
.outfit-card:hover .acts { opacity: 1; transform: translateY(0); }
.act {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, .28);
  background: rgba(18, 20, 24, .86);
  color: #f2efe7;
  font-size: 13px;
  cursor: pointer;
  transition: background .18s, color .18s, border-color .18s;
}
.act:hover { background: var(--vw-gold); border-color: var(--vw-gold); color: #1b1d21; }
.act-danger:hover { background: var(--vw-seal); border-color: var(--vw-seal); color: #fff; }
</style>
