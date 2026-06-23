<template>
  <div class="profile-section">
    <h3 class="section-title"><span class="title-bar" />账号安全</h3>

    <el-descriptions :column="1" border class="security-info">
      <el-descriptions-item label="用户名">{{ authStore.user?.username }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ roleLabel }}</el-descriptions-item>
      <el-descriptions-item label="最近更新">
        {{ fmt(authStore.user?.updatedAt || authStore.user?.createdAt) }}
      </el-descriptions-item>
    </el-descriptions>

    <h4 class="sub-title" style="margin-top: 32px">修改密码</h4>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 420px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 8 位，含字母和数字" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <el-button type="primary" :loading="saving" @click="handleSave">修改密码</el-button>
      <el-button @click="resetForm">清空</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { validatePassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'
import { ROLE_LABELS } from '@/types/auth'

const authStore = useAuthStore()
const profileStore = useProfileStore()
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
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
        cb(v !== form.newPassword ? new Error('两次输入的新密码不一致') : undefined)
      },
      trigger: 'blur',
    },
  ],
}

const roleLabel = computed(() => (authStore.user ? ROLE_LABELS[authStore.user.role] : ''))

function resetForm() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !authStore.user) return

  saving.value = true
  try {
    await profileStore.changePassword(authStore.user, { ...form })
    ElMessage.success('密码已修改，请使用新密码登录')
    resetForm()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  } finally {
    saving.value = false
  }
}

function fmt(iso?: string) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-CN')
}
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 28px;
  font-size: 18px;
  font-weight: 600;
  color: #1a2b3c;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 18px;
  background: #1a5fb4;
  border-radius: 2px;
}

.security-info {
  max-width: 520px;
}

.sub-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: #1a2b3c;
}

.form-footer {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
