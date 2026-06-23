<template>
  <div class="profile-section">
    <h3 class="section-title"><span class="title-bar" />我的信息</h3>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="profile-form">
      <el-form-item label="姓名" prop="displayName">
        <el-input v-model="form.displayName" placeholder="显示名称" style="max-width: 360px" />
      </el-form-item>
      <el-form-item label="用户名">
        <span class="readonly-text">{{ authStore.user?.username }}</span>
        <span class="field-hint">用户名不可修改</span>
      </el-form-item>
      <el-form-item label="个性签名" prop="bio">
        <el-input
          v-model="form.bio"
          type="textarea"
          :rows="4"
          maxlength="80"
          show-word-limit
          placeholder="写一句个性签名，将显示在顶部导航栏"
          style="max-width: 480px"
        />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" placeholder="选填" style="max-width: 360px" />
      </el-form-item>
      <el-form-item label="角色">
        <el-tag type="info">{{ roleLabel }}</el-tag>
      </el-form-item>
      <el-form-item label="注册时间">
        <span class="readonly-text">{{ fmt(authStore.user?.createdAt) }}</span>
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      <el-button @click="resetForm">重置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABELS } from '@/types/auth'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  displayName: '',
  phone: '',
  bio: '',
})

const rules: FormRules = {
  displayName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  bio: [{ max: 80, message: '个性签名不能超过 80 字', trigger: 'blur' }],
}

const roleLabel = computed(() => (authStore.user ? ROLE_LABELS[authStore.user.role] : ''))

function loadForm() {
  const u = authStore.user
  if (!u) return
  form.displayName = u.displayName
  form.phone = u.phone ?? ''
  form.bio = u.bio ?? ''
}

function resetForm() {
  loadForm()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !authStore.user) return

  saving.value = true
  try {
    await authStore.updateProfile({
      displayName: form.displayName,
      phone: form.phone,
      bio: form.bio,
    })
    ElMessage.success('资料已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function fmt(iso?: string) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-CN')
}

onMounted(loadForm)
</script>

<style scoped>
.profile-section {
  max-width: 640px;
}

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

.readonly-text {
  color: #606266;
  font-size: 14px;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
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
