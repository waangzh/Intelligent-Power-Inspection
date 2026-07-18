<template>
  <div>
    <PageHeader
      title="用户管理"
      description="管理系统用户与角色分配（仅管理员）"
      :breadcrumbs="[{ label: '系统管理' }, { label: '用户管理' }]"
    />

    <el-card shadow="never">
      <el-table :data="userStore.users" size="small">
        <el-table-column label="头像" width="70">
          <template #default="{ row }: { row: User }">
            <UserAvatar :display-name="row.displayName" :avatar-url="row.avatarUrl" :seed="row.id" :size="36" />
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="displayName" label="姓名" width="120" />
        <el-table-column prop="bio" label="个性签名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="角色" width="160">
          <template #default="{ row }: { row: User }">
            <el-select
              v-model="row.role"
              size="small"
              :disabled="row.id === authStore.user?.id"
              @change="(v: UserRole) => changeRole(row.id, v)"
            >
              <el-option label="管理员" value="ADMIN" />
              <el-option label="调度员" value="DISPATCHER" />
              <el-option label="观察员" value="VIEWER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }: { row: User }">
            <el-tag :type="row.enabled === false ? 'danger' : 'success'" size="small">
              {{ row.enabled === false ? '已禁用' : '已启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.id === authStore.user?.id" size="small" type="info">当前用户</el-tag>
            <el-button
              v-else
              link
              :type="row.enabled === false ? 'success' : 'danger'"
              :loading="togglingId === row.id"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled === false ? '启用' : '禁用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>角色权限说明</template>
      <el-table :data="roleTable" size="small">
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column prop="desc" label="说明" />
        <el-table-column prop="perms" label="权限范围" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onActivated, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { ROLE_SUMMARIES } from '@/utils/permission'
import type { User, UserRole } from '@/types/auth'

const userStore = useUserStore()
const authStore = useAuthStore()
const togglingId = ref<string | null>(null)

const roleTable = [
  { role: '管理员', desc: ROLE_SUMMARIES.ADMIN.title, perms: ROLE_SUMMARIES.ADMIN.scope },
  { role: '调度员', desc: ROLE_SUMMARIES.DISPATCHER.title, perms: ROLE_SUMMARIES.DISPATCHER.scope },
  { role: '观察员', desc: ROLE_SUMMARIES.VIEWER.title, perms: ROLE_SUMMARIES.VIEWER.scope },
]

onMounted(() => userStore.loadUsers())
onActivated(() => userStore.loadUsers())

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

async function changeRole(userId: string, role: UserRole) {
  try {
    await userStore.updateRole(userId, role)
    if (authStore.user?.id === userId) {
      authStore.patchUser({ role })
    }
    ElMessage.success('角色已更新')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  }
}

async function toggleEnabled(user: User) {
  const enabled = user.enabled === false
  const action = enabled ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      enabled
        ? `确定要启用用户“${user.username}”吗？`
        : `确定要禁用用户“${user.username}”吗？该用户现有的登录会话将被撤销。`,
      `${action}用户`,
      { type: enabled ? 'success' : 'warning', confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  togglingId.value = user.id
  try {
    await userStore.updateEnabled(user.id, enabled)
    ElMessage.success(`用户已${action}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : `${action}失败`)
  } finally {
    togglingId.value = null
  }
}
</script>
