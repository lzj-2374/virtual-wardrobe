<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">添加衣物</h2>
    </div>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-upload
          class="uploader"
          drag
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onFileChange"
          accept="image/*"
        >
          <el-image v-if="previewUrl" :src="previewUrl" fit="contain" class="preview" />
          <div v-else class="upload-placeholder">
            <el-icon class="up-icon"><UploadFilled /></el-icon>
            <div>点击或拖拽衣物图片</div>
          </div>
        </el-upload>
        <el-button :icon="MagicStick" :loading="recognizing" :disabled="!file" type="warning" plain class="mt" @click="onRecognize">
          AI 自动识别属性
        </el-button>
        <p class="text-muted tip">识别结果作为建议值，可手动修改。</p>
      </el-col>

      <el-col :span="16">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="如：白色基础款T恤" />
          </el-form-item>
          <el-form-item label="一级品类" prop="category">
            <el-select v-model="form.category" placeholder="选择品类" style="width: 100%">
              <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="子品类">
            <el-input v-model="form.subCategory" placeholder="如：T恤、牛仔裤" />
          </el-form-item>
          <el-form-item label="主色">
            <el-input v-model="form.color" style="width: 160px" />
          </el-form-item>
          <el-form-item label="季节">
            <el-select v-model="form.season" placeholder="可多选" multiple style="width: 100%">
              <el-option v-for="s in seasons" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="厚薄">
            <el-radio-group v-model="form.thickness">
              <el-radio :value="1">薄</el-radio>
              <el-radio :value="2">适中</el-radio>
              <el-radio :value="3">厚</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="风格">
            <el-select v-model="styleTags" multiple filterable allow-create default-first-option style="width: 100%" placeholder="可多选/自定义">
              <el-option v-for="t in styleOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="场合">
            <el-select v-model="occasionTags" multiple filterable allow-create default-first-option style="width: 100%" placeholder="可多选/自定义">
              <el-option v-for="t in occasionOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="onSubmit">保存衣物</el-button>
            <el-button @click="$router.back()">取消</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, UploadFilled, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { createClothing, recognizeClothing } from '@/api/clothing'
import { uploadFile } from '@/api/file'

const router = useRouter()
const categories = [
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
  { label: '冬', value: 8 }
]
const styleOptions = ['休闲', '通勤', '运动', '复古', '甜美']
const occasionOptions = ['日常', '上班', '约会', '运动', '正式']

const formRef = ref()
const file = ref(null)
const previewUrl = ref('')
const imageUrl = ref('')
const recognizing = ref(false)
const saving = ref(false)
const styleTags = ref([])
const occasionTags = ref([])

const form = reactive({ name: '', category: '', subCategory: '', color: '', season: [], thickness: 2 })
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择品类', trigger: 'change' }]
}

function onFileChange(uploadFile) {
  file.value = uploadFile.raw
  // 本地预览用，只活在当前标签页内存里，绝不能存库
  previewUrl.value = URL.createObjectURL(uploadFile.raw)
  // 换图要把上一张的 OSS 地址清掉，否则提交时用的还是旧图
  imageUrl.value = ''
}

// 后端季节字段是位掩码：1春 2夏 4秋 8冬，相加表示多选（5 = 春秋，15 = 四季）。
// 下拉框的选项只有 1/2/4/8，直接把 5 或 15 塞进去是匹配不到选项的，
// 页面就会把裸数字显示出来——之前表单里那个 "15" 就是这么来的。这里拆成单个季节。
function splitSeason(mask) {
  if (!mask) return []
  return seasons.filter((s) => (mask & s.value) === s.value).map((s) => s.value)
}

async function onRecognize() {
  if (!file.value) {
    ElMessage.warning('请先上传图片')
    return
  }
  recognizing.value = true
  try {
    // 识别要用像素，只给文件名是不够的：手机照片叫 IMG_xxx、拖进来的叫一串哈希，
    // 文件名里一个颜色词都没有，后端就只能返回"待补充"。
    // 所以先确保图已经落到 OSS，再把地址一起传过去。提交时直接复用这个地址，不会重复上传。
    if (!imageUrl.value) {
      const up = await uploadFile(file.value)
      imageUrl.value = up.url
    }
    const res = await recognizeClothing({ fileName: file.value.name, imageUrl: imageUrl.value })
    form.name = res.name || form.name
    form.category = res.category || form.category
    form.subCategory = res.subCategory || ''
    form.color = res.color || ''
    form.season = splitSeason(res.season)
    form.thickness = res.thickness || 2
    styleTags.value = (res.tags || []).filter((t) => t.tagType === 1).map((t) => t.tagValue)
    occasionTags.value = (res.tags || []).filter((t) => t.tagType === 2).map((t) => t.tagValue)
    ElMessage.success('识别完成，请核对')
  } catch (e) {
    // 拦截器已提示
  } finally {
    recognizing.value = false
  }
}

async function onSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      // 提交前保证图片已经落到 OSS：没传过就先传一张，等它回来再提交
      if (file.value && !imageUrl.value) {
        const res = await uploadFile(file.value)
        imageUrl.value = res.url
      }
      const payload = {
        ...form,
        season: form.season.reduce((a, b) => a + b, 0),
        imageUrl: imageUrl.value,
        tags: [
          ...styleTags.value.map((v) => ({ tagType: 1, tagValue: v })),
          ...occasionTags.value.map((v) => ({ tagType: 2, tagValue: v }))
        ]
      }
      await createClothing(payload)
      ElMessage.success('添加成功')
      router.push('/workspace/wardrobe')
    } catch (e) {
      // 拦截器已提示
    } finally {
      saving.value = false
    }
  })
}
</script>

<style scoped>
.uploader :deep(.el-upload), .uploader :deep(.el-upload-dragger) { width: 100%; }
.preview { width: 100%; height: 260px; border-radius: 8px; background: var(--vw-img-bg); }
.upload-placeholder { height: 260px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--vw-ink-mute); border: 1px dashed #dcdfe6; border-radius: 8px; }
.up-icon { font-size: 40px; margin-bottom: 8px; }
.mt { margin-top: 12px; }
.tip { font-size: 12px; margin-top: 8px; }
</style>
