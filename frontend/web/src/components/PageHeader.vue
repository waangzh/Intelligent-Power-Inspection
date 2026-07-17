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
  margin-bottom: 12px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-top: 6px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: var(--pi-text);
}

.desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--pi-muted);
}

.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .page-header {
    align-items: flex-start;
    gap: 10px;
  }

  .actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
