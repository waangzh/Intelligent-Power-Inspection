<template>
  <el-container class="layout-root">
    <el-aside
      class="sidebar"
      :class="{ 'is-collapsed': isSidebarCollapsed }"
      :width="isSidebarCollapsed ? '72px' : '220px'"
    >
      <div class="sidebar-brand">
        <span class="sidebar-brand-mark"><el-icon :size="21"><Lightning /></el-icon></span>
        <strong v-show="!isSidebarCollapsed">电力智能巡检</strong>
      </div>
      <el-scrollbar class="sidebar-scroll">
        <template v-for="group in visibleMenuGroups" :key="group.title">
          <div v-if="!isSidebarCollapsed" class="sidebar-group-title">{{ group.title }}</div>
          <div v-else class="sidebar-group-divider" aria-hidden="true" />
          <el-menu
            class="sidebar-menu"
            :collapse="isSidebarCollapsed"
            :collapse-transition="false"
            :default-active="route.path"
            router
          >
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.label }}</template>
            </el-menu-item>
          </el-menu>
        </template>
      </el-scrollbar>
    </el-aside>

    <el-container class="workspace">
      <el-header class="topbar">
      <div class="topbar-left">
        <el-button class="nav-trigger" text aria-label="切换侧边栏" @click="handleNavTrigger">
          <el-icon :size="19"><Menu /></el-icon>
        </el-button>
        <span class="page-title">{{ currentTitle }}</span>
        <el-tag class="product-tag" type="info" size="small" effect="plain">LocateAnything</el-tag>
      </div>

      <button type="button" class="global-search" aria-label="搜索并打开应用导航" @click="navOpen = true">
        <el-icon><Search /></el-icon>
        <span>搜索站点 / 机器人 / 告警</span>
      </button>

      <div class="topbar-right">
        <el-badge :value="notificationUnread" :hidden="notificationUnread === 0">
          <el-button class="topbar-action" text @click="router.push('/notifications')">
            <el-icon><Message /></el-icon>
            <span>消息</span>
          </el-button>
        </el-badge>
        <el-badge :value="alarmStore.unacknowledgedCount" :hidden="alarmStore.unacknowledgedCount === 0">
          <el-button class="topbar-action" text @click="router.push('/alarms')">
            <el-icon><Bell /></el-icon>
            <span>告警</span>
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
              :size="34"
            />
            <span class="user-info">
              <span class="user-name">{{ authStore.user?.displayName ?? '用户' }}</span>
              <span class="user-bio">{{ roleLabel }}</span>
            </span>
            <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
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

    <el-drawer v-model="navOpen" class="app-nav-drawer" direction="ltr" size="280px">
      <template #header>
        <div class="drawer-brand">
          <span class="brand-mark"><el-icon :size="22"><Lightning /></el-icon></span>
          <span>
            <strong>电力智能巡检</strong>
            <small>智能运维工作台</small>
          </span>
        </div>
      </template>
      <el-input v-model="navKeyword" class="nav-search" clearable placeholder="搜索功能页面">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-scrollbar class="menu-scroll">
        <template v-for="group in filteredMenuGroups" :key="group.title">
          <div class="menu-group-title">{{ group.title }}</div>
          <el-menu :default-active="route.path" router @select="navOpen = false">
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu>
        </template>
        <div v-if="filteredMenuGroups.length === 0" class="empty-navigation">未找到匹配页面</div>
      </el-scrollbar>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
const navOpen = ref(false)
const navKeyword = ref('')
const isSidebarCollapsed = ref(false)

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

const filteredMenuGroups = computed(() => {
  const keyword = navKeyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return visibleMenuGroups.value
  return visibleMenuGroups.value
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => `${group.title}${item.label}`.toLocaleLowerCase('zh-CN').includes(keyword)),
    }))
    .filter((group) => group.items.length > 0)
})

const currentTitle = computed(() => (route.meta.title as string) || '电力智能巡检平台')

function handleNavTrigger() {
  if (window.innerWidth <= 900) {
    navOpen.value = true
    return
  }
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

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
  min-width: 0;
}

.sidebar {
  position: relative;
  z-index: 40;
  flex: 0 0 auto;
  height: 100vh;
  overflow: hidden;
  border-right: 1px solid rgba(71, 137, 211, 0.18);
  background:
    radial-gradient(circle at 92% 29%, rgba(26, 119, 222, 0.2) 0, rgba(26, 119, 222, 0.04) 12%, transparent 25%),
    radial-gradient(circle at 18% 76%, rgba(14, 92, 182, 0.14) 0, transparent 22%),
    linear-gradient(180deg, #061f42 0%, #041a38 48%, #02152f 100%);
  box-shadow: 8px 0 28px rgba(3, 22, 48, 0.12);
  transition: width var(--pi-motion);
}

.sidebar::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    radial-gradient(circle at 18% 8%, rgba(117, 190, 255, 0.4) 0 1px, transparent 1.6px),
    radial-gradient(circle at 83% 36%, rgba(80, 157, 238, 0.3) 0 1px, transparent 1.5px),
    radial-gradient(circle at 66% 68%, rgba(87, 176, 255, 0.22) 0 1px, transparent 1.6px),
    linear-gradient(116deg, transparent 0 66%, rgba(59, 138, 222, 0.04) 66.2%, transparent 66.5%);
  content: '';
}

.sidebar-brand,
.sidebar-scroll {
  position: relative;
  z-index: 1;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 62px;
  padding: 0 18px;
  color: #f4f9ff;
  white-space: nowrap;
}

.sidebar-brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
  color: #d8efff;
  border: 1px solid rgba(129, 205, 255, 0.5);
  border-radius: 50%;
  background: linear-gradient(145deg, rgba(65, 172, 255, 0.95), rgba(25, 102, 215, 0.9));
  box-shadow: 0 0 16px rgba(48, 156, 255, 0.35);
}

.sidebar-brand strong {
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
}

.sidebar-scroll {
  height: calc(100vh - 62px);
}

.sidebar-group-title {
  padding: 13px 18px 5px;
  color: #6682a1;
  font-size: 10px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.sidebar-group-divider {
  width: 24px;
  height: 1px;
  margin: 11px auto 5px;
  background: rgba(129, 162, 199, 0.2);
}

.sidebar-menu {
  width: auto;
  padding: 0 10px;
  border-right: 0;
  background: transparent;
}

.sidebar-menu.el-menu--collapse {
  width: auto;
  padding: 0 8px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 34px;
  margin: 2px 0;
  padding: 0 11px !important;
  color: #afc0d4;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  font-size: 12px;
  transition: color var(--pi-motion), border-color var(--pi-motion), background-color var(--pi-motion), box-shadow var(--pi-motion);
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item) {
  justify-content: center;
  padding: 0 !important;
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  width: 18px;
  margin-right: 9px;
  color: #9fb4ca;
  font-size: 15px;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  color: #e8f4ff;
  background: rgba(32, 103, 181, 0.2);
}

.sidebar-menu :deep(.el-menu-item:hover .el-icon) {
  color: #bfe3ff;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  color: #ffffff;
  border-color: #2d7be8;
  background: linear-gradient(90deg, rgba(22, 105, 226, 0.52), rgba(20, 82, 169, 0.2));
  box-shadow: inset 3px 0 0 #44a2ff, 0 0 15px rgba(20, 105, 226, 0.2);
  font-weight: 650;
}

.sidebar-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #ffffff;
  filter: drop-shadow(0 0 5px rgba(90, 181, 255, 0.65));
}

.sidebar.is-collapsed .sidebar-brand {
  justify-content: center;
  padding: 0;
}

.sidebar :deep(.el-scrollbar__bar) {
  opacity: 0;
}

.workspace {
  flex-direction: column;
  min-width: 0;
  height: 100vh;
}

.topbar {
  position: relative;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background: #fff;
  border-bottom: 1px solid var(--pi-border);
  box-shadow: 0 1px 10px rgba(26, 53, 88, 0.035);
  height: 62px;
  padding: 0 18px 0 12px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 300px;
}

.nav-trigger {
  width: 38px;
  height: 38px;
  padding: 0;
  color: var(--pi-text-regular);
}

.nav-trigger:hover {
  color: var(--pi-primary);
  background: var(--pi-primary-soft);
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--pi-text);
  white-space: nowrap;
}

.product-tag {
  height: 24px;
  padding: 0 10px;
  border-color: #d8e1ed;
  color: #65748a;
  background: #f8fafc;
}

.global-search {
  display: flex;
  align-items: center;
  gap: 9px;
  width: min(390px, 28vw);
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--pi-border);
  border-radius: 999px;
  color: var(--pi-text-muted);
  background: #fbfcfe;
  cursor: pointer;
  transition: border-color var(--pi-motion), box-shadow var(--pi-motion), color var(--pi-motion);
}

.global-search:hover {
  border-color: #bdcbe0;
  color: var(--pi-primary);
  box-shadow: 0 4px 14px rgba(31, 58, 96, 0.08);
}

.topbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  min-width: 390px;
}

.topbar-action {
  height: 38px;
  padding: 0 11px;
  color: var(--pi-text-regular);
  border: 1px solid transparent;
}

.topbar-action:hover {
  color: var(--pi-primary);
  border-color: var(--pi-border);
  background: var(--pi-surface-soft);
}

.user-dropdown {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 3px 2px 3px 8px;
  font-size: 13px;
  color: var(--pi-text-regular);
  border-radius: 10px;
  cursor: pointer;
  transition: background-color var(--pi-motion);
}

.user-dropdown:hover {
  background: var(--pi-surface-soft);
}

.user-info {
  display: flex;
  flex-direction: column;
  min-width: 82px;
  max-width: 132px;
}

.user-name {
  font-weight: 650;
  color: var(--pi-text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-bio {
  font-size: 11px;
  color: var(--pi-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-arrow {
  color: #9aa7ba;
}

.main-content {
  min-height: 0;
  padding: 14px 16px 20px;
  overflow: auto;
  background: var(--pi-bg);
}

.drawer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--pi-text);
}

.drawer-brand > span:last-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.drawer-brand strong {
  font-size: 16px;
}

.drawer-brand small {
  color: var(--pi-text-muted);
  font-size: 11px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #fff;
  border-radius: 12px;
  background: linear-gradient(145deg, #12b8aa, #2468f2);
  box-shadow: 0 8px 18px rgba(36, 104, 242, 0.22);
}

.nav-search {
  margin-bottom: 12px;
}

.menu-scroll {
  height: calc(100vh - 178px);
}

.menu-group-title {
  padding: 16px 12px 7px;
  color: var(--pi-text-muted);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.el-menu {
  border-right: none;
}

.el-menu-item {
  height: 44px;
  margin: 3px 0;
  border-radius: 9px;
  color: var(--pi-text-regular);
}

.el-menu-item:hover {
  color: var(--pi-primary);
  background: var(--pi-primary-soft);
}

.el-menu-item.is-active {
  color: var(--pi-primary);
  font-weight: 650;
  background: var(--pi-primary-soft);
  box-shadow: inset 3px 0 0 var(--pi-primary);
}

.empty-navigation {
  padding: 42px 16px;
  color: var(--pi-text-muted);
  text-align: center;
}

:deep(.app-nav-drawer .el-drawer__header) {
  margin-bottom: 10px;
  padding: 20px 20px 12px;
}

:deep(.app-nav-drawer .el-drawer__body) {
  padding: 4px 16px 16px;
}

:deep(.app-nav-drawer.el-drawer) {
  color: #afc0d4;
  border-radius: 0 14px 14px 0;
  background:
    radial-gradient(circle at 90% 32%, rgba(24, 112, 211, 0.18), transparent 26%),
    linear-gradient(180deg, #061f42, #02152f);
}

:deep(.app-nav-drawer .el-drawer__header) {
  color: #f4f9ff;
}

:deep(.app-nav-drawer .drawer-brand) {
  color: #f4f9ff;
}

:deep(.app-nav-drawer .drawer-brand small),
:deep(.app-nav-drawer .menu-group-title) {
  color: #6682a1;
}

:deep(.app-nav-drawer .el-input__wrapper) {
  color: #dcecff;
  background: rgba(2, 18, 40, 0.5);
  box-shadow: 0 0 0 1px rgba(94, 145, 201, 0.35) inset;
}

:deep(.app-nav-drawer .el-input__inner) {
  color: #dcecff;
}

:deep(.app-nav-drawer .el-menu) {
  background: transparent;
}

:deep(.app-nav-drawer .el-menu-item) {
  color: #afc0d4;
  background: transparent;
}

:deep(.app-nav-drawer .el-menu-item:hover) {
  color: #ffffff;
  background: rgba(32, 103, 181, 0.2);
}

:deep(.app-nav-drawer .el-menu-item.is-active) {
  color: #ffffff;
  border: 1px solid #2d7be8;
  background: linear-gradient(90deg, rgba(22, 105, 226, 0.52), rgba(20, 82, 169, 0.2));
  box-shadow: inset 3px 0 0 #44a2ff, 0 0 15px rgba(20, 105, 226, 0.2);
}

@media (max-width: 1180px) {
  .topbar-left {
    min-width: auto;
  }

  .topbar-right {
    min-width: auto;
  }

  .global-search {
    flex: 1;
    max-width: 300px;
  }

  .product-tag,
  .control-action {
    display: none;
  }
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }
}

@media (max-width: 820px) {
  .topbar {
    height: 58px;
    gap: 6px;
    padding-right: 10px;
  }

  .global-search,
  .topbar-action span,
  .topbar-right .el-divider,
  .user-info,
  .dropdown-arrow {
    display: none;
  }

  .topbar-action {
    width: 38px;
    padding: 0;
  }

  .page-title {
    font-size: 16px;
  }

  .user-dropdown {
    min-height: 38px;
    padding-left: 4px;
  }

  .main-content {
    padding: 12px;
  }
}

@media (max-width: 480px) {
  .nav-trigger {
    width: 34px;
  }

  .topbar-left {
    gap: 4px;
  }

  .topbar-right {
    gap: 0;
  }

  .topbar-right > :first-child {
    display: none;
  }
}
</style>
