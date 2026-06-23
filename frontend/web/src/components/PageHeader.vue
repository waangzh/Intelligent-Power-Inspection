<template>
  <div class="page-header-wrap">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="(item, i) in breadcrumbs" :key="i" :to="item.to">
        {{ item.label }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description" class="desc">{{ description }}</p>
      </div>
      <div class="actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  breadcrumbs?: { label: string; to?: string }[]
}>()
</script>

<style scoped>
.page-header-wrap {
  margin-bottom: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-top: 8px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a2b3c;
}

.desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
}

.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
