<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">搭配管理</h2>
      <div class="header-actions">
        <el-button :icon="View" @click="$router.push('/outfits')">浏览态预览</el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push('/workspace/outfits/edit')">新建搭配</el-button>
      </div>
    </div>

    <div v-loading="loading" class="grid-cards">
      <OutfitCard
        v-for="o in list"
        :key="o.id"
        :outfit="o"
        actions
        @click="goEdit"
        @edit="goEdit"
        @delete="onDelete"
      />
      <el-empty v-if="!loading && list.length === 0" description="还没有搭配，新建一套吧">
        <el-button type="primary" :icon="Plus" @click="$router.push('/workspace/outfits/edit')">新建搭配</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OutfitCard from '@/components/OutfitCard.vue'
import { pageOutfit, deleteOutfit } from '@/api/outfit'

const router = useRouter()
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await pageOutfit({ page: 1, size: 60 })
    list.value = res.records
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function goEdit(id) {
  router.push(`/workspace/outfits/edit/${id}`)
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除搭配「${item.name}」吗？`, '删除搭配', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteOutfit(item.id)
    ElMessage.success('已删除')
    list.value = list.value.filter((o) => o.id !== item.id)
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.header-actions { display: flex; align-items: center; gap: 10px; }
.grid-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; min-height: 120px; }
</style>
