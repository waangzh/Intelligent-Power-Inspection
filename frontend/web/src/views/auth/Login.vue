<template>
  <article class="auth-card auth-card--login">
    <header class="login-header">
      <p class="login-eyebrow">SECURE ACCESS</p>
      <h2>登录</h2>
      <p class="subtitle">欢迎回来，请使用平台账号继续访问</p>
    </header>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      aria-label="平台登录表单"
      @submit.prevent="handleLogin"
    >
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          :prefix-icon="User"
          autocomplete="username"
          aria-label="用户名"
          placeholder="请输入用户名"
          clearable
        />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          autocomplete="current-password"
          aria-label="密码"
          placeholder="请输入密码"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      <el-form-item class="login-options">
        <div class="form-row">
          <el-checkbox v-model="form.remember">记住我（7 天）</el-checkbox>
          <router-link class="forgot-link" to="/forgot-password">找回密码</router-link>
        </div>
      </el-form-item>
      <el-form-item class="submit-item">
        <el-button native-type="submit" type="primary" class="submit-btn" :loading="loading">
          <el-icon v-if="!loading"><Lightning /></el-icon>
          登录
        </el-button>
      </el-form-item>
    </el-form>

    <div class="auth-footer">
      还没有账号？
      <router-link to="/register">立即注册</router-link>
    </div>

    <el-divider>演示账号</el-divider>
    <div class="demo-accounts" aria-label="演示账号快捷登录">
      <el-tag class="demo-tag" effect="plain" role="button" tabindex="0" @click="fillDemo('admin', 'Admin@123')" @keydown.enter="fillDemo('admin', 'Admin@123')">
        管理员 admin
      </el-tag>
      <el-tag type="success" class="demo-tag" effect="plain" role="button" tabindex="0" @click="fillDemo('dispatcher', 'Disp@123')" @keydown.enter="fillDemo('dispatcher', 'Disp@123')">
        调度员 dispatcher
      </el-tag>
      <el-tag type="warning" class="demo-tag" effect="plain" role="button" tabindex="0" @click="fillDemo('viewer', 'View@123')" @keydown.enter="fillDemo('viewer', 'View@123')">
        观察员 viewer
      </el-tag>
    </div>

    <p class="security-note">仅限授权人员访问，登录行为将被安全审计</p>
  </article>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lightning, Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  remember: true,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为 4～20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
}

function fillDemo(username: string, password: string) {
  form.username = username
  form.password = password
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(form.username, form.password, form.remember)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-eyebrow {
  margin: 0 0 8px;
  color: #3471bb;
  font-family: 'Bahnschrift', 'DIN Alternate', sans-serif;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.forgot-link {
  color: #1768f2;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.forgot-link:hover,
.forgot-link:focus-visible {
  text-decoration: underline;
}

.login-options {
  margin-top: -2px;
  margin-bottom: 20px !important;
}

.submit-item {
  margin-bottom: 19px !important;
}

.security-note {
  position: relative;
  margin: 26px 0 0;
  padding-top: 19px;
  border-top: 1px solid #edf1f6;
  color: #95a2b3;
  text-align: center;
  font-size: 12px;
}
</style>
