<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">衣物管理</h2>
      <div class="header-actions">
        <el-button v-if="!batchMode" :icon="Select" @click="enterBatch">批量管理</el-button>
        <el-button v-if="!batchMode" type="primary" :icon="Plus" @click="$router.push('/workspace/wardrobe/add')">添加衣物</el-button>
      </div>
    </div>

    <el-tabs v-model="activeCat" @tab-change="onTabChange">
      <el-tab-pane v-for="c in categories" :key="c.value" :label="c.label" :name="c.value" />
    </el-tabs>

    <!-- 批量操作条 -->
    <div v-if="batchMode" class="batch-bar">
      <span class="batch-count">已选 <b>{{ selectedIds.length }}</b> 件</span>
      <el-button size="small" text @click="toggleSelectAll">{{ allSelected ? '取消全选' : '全选本页' }}</el-button>
      <span class="batch-spacer"></span>
      <el-button
        size="small"
        type="danger"
        plain
        :icon="Delete"
        :disabled="selectedIds.length === 0"
        :loading="batchDeleting"
        @click="onBatchDelete"
      >
        删除选中
      </el-button>
      <el-button size="small" @click="exitBatch">退出批量</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索衣物名称" clearable :prefix-icon="Search" style="width: 240px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="seasonFilter" placeholder="季节" clearable style="width: 130px" @change="onSearch">
        <el-option v-for="s in seasons" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <span class="result-count text-muted">共 {{ total }} 件</span>
    </div>

    <div v-loading="loading" class="grid-cards mt" ref="gridRef">
      <ClothingCard
        v-for="c in list"
        :key="c.id"
        :clothing="c"
        :actions="!batchMode"
        :selectable="batchMode"
        :selected="selectedIds.includes(c.id)"
        @click="onCardClick"
        @edit="goEdit"
        @delete="onDelete"
      />
      <el-empty v-if="!loading && list.length === 0" description="暂无衣物，去添加吧" />
    </div>

    <div class="pager" v-if="total > 0">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        :page-sizes="sizeOptions"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search, Delete, Select } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ClothingCard from '@/components/ClothingCard.vue'
import { pageClothing, deleteClothing, deleteClothingBatch } from '@/api/clothing'

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
const total = ref(0)

// ---- 每页条数要跟着网格的列数走 ----
// 网格用的是 auto-fill，一行能排几列由窗口宽度决定，所以每页条数不能写死。
// 之前固定 6 条、而窗口刚好排 5 列，第 6 件就孤零零占一行，翻页后那一页还只有它一件。
// 现在按实际列数算：一页正好放满两行，翻页永远整行整行地翻。
const gridRef = ref(null)
const columnCount = ref(5)
const pageSize = ref(10)

/** 读浏览器实际排出来的列数。computed 后的 grid-template-columns 是一串像素值，数几个就是几列 */
function measureColumns() {
  const el = gridRef.value
  if (!el) return 0
  const tmpl = getComputedStyle(el).gridTemplateColumns
  if (!tmpl || tmpl === 'none') return 0
  return tmpl.trim().split(/\s+/).length
}

// 下拉选项也跟着列数走。否则用户选个 12 条、而窗口排 5 列，第二行又会只剩 2 个
const sizeOptions = computed(() => {
  const c = columnCount.value || 5
  return [c * 2, c * 3, c * 4, c * 6]
})

let gridObserver = null

function syncColumns() {
  const cols = measureColumns()
  if (!cols || cols === columnCount.value) return
  columnCount.value = cols
  // 列数变了（拉窗口/换显示器），原来的条数就不再是整行，按"两行"重新取
  if (pageSize.value !== cols * 2) {
    pageSize.value = cols * 2
    currentPage.value = 1
    clearSelection()
    load()
  }
}

// 批量管理
const batchMode = ref(false)
const selectedIds = ref([])
const batchDeleting = ref(false)
const allSelected = computed(() => list.value.length > 0 && list.value.every((c) => selectedIds.value.includes(c.id)))

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
  clearSelection()
  load()
}
function onSearch() {
  currentPage.value = 1
  clearSelection()
  load()
}
function onPageChange(p) {
  currentPage.value = p
  clearSelection()
  load()
}
function onSizeChange(s) {
  pageSize.value = s
  currentPage.value = 1
  clearSelection()
  load()
}

// 批量模式切换时，选中项跟着当前筛选/分页走，避免"删到看不见的衣服"
function clearSelection() {
  selectedIds.value = []
}
function enterBatch() {
  batchMode.value = true
  clearSelection()
}
function exitBatch() {
  batchMode.value = false
  clearSelection()
}
function toggleSelect(id) {
  const i = selectedIds.value.indexOf(id)
  if (i > -1) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}
function toggleSelectAll() {
  selectedIds.value = allSelected.value ? [] : list.value.map((c) => c.id)
}

function onCardClick(item) {
  if (batchMode.value) toggleSelect(item.id)
  else goDetail(item.id)
}

function goDetail(id) {
  router.push(`/workspace/wardrobe/${id}`)
}

// 列表页直接进编辑态
function goEdit(id) {
  router.push({ path: `/workspace/wardrobe/${id}`, query: { edit: 1 } })
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${item.name}」吗？删除后该衣物会从所有搭配中移除。`,
      '删除衣物',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteClothing(item.id)
    ElMessage.success('已删除')
    // 当前页删空了就回退一页，避免停在空白页
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onBatchDelete() {
  const count = selectedIds.value.length
  if (count === 0) return
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${count} 件衣物吗？删除后这些衣物会从所有搭配中移除，且不可撤销。`,
      '批量删除',
      { type: 'warning', confirmButtonText: `删除 ${count} 件`, cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return // 用户取消
  }
  batchDeleting.value = true
  try {
    await deleteClothingBatch(selectedIds.value)
    ElMessage.success(`已删除 ${count} 件`)
    // 整页都被删空则回退
    if (selectedIds.value.length >= list.value.length && currentPage.value > 1) currentPage.value -= 1
    clearSelection()
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    batchDeleting.value = false
  }
}

onMounted(() => {
  load()
  // 列数要等浏览器把布局算完才知道，所以交给 ResizeObserver：
  // 它 observe 之后会立刻回调一次，正好用来做首次对齐；之后拉窗口也会重新对齐
  if (typeof ResizeObserver !== 'undefined' && gridRef.value) {
    gridObserver = new ResizeObserver(syncColumns)
    gridObserver.observe(gridRef.value)
  }
})

onBeforeUnmount(() => {
  gridObserver?.disconnect()
  gridObserver = null
})
</script>

<style scoped>
.header-actions { display: flex; gap: 10px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin: 4px 0; }
.result-count { font-size: 13px; }
.mt { margin-top: 12px; }
.grid-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; min-height: 120px; }
.pager { display: flex; justify-content: flex-start; margin-top: 18px; padding-left: 4px; }

/* 批量操作条 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 2px 0 12px;
  padding: 9px 14px;
  border: 1px solid rgba(162, 129, 63, .35);
  border-radius: 3px;
  background: rgba(162, 129, 63, .08);
}
.batch-count { font-size: 13px; color: var(--vw-ink-2); }
.batch-count b { font-size: 15px; color: var(--vw-gold); margin: 0 2px; }
.batch-spacer { flex: 1; }
</style>
