<template>
  <div class="forbidden">
    <el-result icon="warning" title="403" sub-title="您没有权限访问此页面">
      <template #extra>
        <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.forbidden {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
