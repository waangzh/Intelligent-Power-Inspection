<template>
  <div class="auth-card auth-card--register">
    <h2>注册</h2>
    <p class="subtitle">创建账号，默认角色为观察员</p>

    <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleRegister">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="用户名（4～20 位字母数字下划线）" prefix-icon="User" />
      </el-form-item>
      <el-form-item prop="displayName">
        <el-input v-model="form.displayName" placeholder="姓名" prefix-icon="UserFilled" />
      </el-form-item>
      <el-form-item prop="phone">
        <el-input v-model="form.phone" placeholder="手机号（选填）" prefix-icon="Phone" />
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码（至少 8 位，含字母和数字）" prefix-icon="Lock" show-password />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" show-password />
      </el-form-item>
      <el-form-item prop="agreed">
        <el-checkbox v-model="form.agreed">
          我已阅读并同意 <a href="javascript:;" class="link">服务条款</a>
        </el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
      </el-form-item>
    </el-form>

    <div class="auth-footer">
      已有账号？
      <router-link to="/login">去登录</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { validatePassword, validateUsername } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  displayName: '',
  phone: '',
  password: '',
  confirmPassword: '',
  agreed: false,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const err = validateUsername(v)
        cb(err ? new Error(err) : undefined)
      },
      trigger: 'blur',
    },
  ],
  displayName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const err = validatePassword(v)
        cb(err ? new Error(err) : undefined)
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        cb(v !== form.password ? new Error('两次输入的密码不一致') : undefined)
      },
      trigger: 'blur',
    },
  ],
  agreed: [
    {
      validator: (_r, v, cb) => {
        cb(!v ? new Error('请阅读并同意服务条款') : undefined)
      },
      trigger: 'change',
    },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.register({
      username: form.username,
      displayName: form.displayName,
      phone: form.phone,
      password: form.password,
      confirmPassword: form.confirmPassword,
      agreed: form.agreed,
    })
    ElMessage.success('注册成功，请登录（默认角色：观察员）')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>
