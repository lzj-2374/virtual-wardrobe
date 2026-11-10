<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">我的搭配</h2>
      <div class="header-actions">
        <el-tag effect="plain" class="ro-tag">只读</el-tag>
        <el-button :icon="Setting" @click="$router.push('/workspace/outfits')">去工作台维护</el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索搭配名称"
        clearable
        :prefix-icon="Search"
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="sceneFilter" placeholder="场景" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="s in scenes" :key="s" :label="s" :value="s" />
      </el-select>
      <span class="result-count text-muted">共 {{ total }} 套</span>
    </div>

    <div v-loading="loading" class="grid-cards mt">
      <OutfitCard v-for="o in list" :key="o.id" :outfit="o" @click="openDetail" />
      <el-empty v-if="!loading && list.length === 0" :description="emptyText">
        <el-button type="primary" :icon="MagicStick" @click="$router.push('/ai')">让 AI 帮我搭一套</el-button>
      </el-empty>
    </div>

    <div class="pager" v-if="total > pageSize">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="onPageChange"
      />
    </div>

    <!-- 搭配详情：只读抽屉 -->
    <el-drawer v-model="drawer" :title="detail.name || '搭配详情'" size="440px" direction="rtl">
      <div v-loading="detailLoading" class="detail">
        <div class="detail-cover">
          <!-- 同 OutfitCard：框改成 3:4 并改用 contain，保证整张效果图完整可见 -->
          <el-image v-if="detail.coverUrl" :src="detail.coverUrl" fit="contain" class="cover-img" />
          <div v-else class="cover-placeholder">
            <span>👗</span>
            <small class="text-muted">{{ detail.source === 2 ? 'AI 推荐' : '手动搭配' }}</small>
          </div>
        </div>

        <div class="detail-meta">
          <el-tag size="small" type="success">{{ detail.scene || '未设场景' }}</el-tag>
          <el-tag v-if="detail.source === 2" size="small" effect="plain">AI 生成</el-tag>
          <span class="text-muted">{{ detail.items?.length || 0 }} 件单品</span>
        </div>

        <p v-if="detail.description" class="detail-desc text-muted">{{ detail.description }}</p>

        <div class="detail-sub-title">包含单品</div>
        <div v-if="detail.items && detail.items.length" class="item-list">
          <div v-for="it in detail.items" :key="it.id || it.clothingId" class="item">
            <el-image v-if="it.imageUrl || it.clothing?.imageUrl" :src="it.imageUrl || it.clothing?.imageUrl" fit="contain" class="item-img" />
            <div v-else class="item-img item-img-empty">无图</div>
            <div class="item-text">
              <div class="item-name">{{ it.name || it.clothing?.name }}</div>
              <div class="text-muted item-sub">{{ it.categoryName || it.clothing?.categoryName || '' }}</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="这套搭配还没有单品" :image-size="80" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Setting, MagicStick } from '@element-plus/icons-vue'
import OutfitCard from '@/components/OutfitCard.vue'
import { pageOutfit, getOutfit } from '@/api/outfit'

const router = useRouter()
const scenes = ['日常', '通勤', '约会', '运动', '正式']

const keyword = ref('')
const sceneFilter = ref('')
const list = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref({})

const emptyText = computed(() =>
  keyword.value || sceneFilter.value ? '没有符合条件的搭配' : '还没有搭配，可以让 AI 先搭一套'
)

async function load() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (sceneFilter.value) params.scene = sceneFilter.value
    const res = await pageOutfit(params)
    list.value = res.records
    total.value = res.total
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  currentPage.value = 1
  load()
}
function onPageChange(p) {
  currentPage.value = p
  load()
}

async function openDetail(id) {
  drawer.value = true
  detailLoading.value = true
  detail.value = list.value.find((o) => o.id === id) || {}
  try {
    const res = await getOutfit(id)
    detail.value = res
  } catch (e) {
    // 拦截器已提示，保留列表里的简版数据
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header-actions { display: flex; align-items: center; gap: 10px; }
.ro-tag { color: var(--vw-gold); border-color: rgba(162, 129, 63, .4); letter-spacing: .1em; }
.toolbar { display: flex; align-items: center; gap: 12px; margin: 4px 0; }
.result-count { font-size: 13px; }
.mt { margin-top: 12px; }
.grid-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; min-height: 120px; }
.pager { display: flex; justify-content: flex-start; margin-top: 18px; padding-left: 4px; }

.detail { display: flex; flex-direction: column; gap: 14px; }
/* 3:4 与生图比例一致，配合 contain 保证效果图完整。原来 4:3 + cover 会把头裁掉 */
.detail-cover { width: 100%; aspect-ratio: 3 / 4; max-height: 520px; border-radius: 3px; overflow: hidden; background: var(--vw-img-bg); }
.cover-img { width: 100%; height: 100%; }
.cover-placeholder { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; background: linear-gradient(135deg, #eeeadf, #e2e2dd); font-size: 34px; }
.detail-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.detail-desc { font-size: 13px; line-height: 1.7; margin: 0; }
.detail-sub-title {
  font-family: var(--vw-serif); font-weight: 600; letter-spacing: .12em;
  color: var(--vw-ink); padding-top: 6px; border-top: 1px solid var(--line);
}
.item-list { display: flex; flex-direction: column; gap: 10px; }
.item { display: flex; align-items: center; gap: 12px; }
.item-img { width: 52px; height: 52px; border-radius: 2px; background: var(--vw-img-bg); flex: none; }
.item-img-empty { display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--vw-ink-mute); }
.item-name { font-size: 13px; font-weight: 500; }
.item-sub { font-size: 12px; }
</style>
