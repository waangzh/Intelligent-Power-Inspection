<template>
  <div class="auth-card">
    <h2>登录</h2>
    <p class="subtitle">欢迎回来，请登录您的账号</p>

    <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" clearable />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          prefix-icon="Lock"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      <el-form-item>
        <div class="form-row">
          <el-checkbox v-model="form.remember">记住我（7 天）</el-checkbox>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form-item>
    </el-form>

    <div class="auth-footer">
      还没有账号？
      <router-link to="/register">立即注册</router-link>
    </div>

    <el-divider>演示账号</el-divider>
    <div class="demo-accounts">
      <el-tag class="demo-tag" @click="fillDemo('admin', 'Admin@123')">管理员 admin</el-tag>
      <el-tag type="success" class="demo-tag" @click="fillDemo('dispatcher', 'Disp@123')">
        调度员 dispatcher
      </el-tag>
      <el-tag type="info" class="demo-tag" @click="fillDemo('viewer', 'View@123')">
        观察员 viewer
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
.form-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
