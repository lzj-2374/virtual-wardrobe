<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">我的衣柜</h2>
      <div class="header-actions">
        <el-tag effect="plain" class="ro-tag">只读</el-tag>
        <el-button :icon="Setting" @click="$router.push('/workspace/wardrobe')">去工作台维护</el-button>
      </div>
    </div>

    <el-tabs v-model="activeCat" @tab-change="onTabChange">
      <el-tab-pane v-for="c in categories" :key="c.value" :label="c.label" :name="c.value" />
    </el-tabs>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索衣物名称" clearable :prefix-icon="Search" style="width: 240px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="seasonFilter" placeholder="季节" clearable style="width: 130px" @change="onSearch">
        <el-option v-for="s in seasons" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <span class="result-count text-muted">共 {{ total }} 件</span>
    </div>

    <div v-loading="loading" class="grid-cards mt">
      <ClothingCard v-for="c in list" :key="c.id" :clothing="c" @click="goDetail" />
      <el-empty v-if="!loading && list.length === 0" :description="emptyText">
        <el-button type="primary" :icon="Setting" @click="$router.push('/workspace/wardrobe/add')">去工作台添加</el-button>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Setting } from '@element-plus/icons-vue'
import ClothingCard from '@/components/ClothingCard.vue'
import { pageClothing } from '@/api/clothing'

const router = useRouter()
const categories = [
  { label: '全部', value: '' },
  { label: '上衣', value: 1 },
  { label: '裤子', value: 2 },
  { label: '外套', value: 3 },
  { label: '套装', value: 4 },
  { label: '鞋包配饰', value: 5 }
]
const seasons = [
  { label: '春', value: 1 },
  { label: '夏', value: 2 },
  { label: '秋', value: 4 },
  { label: '冬', value: 8 },
  { label: '四季', value: 15 }
]

const activeCat = ref('')
const keyword = ref('')
const seasonFilter = ref('')
const list = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

const emptyText = computed(() =>
  keyword.value || seasonFilter.value || activeCat.value !== ''
    ? '没有符合条件的衣物'
    : '衣柜还是空的，先录入第一件吧'
)

async function load() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (activeCat.value !== '') params.category = activeCat.value
    if (keyword.value) params.keyword = keyword.value
    if (seasonFilter.value) params.season = seasonFilter.value
    const res = await pageClothing(params)
    list.value = res.records
    total.value = res.total
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  currentPage.value = 1
  load()
}
function onSearch() {
  currentPage.value = 1
  load()
}
function onPageChange(p) {
  currentPage.value = p
  load()
}
function goDetail(item) {
  router.push(`/wardrobe/${item.id}`)
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
</style>
