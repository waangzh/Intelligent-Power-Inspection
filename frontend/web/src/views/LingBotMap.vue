<template>
  <div>
    <PageHeader
      title="LingBot-Map 三维建图"
      description="视频驱动的三维空间感知，支持巡检点位对齐与异常定位"
      :breadcrumbs="[{ label: '资产感知' }, { label: 'LingBot 建图' }]"
    >
      <template #actions>
        <el-button v-if="can('lingbot:manage')" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建建图任务
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>LingBot-Map 能力</template>
          <ul class="feature-list">
            <li>低成本室外变电站三维重建</li>
            <li>巡检点位与视频帧空间对齐</li>
            <li>异常告警三维坐标回溯</li>
            <li>支持增量更新与版本管理</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>三维预览（演示）</template>
          <div style="height: 200px">
            <Map3D :route="demoRoute" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>建图任务列表</template>
      <el-table :data="lingbotStore.jobs" size="small">
        <el-table-column prop="name" label="任务名称" min-width="140" />
        <el-table-column prop="siteName" label="站点" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="10" :status="row.status === 'COMPLETED' ? 'success' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="点云数" width="100">
          <template #default="{ row }">{{ (row.pointCount / 10000).toFixed(1) }}万</template>
        </el-table-column>
        <el-table-column prop="videoCount" label="视频数" width="80" />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="can('lingbot:manage')" label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PROCESSING' || row.status === 'PENDING'" text type="primary" size="small" @click="lingbotStore.simulateProgress(row.id)">
              推进
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建建图任务" width="460px">
      <el-form label-width="80px">
        <el-form-item label="任务名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="站点">
          <el-select v-model="form.siteId" style="width: 100%">
            <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Map3D from '@/components/Map3D.vue'
import PageHeader from '@/components/PageHeader.vue'
import { usePermission } from '@/composables/usePermission'
import { useLingBotStore } from '@/stores/lingbot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import type { LingBotMapStatus } from '@/types'

const lingbotStore = useLingBotStore()
const siteStore = useSiteStore()
const routeStore = useRouteStore()
const { can } = usePermission()

const dialogVisible = ref(false)
const form = reactive({ name: '', siteId: siteStore.sites[0]?.id ?? '' })
const demoRoute = computed(() => routeStore.routes[0] ?? null)

function statusLabel(s: LingBotMapStatus) {
  return { PENDING: '待处理', PROCESSING: '建图中', COMPLETED: '已完成', FAILED: '失败' }[s]
}

function statusType(s: LingBotMapStatus) {
  return { PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] as 'info' | 'warning' | 'success' | 'danger'
}

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function openCreate() {
  form.name = `建图任务 ${new Date().toLocaleDateString('zh-CN')}`
  form.siteId = siteStore.sites[0]?.id ?? ''
  dialogVisible.value = true
}

function create() {
  const site = siteStore.getSiteById(form.siteId)
  if (!form.name || !site) return
  lingbotStore.createJob(site.id, site.name, form.name)
  dialogVisible.value = false
  ElMessage.success('建图任务已创建')
}
</script>

<style scoped>
.feature-list {
  margin: 0;
  padding-left: 18px;
  line-height: 2;
  color: #606266;
  font-size: 14px;
}
</style>
