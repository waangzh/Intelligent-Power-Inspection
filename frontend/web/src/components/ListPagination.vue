<template>
  <div v-if="total > size" class="list-pagination">
    <el-pagination
      background
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page + 1"
      @current-change="changePage"
    />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ total: number; page: number; size?: number }>(), {
  size: 20,
})
const emit = defineEmits<{ change: [page: number] }>()

function changePage(page: number) {
  emit('change', page - 1)
}
</script>

<style scoped>
.list-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
