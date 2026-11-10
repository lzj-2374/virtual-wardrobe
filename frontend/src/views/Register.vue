<template>
  <div class="auth-wrap">
    <el-card class="auth-card">
      <div class="auth-logo">
        <span class="seal">衣</span>
        <span class="auth-name">虚拟衣柜</span>
      </div>
      <div class="auth-rule"></div>
      <div class="auth-sub">创建你的专属衣柜</div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="用于登录" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" class="auth-btn" :loading="loading" @click="onSubmit">注 册</el-button>
      </el-form>
      <div class="auth-foot">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { register } from '@/api/user'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirm: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (r, v, cb) => (v === form.password ? cb() : cb(new Error('两次密码不一致'))), trigger: 'blur' }
  ]
}

async function onSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await register(form)
      ElMessage.success('注册成功，请登录')
      router.replace('/login')
    } catch (e) {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.auth-wrap {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.auth-card {
  width: 378px;
  border-radius: 2px;
  --el-card-bg-color: var(--vw-surface);
  backdrop-filter: blur(var(--vw-glass-blur));
  border: 1px solid rgba(201, 173, 114, .28);
  box-shadow: var(--vw-shadow);
}
.auth-card :deep(.el-card__body) { padding: 38px 32px 26px; }
.auth-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  /* 与首页题字一致：行楷 */
  font-family: 'STXingkai', '华文行楷', 'STKaiti', 'KaiTi', '楷体', serif;
  font-size: 28px;
  font-weight: 400;
  letter-spacing: .2em;
  text-indent: .2em;
  color: var(--vw-ink);
}
.auth-name { line-height: 1; }
/* 标题下的金色细线 */
.auth-rule {
  width: 128px;
  height: 1px;
  margin: 18px auto 14px;
  background: linear-gradient(90deg, rgba(162, 129, 63, 0), rgba(162, 129, 63, .8), rgba(162, 129, 63, 0));
}
.auth-sub {
  text-align: center;
  color: var(--vw-ink-mute);
  font-size: 12px;
  letter-spacing: .16em;
  margin: 0 0 26px;
}
.auth-btn { width: 100%; margin-top: 4px; letter-spacing: .18em; }
.auth-btn + .auth-btn { margin-left: 0; }
.auth-foot {
  text-align: center;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--vw-line);
  font-size: 13px;
  color: var(--vw-ink-mute);
  letter-spacing: .04em;
}
.auth-foot a { color: var(--vw-gold); }
</style>
