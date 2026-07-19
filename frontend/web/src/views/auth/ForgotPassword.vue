<template>
  <div class="auth-card">
    <h2>找回密码</h2>
    <p class="subtitle">输入绑定手机号，短信验证通过后设置新密码</p>

    <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleReset">
      <el-form-item prop="phone">
        <el-input v-model="form.phone" placeholder="手机号" prefix-icon="Phone" maxlength="11" />
      </el-form-item>
      <el-form-item prop="smsCode">
        <div class="sms-row">
          <el-input v-model="form.smsCode" placeholder="短信验证码" prefix-icon="Message" maxlength="8" />
          <el-button
            class="sms-btn"
            :disabled="smsCooldown > 0 || smsSending"
            :loading="smsSending"
            @click="handleSendSms"
          >
            {{ smsCooldown > 0 ? `${smsCooldown}s 后重发` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          placeholder="新密码（至少 8 位，含字母和数字）"
          prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="确认新密码"
          prefix-icon="Lock"
          show-password
          @keyup.enter="handleReset"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleReset">
          重置密码
        </el-button>
      </el-form-item>
    </el-form>

    <div class="auth-footer">
      想起密码了？
      <router-link to="/login">返回登录</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { resetPasswordApi, sendResetPasswordSmsApi, validatePassword } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const smsSending = ref(false)
const smsCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  phone: '',
  smsCode: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        cb(!/^1\d{10}$/.test(String(v || '').trim()) ? new Error('手机号格式不正确') : undefined)
      },
      trigger: 'blur',
    },
  ],
  smsCode: [
    { required: true, message: '请输入短信验证码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        cb(!/^\d{4,8}$/.test(String(v || '').trim()) ? new Error('验证码格式不正确') : undefined)
      },
      trigger: 'blur',
    },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const err = validatePassword(v)
        cb(err ? new Error(err) : undefined)
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        cb(v !== form.newPassword ? new Error('两次输入的密码不一致') : undefined)
      },
      trigger: 'blur',
    },
  ],
}

function startCooldown(seconds: number) {
  smsCooldown.value = Math.max(1, Math.floor(seconds))
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    smsCooldown.value -= 1
    if (smsCooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

async function handleSendSms() {
  const phoneOk = await formRef.value?.validateField('phone').then(() => true).catch(() => false)
  if (!phoneOk) return

  smsSending.value = true
  try {
    const result = await sendResetPasswordSmsApi(form.phone.trim())
    ElMessage.success(result.message || '验证码已发送')
    if (result.debugCode) {
      form.smsCode = result.debugCode
      ElMessage.info(`开发模式验证码：${result.debugCode}`)
    }
    startCooldown(result.resendIntervalSeconds || 30)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '验证码发送失败')
  } finally {
    smsSending.value = false
  }
}

async function handleReset() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await resetPasswordApi({
      phone: form.phone.trim(),
      smsCode: form.smsCode.trim(),
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword,
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    await router.replace('/login')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重置失败')
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<style scoped>
.sms-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.sms-row :deep(.el-input) {
  flex: 1;
}

.sms-btn {
  flex-shrink: 0;
  min-width: 118px;
}
</style>
