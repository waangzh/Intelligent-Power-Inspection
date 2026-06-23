<template>
  <div class="profile-layout">
    <PageHeader
      title="个人中心"
      description="管理个人资料、安全与偏好"
      :breadcrumbs="[{ label: '系统管理' }, { label: '个人中心' }]"
    />

    <div class="profile-shell">
      <aside class="profile-sidebar">
        <div class="sidebar-title">个人中心</div>
        <nav class="sidebar-nav">
          <router-link
            v-for="item in profileMenuItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>

      <main class="profile-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { profileMenuItems } from '@/config/profileMenu'

const route = useRoute()

function isActive(path: string) {
  return route.path === path
}
</script>

<style scoped>
.profile-shell {
  display: flex;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e3e5e7;
  min-height: 560px;
  overflow: hidden;
}

.profile-sidebar {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #e3e5e7;
  background: #fafbfc;
}

.sidebar-title {
  padding: 20px 20px 12px;
  font-size: 15px;
  font-weight: 600;
  color: #61666d;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  padding: 0 8px 16px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 16px;
  margin-bottom: 2px;
  border-radius: 6px;
  font-size: 14px;
  color: #61666d;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: #f1f2f3;
  color: #1a2b3c;
}

.nav-item.active {
  background: #1a5fb4;
  color: #fff;
}

.nav-item.active .el-icon {
  color: #fff;
}

.profile-main {
  flex: 1;
  padding: 28px 36px 36px;
  min-width: 0;
}

@media (max-width: 768px) {
  .profile-shell {
    flex-direction: column;
  }

  .profile-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #e3e5e7;
  }

  .sidebar-nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 4px;
  }

  .nav-item {
    padding: 8px 12px;
    font-size: 13px;
  }
}
</style>
