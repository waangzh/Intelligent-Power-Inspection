<template>
  <el-container class="layout-root">
    <el-aside v-show="!isSidebarCollapsed" width="220px" :class="['sidebar', 'desktop-sidebar']">
      <div class="brand">
        <el-icon :size="22" color="#ffd700"><Lightning /></el-icon>
        <strong v-show="!isSidebarCollapsed">电力智能巡检</strong>
      </div>
      <el-scrollbar class="menu-scroll">
        <template v-for="group in visibleMenuGroups" :key="group.title">
          <div v-show="!isSidebarCollapsed" class="menu-group-title">{{ group.title }}</div>
          <el-menu
            :default-active="route.path"
            router
            :collapse="isSidebarCollapsed"
            :collapse-transition="false"
            class="sidebar-menu"
          >
            <el-menu-item
              v-for="item in group.items"
              :key="item.path"
              :index="item.path"
              @mouseenter="preloadRouteView(item.path)"
              @focusin="preloadRouteView(item.path)"
            >
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
          <el-button class="sidebar-trigger" text :aria-label="isMobile ? '打开导航菜单' : isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" @click="toggleNavigation">
            <el-icon :size="20"><component :is="isSidebarCollapsed ? 'Menu' : 'Fold'" /></el-icon>
          </el-button>
          <span class="page-title">{{ currentTitle }}</span>
          <el-tag type="info" size="small" effect="plain">LocateAnything · ROS 建图巡检</el-tag>
        </div>
        <div class="topbar-right">
          <el-tooltip content="消息中心" placement="bottom">
            <el-badge :value="notificationUnread" :hidden="notificationUnread === 0" class="topbar-badge">
              <el-button
                :class="['topbar-icon-btn', { active: route.path === '/notifications' }]"
                circle
                text
                @click="router.push('/notifications')"
              >
                <el-icon :size="18"><Message /></el-icon>
              </el-button>
            </el-badge>
          </el-tooltip>
          <el-tooltip content="告警中心" placement="bottom">
            <el-badge :value="alarmStore.unacknowledgedCount" :hidden="alarmStore.unacknowledgedCount === 0" class="topbar-badge">
              <el-button
                :class="['topbar-icon-btn', { active: route.path === '/alarms' }]"
                circle
                text
                @click="router.push('/alarms')"
              >
                <el-icon :size="18"><Bell /></el-icon>
              </el-button>
            </el-badge>
          </el-tooltip>
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

    <el-drawer v-model="mobileNavVisible" direction="ltr" size="220px" :with-header="false" class="mobile-nav-drawer">
      <aside class="sidebar mobile-sidebar">
        <div class="brand">
          <el-icon :size="22" color="#ffd700"><Lightning /></el-icon>
          <strong>电力智能巡检</strong>
        </div>
        <el-scrollbar class="menu-scroll">
          <template v-for="group in visibleMenuGroups" :key="group.title">
            <div class="menu-group-title">{{ group.title }}</div>
            <el-menu :default-active="route.path" router class="sidebar-menu" @select="mobileNavVisible = false">
              <el-menu-item
                v-for="item in group.items"
                :key="item.path"
                :index="item.path"
                @mouseenter="preloadRouteView(item.path)"
                @focusin="preloadRouteView(item.path)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </el-menu-item>
            </el-menu>
          </template>
        </el-scrollbar>
      </aside>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
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
const isSidebarCollapsed = ref(false)
const isMobile = ref(window.innerWidth < 900)
const mobileNavVisible = ref(false)
const preloadedRoutePaths = new Set<string>()

const roleLabel = computed(() => (authStore.user ? ROLE_LABELS[authStore.user.role] : ''))

const notificationUnread = computed(() => {
  const uid = authStore.user?.id
  if (!uid) return 0
  return notificationStore.forUser(uid).filter((n) => !n.read).length
})

const visibleMenuGroups = computed(() => {
  const role = authStore.user?.role
  const permissions = authStore.permissions
  return menuGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((item) => canAccess(permissions, role, item)),
    }))
    .filter((g) => g.items.length > 0)
})

const currentTitle = computed(() => (route.meta.title as string) || '电力智能巡检平台')

function syncViewport() {
  isMobile.value = window.innerWidth < 900
  if (!isMobile.value) mobileNavVisible.value = false
}

function toggleNavigation() {
  if (isMobile.value) {
    mobileNavVisible.value = true
    return
  }
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

function preloadRouteView(path: string) {
  if (preloadedRoutePaths.has(path)) return
  const component = router.resolve(path).matched.at(-1)?.components?.default
  if (typeof component !== 'function') return

  preloadedRoutePaths.add(path)
  void (component as () => Promise<unknown>)().catch(() => {
    preloadedRoutePaths.delete(path)
  })
}

onMounted(() => window.addEventListener('resize', syncViewport))
onUnmounted(() => window.removeEventListener('resize', syncViewport))

function handleUserCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
      .then(async () => {
        await authStore.logout()
        await router.push('/login')
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
  background:
    radial-gradient(circle at 20% 8%, rgba(43, 129, 255, 0.24), transparent 26%),
    radial-gradient(circle at 88% 46%, rgba(21, 110, 224, 0.18), transparent 30%),
    linear-gradient(160deg, #071e42 0%, #0a3265 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 210ms ease-out;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  padding: 16px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu-scroll {
  flex: 1;
}

.menu-group-title {
  padding: 16px 16px 7px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item) {
  min-height: 44px;
  height: 44px;
  margin: 3px 10px;
  padding-left: 14px !important;
  border: 1px solid transparent;
  border-radius: 8px;
  color: rgba(220, 235, 255, 0.75);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.1px;
  transition: background-color 180ms ease-out, border-color 180ms ease-out, box-shadow 180ms ease-out;
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  margin-right: 11px;
  font-size: 17px;
  color: rgba(182, 211, 249, 0.86);
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(83, 157, 255, 0.12);
  color: #fff;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(31, 117, 255, 0.38), rgba(45, 135, 255, 0.14));
  border-color: rgba(98, 174, 255, 0.72);
  box-shadow: inset 3px 0 0 #70c4ff, 0 0 15px rgba(46, 137, 255, 0.16);
  color: #fff;
}

.sidebar-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #8dd5ff;
}

.collapsed .brand {
  justify-content: center;
  padding-inline: 0;
}

.collapsed .sidebar-menu :deep(.el-menu-item) {
  margin-inline: 10px;
  padding: 0 !important;
  justify-content: center;
}

.collapsed .sidebar-menu :deep(.el-menu-item .el-icon) {
  margin: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8edf3;
  height: 64px;
  padding: 0 18px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sidebar-trigger {
  width: 38px;
  height: 38px;
  color: #315272;
}

.sidebar-trigger:hover {
  color: var(--pi-primary);
  background: #edf5ff;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.35px;
  color: var(--pi-text);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.topbar-badge :deep(.el-badge__content) {
  border: none;
}

.topbar-icon-btn {
  width: 36px;
  height: 36px;
  color: #6b7c93;
}

.topbar-icon-btn:hover,
.topbar-icon-btn.active {
  color: #1a5fb4;
  background: #eef4fc;
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
  padding: 14px 16px 20px;
  overflow: auto;
  background: var(--pi-bg);
}

.mobile-nav-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.mobile-sidebar {
  height: 100%;
}

@media (max-width: 899px) {
  .desktop-sidebar {
    display: none;
  }

  .topbar {
    padding-inline: 10px;
  }

  .topbar-left > .el-tag,
  .user-info,
  .user-dropdown > .el-tag,
  .topbar-right :deep(.el-divider) {
    display: none;
  }

  .main-content {
    padding: 12px;
  }
}
</style>
