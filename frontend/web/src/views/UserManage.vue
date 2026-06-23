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
        <el-table-column label="说明">
          <template #default="{ row }">
            <el-tag v-if="row.id === authStore.user?.id" size="small" type="info">当前用户</el-tag>
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
import { onActivated, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import type { User, UserRole } from '@/types/auth'

const userStore = useUserStore()
const authStore = useAuthStore()

const roleTable = [
  { role: '管理员', desc: '系统全权管理', perms: '全部功能 + 用户管理' },
  { role: '调度员', desc: '日常运维调度', perms: '任务下发/控制、站点路线编辑、告警确认' },
  { role: '观察员', desc: '只读浏览', perms: '查看监控、告警、记录，不可操作任务' },
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
</script>
