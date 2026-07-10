<template>
  <div class="page-header-wrap">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="(item, i) in breadcrumbs" :key="i" :to="item.to">
        {{ item.label }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-heading">
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
  margin-bottom: 14px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-top: 7px;
}

.page-heading h2 {
  margin: 0;
  color: var(--pi-text);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}

.desc {
  margin: 5px 0 0;
  color: var(--pi-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

:deep(.el-breadcrumb__inner) {
  color: #8895a8;
  font-size: 12px;
  font-weight: 400;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--pi-text-regular);
}

@media (max-width: 640px) {
  .page-heading {
    flex-direction: column;
  }

  .page-heading h2 {
    font-size: 20px;
  }

  .actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
