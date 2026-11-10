<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">个人中心</h2>
    </div>

    <el-row :gutter="20">
      <el-col :span="9">
        <el-card shadow="never" class="profile-head">
          <el-avatar :size="84" class="big-avatar">{{ nickname.charAt(0) }}</el-avatar>
          <div class="p-name">{{ form.nickname || form.username }}</div>
          <div class="text-muted">@{{ form.username }}</div>
          <div class="p-tags">
            <el-tag v-for="t in stylePref" :key="t" size="small" effect="plain" type="primary">{{ t }}</el-tag>
          </div>
          <el-button class="mt" type="primary" :icon="Edit" @click="editing = true" v-if="!editing">编辑资料</el-button>
        </el-card>

        <el-card shadow="never" class="mt">
          <template #header><span class="card-title">我的数据</span></template>
          <div class="data-grid">
            <div class="data-cell"><div class="data-num">{{ stats.totalClothing }}</div><div class="text-muted">衣物</div></div>
            <div class="data-cell"><div class="data-num">{{ stats.totalOutfit }}</div><div class="text-muted">搭配</div></div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="15">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">基本资料</span>
              <el-button v-if="editing" link type="primary" @click="editing = false">取消</el-button>
            </div>
          </template>
          <el-form :model="form" label-width="90px" :disabled="!editing" ref="formRef">
            <el-form-item label="用户名"><el-input v-model="form.username" disabled /></el-form-item>
            <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
            <el-form-item label="性别">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio><el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="身高(cm)"><el-input-number v-model="form.height" :min="100" :max="230" /></el-form-item>
            <el-form-item label="体重(kg)"><el-input-number v-model="form.weight" :min="30" :max="150" /></el-form-item>
            <el-form-item label="常穿尺码"><el-input v-model="form.size" style="width: 120px" /></el-form-item>
            <el-form-item label="偏好风格">
              <el-select v-model="stylePref" multiple filterable allow-create default-first-option style="width:100%">
                <el-option v-for="t in styleOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="所在城市"><el-input v-model="form.city" style="width: 160px" /></el-form-item>
            <el-form-item v-if="editing">
              <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="mt">
          <template #header><span class="card-title">账号安全</span></template>
          <div class="sec-row">
            <div>
              <div class="sec-title">登录密码</div>
              <div class="text-muted">使用 BCrypt 加密存储，登录态基于 JWT</div>
            </div>
            <el-button :icon="Key" @click="onChangePwd">修改密码</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Edit, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile } from '@/api/user'
import { getOverview } from '@/api/statistics'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const styleOptions = ['休闲', '通勤', '运动', '复古', '甜美']
const form = reactive({ username: '', nickname: '', gender: 1, height: 170, weight: 60, size: 'L', stylePref: '', city: '' })
const stylePref = ref([])
const editing = ref(false)
const saving = ref(false)
const stats = reactive({ totalClothing: 0, totalOutfit: 0 })

const nickname = ref('')

function syncPref() {
  form.stylePref = stylePref.value.join(',')
}

async function onSave() {
  syncPref()
  saving.value = true
  try {
    await updateProfile({ ...form })
    // 同步到全局状态。侧边栏 Layout.vue 读的是 store 里的 nickname，
    // 只请求后端不更新 store，侧边栏就会一直显示登录时那份旧数据。
    // 用合并而不是直接传 form，是为了保住 id / role / status 这些表单里没有的字段。
    userStore.setUserInfo({ ...userStore.userInfo, ...form })
    ElMessage.success('已保存')
    editing.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function onChangePwd() {
  ElMessage.info('修改密码功能演示中')
}

onMounted(async () => {
  try {
    const res = await getProfile()
    Object.assign(form, res)
    stylePref.value = (res.stylePref || '').split(',').filter(Boolean)
    nickname.value = res.nickname || res.username || ''
    // 进页面时也以服务端为准刷一次 store，顺带修正 localStorage 里可能过期的旧数据
    userStore.setUserInfo(res)
    const ov = await getOverview()
    stats.totalClothing = ov.totalClothing
    stats.totalOutfit = ov.totalOutfit
  } catch (e) {}
})
</script>

<style scoped>
.card-title { font-family: var(--vw-serif); font-weight: 600; letter-spacing: .1em; color: var(--vw-ink); }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.profile-head { border-radius: 3px; text-align: center; padding: 24px 16px; }
.big-avatar { background: linear-gradient(135deg, #24272c, #4a4e55); color: #e6d9bd; font-family: var(--vw-serif); font-size: 32px; font-weight: 600; margin: 0 auto; }
.p-name { font-family: var(--vw-serif); font-size: 19px; font-weight: 600; letter-spacing: .12em; margin-top: 12px; }
.p-tags { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; margin-top: 12px; }
.mt { margin-top: 16px; }
.data-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.data-cell { text-align: center; background: #f5f1e7; border: 1px solid #eae3d3; border-radius: 2px; padding: 16px; }
.data-num { font-family: var(--vw-serif); font-size: 27px; font-weight: 600; color: var(--vw-ink); }
.sec-row { display: flex; align-items: center; justify-content: space-between; }
.sec-title { font-family: var(--vw-serif); font-weight: 600; letter-spacing: .1em; margin-bottom: 4px; }
</style>
