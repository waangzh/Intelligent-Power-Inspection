<template>
  <el-container :class="['layout-root', `layout-${sidebarMode}`]">
    <el-aside
      v-if="!isFocusMode"
      :width="sidebarWidth"
      :class="['sidebar', 'desktop-sidebar', { 'is-collapsed': isSidebarCollapsed }]"
    >
      <div class="brand">
        <span class="brand-mark">
          <img class="brand-logo" :src="logoUrl" alt="" aria-hidden="true" />
        </span>
        <strong v-show="!isSidebarCollapsed">电力智能巡检</strong>
      </div>
      <el-scrollbar class="menu-scroll">
        <div v-for="group in visibleMenuGroups" :key="group.title" class="menu-group">
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
              :aria-label="item.label"
              @mouseenter="preloadRouteView(item.path)"
              @focusin="preloadRouteView(item.path)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.label }}</template>
            </el-menu-item>
          </el-menu>
        </div>
      </el-scrollbar>
    </el-aside>

    <el-container class="workspace-shell">
      <el-header :class="['topbar', { 'is-focus': isFocusMode }]">
        <div class="topbar-left">
          <el-tooltip v-if="isMobile || !isFocusMode" :content="navigationToggleLabel" placement="bottom" :show-after="300">
            <el-button class="topbar-control" text :aria-label="navigationToggleLabel" @click="toggleNavigation">
              <el-icon :size="20"><component :is="isMobile || isSidebarCollapsed ? 'Menu' : 'Fold'" /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip v-if="supportsFocusMode && !isMobile" :content="isFocusMode ? '退出专注模式' : '进入专注模式'" placement="bottom" :show-after="300">
            <el-button
              :class="['topbar-control', 'focus-trigger', { active: isFocusMode }]"
              text
              :aria-label="isFocusMode ? '退出专注模式' : '进入专注模式'"
              :aria-pressed="isFocusMode"
              @click="toggleFocusMode"
            >
              <el-icon :size="19"><Aim /></el-icon>
            </el-button>
          </el-tooltip>
          <span class="page-title">{{ currentTitle }}</span>
          <el-tag v-if="!isFocusMode" type="info" size="small" effect="plain">LocateAnything · ROS 建图巡检</el-tag>
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

      <el-main :class="['main-content', { 'focus-content': isFocusMode }]">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer v-model="mobileNavVisible" direction="ltr" size="228px" :with-header="false" class="mobile-nav-drawer">
      <aside class="sidebar mobile-sidebar">
        <div class="brand">
          <span class="brand-mark">
            <img class="brand-logo" :src="logoUrl" alt="" aria-hidden="true" />
          </span>
          <strong>电力智能巡检</strong>
        </div>
        <el-scrollbar class="menu-scroll">
          <div v-for="group in visibleMenuGroups" :key="group.title" class="menu-group">
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
                <template #title>{{ item.label }}</template>
              </el-menu-item>
            </el-menu>
          </div>
        </el-scrollbar>
      </aside>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { menuGroups } from '@/config/menu'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { ROLE_LABELS } from '@/types/auth'
import { canAccess } from '@/utils/permission'
import UserAvatar from '@/components/UserAvatar.vue'
import logoUrl from '../../img/logo2.png'

const route = useRoute()
const router = useRouter()
const alarmStore = useAlarmStore()
const notificationStore = useNotificationStore()
const authStore = useAuthStore()
type SidebarMode = 'expanded' | 'collapsed' | 'focus'
type SidebarPresentation = Exclude<SidebarMode, 'focus'>

const sidebarPresentation = ref<SidebarPresentation>('expanded')
const focusModeEnabled = ref(false)
const isMobile = ref(window.innerWidth < 900)
const mobileNavVisible = ref(false)
const preloadedRoutePaths = new Set<string>()

const supportsFocusMode = computed(() => route.meta.supportsFocusMode === true)
const isFocusMode = computed(() => focusModeEnabled.value)
const isSidebarCollapsed = computed(() => sidebarPresentation.value === 'collapsed')
const sidebarMode = computed<SidebarMode>(() => (isFocusMode.value ? 'focus' : sidebarPresentation.value))
const sidebarWidth = computed(() => (isSidebarCollapsed.value ? '68px' : '228px'))
const navigationToggleLabel = computed(() => {
  if (isMobile.value) return '打开导航菜单'
  return isSidebarCollapsed.value ? '展开侧边栏' : '折叠侧边栏'
})

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
  if (isMobile.value) focusModeEnabled.value = false
  if (!isMobile.value) mobileNavVisible.value = false
}

function toggleNavigation() {
  if (isMobile.value) {
    mobileNavVisible.value = true
    return
  }
  sidebarPresentation.value = isSidebarCollapsed.value ? 'expanded' : 'collapsed'
}

function toggleFocusMode() {
  if (!supportsFocusMode.value || isMobile.value) return
  focusModeEnabled.value = !focusModeEnabled.value
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

watch(supportsFocusMode, (supported) => {
  if (!supported) focusModeEnabled.value = false
})

onMounted(() => {
  window.addEventListener('resize', syncViewport)
})
onUnmounted(() => {
  window.removeEventListener('resize', syncViewport)
})

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
  min-width: 0;
  background: var(--pi-bg);
}

.workspace-shell {
  min-width: 0;
}

.sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 0 0 auto;
  border-right: 1px solid rgba(181, 211, 242, 0.12);
  background: linear-gradient(180deg, #142d4d 0%, #1d3d62 100%);
  box-shadow: 6px 0 18px rgba(15, 37, 64, 0.08);
  transition: width 200ms cubic-bezier(0.2, 0, 0, 1);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 64px;
  padding: 0 17px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  border-bottom: 1px solid rgba(225, 238, 251, 0.1);
}

.brand-mark {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  color: #ffd529;
}

.brand-logo {
  display: block;
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.brand strong {
  overflow: hidden;
  text-overflow: clip;
}

.menu-scroll {
  flex: 1;
}

.menu-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.menu-group {
  padding-top: 5px;
}

.menu-group-title {
  padding: 10px 18px 5px;
  font-size: 11px;
  line-height: 18px;
  color: rgba(207, 224, 244, 0.54);
  letter-spacing: 0;
}

.sidebar-menu {
  width: 100%;
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item) {
  min-height: 42px;
  height: 42px;
  margin: 2px 10px;
  padding-left: 13px !important;
  border: 1px solid transparent;
  border-radius: 6px;
  color: rgba(225, 236, 249, 0.78);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0;
  transition: background-color 160ms ease-out, color 160ms ease-out, box-shadow 160ms ease-out;
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  margin-right: 11px;
  font-size: 18px;
  color: rgba(203, 222, 244, 0.9);
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(93, 157, 236, 0.16);
  color: #fff;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  border-color: rgba(122, 180, 255, 0.42);
  background: linear-gradient(90deg, #2e73d8 0%, #357fe9 100%);
  box-shadow: 0 5px 12px rgba(5, 28, 63, 0.22);
  color: #fff;
}

.sidebar-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #fff;
}

.is-collapsed .brand {
  justify-content: center;
  padding-inline: 0;
}

.is-collapsed .menu-group {
  margin: 0 10px;
  padding: 8px 0 4px;
  border-top: 1px solid rgba(225, 238, 251, 0.09);
}

.is-collapsed .menu-group:first-child {
  border-top: 0;
}

.is-collapsed .sidebar-menu :deep(.el-menu-item) {
  width: 44px;
  margin: 2px auto;
  padding: 0 !important;
  justify-content: center;
}

.is-collapsed .sidebar-menu :deep(.el-menu-tooltip__trigger) {
  padding: 0;
  justify-content: center;
}

.is-collapsed .sidebar-menu :deep(.el-menu-item .el-icon) {
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

.topbar-control {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  padding: 0;
  border-radius: 6px;
  color: #315272;
}

.topbar-control:hover,
.focus-trigger.active {
  color: var(--pi-primary);
  background: #edf5ff;
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0;
  color: var(--pi-text);
  white-space: nowrap;
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
  transition: padding 180ms ease-out;
}

.focus-content {
  padding: 10px 12px 14px;
}

.focus-content > :deep(*) {
  width: 100%;
  max-width: none;
  margin-inline: 0;
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
