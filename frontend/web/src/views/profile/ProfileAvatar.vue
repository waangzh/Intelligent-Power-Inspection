<template>
  <div class="profile-section">
    <h3 class="section-title"><span class="title-bar" />我的头像</h3>

    <div class="avatar-block">
      <div class="avatar-preview">
        <UserAvatar
          :display-name="authStore.user?.displayName || '用户'"
          :avatar-url="previewUrl"
          :seed="authStore.user?.id"
          :size="120"
        />
      </div>

      <div class="avatar-actions">
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          accept="image/jpeg,image/png,image/webp"
          @change="onAvatarChange"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            选择图片
          </el-button>
        </el-upload>
        <el-button @click="resetAvatar">恢复默认头像</el-button>
        <p class="hint">支持 JPG / PNG / WebP，不超过 2MB</p>
      </div>
    </div>

    <el-divider />

    <h4 class="sub-title">顶栏预览</h4>
    <div class="preview-bar">
      <UserAvatar
        :display-name="authStore.user?.displayName || '用户'"
        :avatar-url="previewUrl"
        :seed="authStore.user?.id"
        :size="40"
      />
      <div class="preview-text">
        <div class="preview-name">{{ authStore.user?.displayName }}</div>
        <div class="preview-bio">{{ authStore.user?.bio || '这个人很懒，什么都没写~' }}</div>
      </div>
    </div>

    <div class="form-footer">
      <el-button type="primary" :loading="saving" :disabled="!dirty" @click="handleSave">保存头像</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { generateDefaultAvatar, readFileAsDataUrl, validateAvatarFile } from '@/utils/avatar'

const authStore = useAuthStore()
const previewUrl = ref<string | undefined>()
const saving = ref(false)

const dirty = computed(() => previewUrl.value !== authStore.user?.avatarUrl)

function loadAvatar() {
  previewUrl.value = authStore.user?.avatarUrl
}

function resetAvatar() {
  if (!authStore.user) return
  previewUrl.value = generateDefaultAvatar(authStore.user.displayName, authStore.user.id)
}

async function onAvatarChange(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const err = validateAvatarFile(file)
  if (err) {
    ElMessage.error(err)
    return
  }
  try {
    previewUrl.value = await readFileAsDataUrl(file)
  } catch {
    ElMessage.error('图片读取失败')
  }
}

async function handleSave() {
  if (!authStore.user || previewUrl.value === undefined) return

  saving.value = true
  try {
    await authStore.updateProfile({ avatarUrl: previewUrl.value })
    ElMessage.success('头像已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadAvatar)
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

.avatar-block {
  display: flex;
  align-items: flex-start;
  gap: 40px;
}

.avatar-preview {
  flex-shrink: 0;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding-top: 8px;
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.sub-title {
  margin: 0 0 12px;
  font-size: 14px;
  color: #606266;
}

.preview-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #f5f7fa;
  border-radius: 8px;
  max-width: 400px;
}

.preview-name {
  font-weight: 600;
  color: #1a2b3c;
}

.preview-bio {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.form-footer {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  justify-content: center;
}
</style>
