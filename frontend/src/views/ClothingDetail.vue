<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">{{ readOnly ? '衣物详情' : '衣物维护' }}</h2>
      <div class="header-actions">
        <template v-if="readOnly">
          <el-button :icon="ArrowLeft" @click="router.push('/wardrobe')">返回衣柜</el-button>
          <el-tag effect="plain" class="ro-tag">只读</el-tag>
          <el-button :icon="Setting" @click="goWorkspace">去工作台编辑</el-button>
        </template>
        <template v-else>
          <el-button :icon="ArrowLeft" @click="router.push('/workspace/wardrobe')">返回列表</el-button>
          <el-button type="danger" plain :icon="Delete" @click="onDelete">删除</el-button>
          <el-button type="primary" :icon="Edit" @click="editing = !editing">{{ editing ? '预览' : '编辑' }}</el-button>
        </template>
      </div>
    </div>

    <el-row :gutter="24" v-if="clothing.id">
      <el-col :span="8">
        <!-- 只有工作台的编辑态才允许换图；浏览态和预览态保持纯展示 -->
        <el-upload
          v-if="canChangeImage"
          class="img-uploader"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onImageChange"
          accept="image/*"
        >
          <div class="img-wrap">
            <el-image :src="clothing.imageUrl" fit="contain" class="big-img">
              <template #error>
                <div class="img-fallback">{{ categoryName }}</div>
              </template>
            </el-image>
            <div class="img-tip">{{ uploading ? '上传中…' : '点击更换图片' }}</div>
          </div>
        </el-upload>
        <el-image v-else :src="clothing.imageUrl" fit="contain" class="big-img">
          <template #error>
            <div class="img-fallback">{{ categoryName }}</div>
          </template>
        </el-image>
      </el-col>

      <el-col :span="16">
        <!-- 浏览态：只读展示 -->
        <el-descriptions v-if="readOnly" :column="1" border class="ro-desc">
          <el-descriptions-item label="名称">{{ clothing.name }}</el-descriptions-item>
          <el-descriptions-item label="一级品类">{{ categoryName }}</el-descriptions-item>
          <el-descriptions-item label="子品类">{{ clothing.subCategory || '—' }}</el-descriptions-item>
          <el-descriptions-item label="主色">{{ clothing.color || '—' }}</el-descriptions-item>
          <el-descriptions-item label="厚薄">{{ thicknessText }}</el-descriptions-item>
          <el-descriptions-item label="风格">
            <el-tag v-for="t in styleTags" :key="t" size="small" class="tag">{{ t }}</el-tag>
            <span v-if="!styleTags.length" class="text-muted">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="场合">
            <el-tag v-for="t in occasionTags" :key="t" size="small" class="tag" effect="plain">{{ t }}</el-tag>
            <span v-if="!occasionTags.length" class="text-muted">—</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 工作台：可编辑 -->
        <el-form v-else :model="clothing" label-width="90px" :disabled="!editing" ref="formRef">
          <el-form-item label="名称"><el-input v-model="clothing.name" /></el-form-item>
          <el-form-item label="一级品类">
            <el-select v-model="clothing.category" style="width: 100%">
              <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="子品类"><el-input v-model="clothing.subCategory" /></el-form-item>
          <el-form-item label="主色"><el-input v-model="clothing.color" style="width: 160px" /></el-form-item>
          <el-form-item label="厚薄">
            <el-radio-group v-model="clothing.thickness">
              <el-radio :value="1">薄</el-radio><el-radio :value="2">适中</el-radio><el-radio :value="3">厚</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="风格">
            <el-select v-model="styleTags" multiple filterable allow-create default-first-option
                       style="width: 100%" placeholder="可多选/自定义">
              <el-option v-for="t in styleOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="场合">
            <el-select v-model="occasionTags" multiple filterable allow-create default-first-option
                       style="width: 100%" placeholder="可多选/自定义">
              <el-option v-for="t in occasionOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="editing">
            <el-button type="primary" :loading="saving" @click="onSave">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>
    <el-empty v-else description="加载中或衣物不存在" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit, Delete, Setting, ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClothing, updateClothing, deleteClothing } from '@/api/clothing'
import { uploadFile } from '@/api/file'

const route = useRoute()
const router = useRouter()
const categories = [
  { label: '上衣', value: 1 }, { label: '裤子', value: 2 }, { label: '外套', value: 3 }, { label: '套装', value: 4 }, { label: '鞋包配饰', value: 5 }
]
// 和 ClothingAdd.vue 保持同一套候选值，避免两个页面下拉出来的选项不一样
const styleOptions = ['休闲', '通勤', '运动', '复古', '甜美']
const occasionOptions = ['日常', '上班', '约会', '运动', '正式']
const clothing = reactive({ id: '', tags: [] })
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)

// 由路由 meta.readOnly 决定：浏览态 /wardrobe/:id 只读，工作台 /workspace/wardrobe/:id 可改
const readOnly = computed(() => route.meta.readOnly === true)
// 浏览态不能改任何东西，预览态（编辑已保存但还没再点编辑）也不该露出上传入口
const canChangeImage = computed(() => !readOnly.value && editing.value)

const categoryName = computed(() => categories.find((c) => c.value === clothing.category)?.label || '衣物')
const thicknessText = computed(() => ({ 1: '薄', 2: '适中', 3: '厚' }[clothing.thickness] || '—'))
// 这两个不能再用 computed：下拉框要用 v-model 双向绑定，
// computed 是从 clothing.tags 算出来的只读派生值，绑上去改不动
const styleTags = ref([])
const occasionTags = ref([])

function goWorkspace() {
  router.push(`/workspace/wardrobe/${clothing.id}`)
}

/**
 * 换图：选完立刻传到 OSS，把地址写回 clothing.imageUrl。
 * 注意这一步只改了前端对象，数据库要等点「保存修改」才更新，
 * 所以换完必须提醒用户保存，否则 OSS 上多一张没人引用的图。
 */
async function onImageChange(upload) {
  const raw = upload.raw
  if (!raw) return
  uploading.value = true
  try {
    const res = await uploadFile(raw)
    clothing.imageUrl = res.url
    ElMessage.success('图片已替换，记得点「保存修改」')
  } catch (e) {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}


async function load() {
  loading.value = true
  try {
    const res = await getClothing(route.params.id)
    Object.assign(clothing, res)
    if (!clothing.tags) clothing.tags = []
    // 把查回来的标签按类型拆成两组，分别喂给两个下拉框
    styleTags.value = clothing.tags.filter((t) => t.tagType === 1).map((t) => t.tagValue)
    occasionTags.value = clothing.tags.filter((t) => t.tagType === 2).map((t) => t.tagValue)
    // 从工作台列表页点「编辑」进来时直接进入编辑态
    if (!readOnly.value && route.query.edit === '1') editing.value = true
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onSave() {
  saving.value = true
  try {
    // 下拉框双向绑定的是两个独立数组，提交前必须拼回 clothing.tags，
    // 否则发出去的还是页面加载时那份旧标签，等于白改
    clothing.tags = [
      ...styleTags.value.map((v) => ({ tagType: 1, tagValue: v })),
      ...occasionTags.value.map((v) => ({ tagType: 2, tagValue: v }))
    ]
    await updateClothing(clothing.id, { ...clothing })
    ElMessage.success('已保存')
    editing.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  try {
    await ElMessageBox.confirm(
      `确认删除「${clothing.name}」吗？删除后该衣物会从所有搭配中移除。`,
      '删除衣物',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await deleteClothing(clothing.id)
    ElMessage.success('已删除')
    router.push('/workspace/wardrobe')
  } catch (e) {
    // 取消或失败
  }
}

onMounted(load)
</script>

<style scoped>
.header-actions { display: flex; align-items: center; gap: 10px; }
.ro-tag { color: var(--vw-gold); border-color: rgba(162, 129, 63, .4); letter-spacing: .1em; }
.big-img { width: 100%; height: 360px; border-radius: 3px; background: var(--vw-img-bg); }
.img-uploader :deep(.el-upload) { width: 100%; display: block; cursor: pointer; }
.img-wrap { position: relative; width: 100%; height: 360px; border-radius: 3px; overflow: hidden; }
.img-tip { position: absolute; left: 0; right: 0; bottom: 0; height: 32px; line-height: 32px; text-align: center; font-size: 12px; color: #fff; background: rgba(0, 0, 0, .45); }
.img-fallback { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: var(--vw-ink-mute); font-size: 20px; }
.tag { margin: 0 6px 6px 0; }
.ro-desc :deep(.el-descriptions__label) { width: 96px; color: var(--vw-ink-mute); }
</style>
