<template>
  <el-container class="layout-root">
    <el-aside width="230px" class="sidebar">
      <div class="brand">
        <el-icon :size="22" color="#ffd700"><Lightning /></el-icon>
        <span>电力智能巡检</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <template v-for="group in visibleMenuGroups" :key="group.title">
          <div class="menu-group-title">{{ group.title }}</div>
          <el-menu
            :default-active="route.path"
            router
            background-color="#0d2137"
            text-color="#b8c5d6"
            active-text-color="#ffffff"
          >
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu>
        </template>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="page-title">{{ currentTitle }}</span>
          <el-tag type="info" size="small" effect="plain">LocateAnything + LingBot-Map</el-tag>
        </div>
        <div class="topbar-right">
          <el-badge :value="notificationUnread" :hidden="notificationUnread === 0">
            <el-button text @click="router.push('/notifications')">
              <el-icon><Message /></el-icon>
              消息
            </el-button>
          </el-badge>
          <el-badge :value="alarmStore.unacknowledgedCount" :hidden="alarmStore.unacknowledgedCount === 0">
            <el-button text @click="router.push('/alarms')">
              <el-icon><Bell /></el-icon>
              告警
            </el-button>
          </el-badge>
          <el-divider direction="vertical" />
          <el-dropdown trigger="click" @command="handleUserCommand">
            <span class="user-dropdown">
              <UserAvatar
                v-if="authStore.user"
                :display-name="authStore.user.displayName"
                :avatar-url="authStore.user.avatarUrl"
                :seed="authStore.user.id"
                :size="32"
              />
              <span class="user-info">
                <span class="user-name">{{ authStore.user?.displayName ?? '用户' }}</span>
                <span v-if="authStore.user?.bio" class="user-bio">{{ authStore.user.bio }}</span>
              </span>
              <el-tag size="small" type="info" effect="plain">{{ roleLabel }}</el-tag>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ authStore.user?.username }}</el-dropdown-item>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { menuGroups } from '@/config/menu'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { ROLE_LABELS } from '@/types/auth'
import { canAccess } from '@/utils/permission'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const alarmStore = useAlarmStore()
const notificationStore = useNotificationStore()
const authStore = useAuthStore()

const roleLabel = computed(() => (authStore.user ? ROLE_LABELS[authStore.user.role] : ''))

const notificationUnread = computed(() => {
  const uid = authStore.user?.id
  if (!uid) return 0
  return notificationStore.forUser(uid).filter((n) => !n.read).length
})

const visibleMenuGroups = computed(() => {
  const role = authStore.user?.role
  return menuGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((item) => canAccess(role, item)),
    }))
    .filter((g) => g.items.length > 0)
})

const currentTitle = computed(() => (route.meta.title as string) || '电力智能巡检平台')

function handleUserCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
      .then(() => {
        authStore.logout()
        router.push('/login')
      })
      .catch(() => {})
  }
}
</script>

<style scoped>
.layout-root {
  height: 100vh;
}

.sidebar {
  background: var(--pi-sidebar);
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu-scroll {
  flex: 1;
}

.menu-group-title {
  padding: 14px 16px 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.el-menu {
  border-right: none;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8edf3;
  height: 56px;
  padding: 0 20px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a2b3c;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-dropdown {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7c93;
  cursor: pointer;
}

.user-info {
  display: flex;
  flex-direction: column;
  max-width: 140px;
}

.user-name {
  font-weight: 500;
  color: #1a2b3c;
  line-height: 1.3;
}

.user-bio {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  padding: 16px 20px;
  overflow: auto;
  background: var(--pi-bg);
}
</style>
