<template>
  <div class="user-page">
    <PageHeader
      title="用户管理"
      description="管理系统用户与角色分配（仅管理员）"
      :breadcrumbs="[{ label: '系统管理' }, { label: '用户管理' }]"
    />

    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索用户名 / 姓名" clearable class="filter-search">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <span class="record-count">共 {{ filteredUsers.length }} 位用户</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-head">
          <span class="table-title">用户列表</span>
        </div>
      </template>
      <el-table :data="filteredUsers" size="small">
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
            <el-tag :type="row.enabled === false ? 'danger' : 'success'" size="small" effect="light">
              {{ row.enabled === false ? '已禁用' : '已启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" class-name="actions-col" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tag v-if="row.id === authStore.user?.id" size="small" type="info">当前用户</el-tag>
              <el-button
                v-else
                plain
                size="small"
                class="action-btn"
                :class="row.enabled === false ? 'action-submit' : 'action-danger'"
                :loading="togglingId === row.id"
                @click="toggleEnabled(row)"
              >
                {{ row.enabled === false ? '启用' : '禁用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-collapse v-model="rolePanel" class="role-panel">
      <el-collapse-item name="roles">
        <template #title>
          <span class="table-title">角色权限说明</span>
        </template>
        <div class="role-grid">
          <article v-for="item in roleTable" :key="item.role" class="role-card">
            <div class="role-card-head">
              <strong>{{ item.role }}</strong>
            </div>
            <p class="role-desc">{{ item.desc }}</p>
            <p class="role-perms">{{ item.perms }}</p>
          </article>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { ROLE_SUMMARIES } from '@/utils/permission'
import type { User, UserRole } from '@/types/auth'

const userStore = useUserStore()
const authStore = useAuthStore()
const togglingId = ref<string | null>(null)
const keyword = ref('')
const rolePanel = ref<string[]>([])

const roleTable = [
  { role: '管理员', desc: ROLE_SUMMARIES.ADMIN.title, perms: ROLE_SUMMARIES.ADMIN.scope },
  { role: '调度员', desc: ROLE_SUMMARIES.DISPATCHER.title, perms: ROLE_SUMMARIES.DISPATCHER.scope },
  { role: '观察员', desc: ROLE_SUMMARIES.VIEWER.title, perms: ROLE_SUMMARIES.VIEWER.scope },
]

const filteredUsers = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return userStore.users
  return userStore.users.filter(
    (user) => user.username.toLowerCase().includes(q) || user.displayName.toLowerCase().includes(q),
  )
})

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

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.filter-search {
  width: min(280px, 100%);
}

.filter-bar .record-count {
  margin-left: auto;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.role-panel {
  margin-top: 14px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  overflow: hidden;
}

.role-panel :deep(.el-collapse-item__header) {
  padding: 0 14px;
  height: 46px;
  background: #fafbfc;
}

.role-panel :deep(.el-collapse-item__content) {
  padding: 14px 16px 16px;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.role-card {
  padding: 12px 14px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  background: #fbfdff;
}

.role-card-head strong {
  font-size: 14px;
  color: var(--pi-text);
}

.role-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--pi-text);
  line-height: 1.5;
}

.role-perms {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--pi-muted);
  line-height: 1.55;
}

@media (max-width: 900px) {
  .role-grid {
    grid-template-columns: 1fr;
  }
}
</style>
