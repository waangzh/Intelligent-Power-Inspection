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
        <el-table-column label="Map ID" min-width="140">
          <template #default="{ row }">{{ row.mapId || '-' }}</template>
        </el-table-column>
        <el-table-column label="帧数" width="90">
          <template #default="{ row }">{{ row.frameCount || '-' }}</template>
        </el-table-column>
        <el-table-column label="点云数" width="100">
          <template #default="{ row }">{{ ((row.pointCount || 0) / 10000).toFixed(1) }}万</template>
        </el-table-column>
        <el-table-column prop="videoCount" label="视频数" width="80" />
        <el-table-column label="产物" min-width="180">
          <template #default="{ row }">
            <div v-if="artifactEntries(row).length" class="artifact-links">
              <el-link v-for="item in artifactEntries(row)" :key="item.label" :href="item.url" target="_blank" type="primary">
                {{ item.label }}
              </el-link>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="can('lingbot:manage')" label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PROCESSING' || row.status === 'PENDING'" text type="primary" size="small" @click="refresh(row.id)">
              刷新状态
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建建图任务" width="620px">
      <el-form label-width="112px">
        <el-form-item label="任务名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="站点">
          <el-select v-model="form.siteId" style="width: 100%">
            <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="建图视频">
          <div class="video-upload-row">
            <input ref="videoInputRef" class="file-input" type="file" accept="video/mp4,video/quicktime,video/x-msvideo,video/x-matroska,video/webm" @change="onVideoChange" />
            <el-button @click="triggerVideoInput">选择视频</el-button>
            <span class="video-name">{{ selectedVideoName || '未选择视频' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="输出规格">
          <el-select v-model="form.outputProfile" style="width: 100%">
            <el-option label="预览产物" value="preview" />
            <el-option label="Viewer Ready" value="viewer-ready" />
            <el-option label="渲染视频" value="rendered-video" />
            <el-option label="预测数据" value="predictions" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="FPS">
              <el-input-number v-model="form.fps" :min="1" :max="120" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Stride">
              <el-input-number v-model="form.stride" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="关键帧间隔">
              <el-input-number v-model="form.keyframeInterval" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="窗口大小">
              <el-input-number v-model="form.windowSize" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="天空遮罩">
          <el-switch v-model="form.maskSky" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建</el-button>
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
import type { LingBotMapJob, LingBotMapOutputProfile, LingBotMapStatus } from '@/types'

const lingbotStore = useLingBotStore()
const siteStore = useSiteStore()
const routeStore = useRouteStore()
const { can } = usePermission()

const dialogVisible = ref(false)
const creating = ref(false)
const videoInputRef = ref<HTMLInputElement | null>(null)
const selectedVideo = ref<File | null>(null)
const form = reactive({
  name: '',
  siteId: siteStore.sites[0]?.id ?? '',
  fps: 10,
  stride: 1,
  keyframeInterval: 5,
  windowSize: 16,
  outputProfile: 'preview' as LingBotMapOutputProfile,
  maskSky: false,
})
const demoRoute = computed(() => routeStore.routes[0] ?? null)
const selectedVideoName = computed(() => selectedVideo.value?.name ?? '')

function statusLabel(s: LingBotMapStatus) {
  return { PENDING: '待处理', PROCESSING: '建图中', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[s]
}

function statusType(s: LingBotMapStatus) {
  return { PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }[s] as 'info' | 'warning' | 'success' | 'danger'
}

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function openCreate() {
  form.name = `建图任务 ${new Date().toLocaleDateString('zh-CN')}`
  form.siteId = siteStore.sites[0]?.id ?? ''
  form.fps = 10
  form.stride = 1
  form.keyframeInterval = 5
  form.windowSize = 16
  form.outputProfile = 'preview'
  form.maskSky = false
  selectedVideo.value = null
  if (videoInputRef.value) videoInputRef.value.value = ''
  dialogVisible.value = true
}

function triggerVideoInput() {
  videoInputRef.value?.click()
}

function onVideoChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedVideo.value = input.files?.[0] ?? null
}

async function create() {
  const site = siteStore.getSiteById(form.siteId)
  if (!form.name || !site) {
    ElMessage.warning('请填写任务名称和站点')
    return
  }
  if (!selectedVideo.value) {
    ElMessage.warning('请先选择建图视频')
    return
  }
  creating.value = true
  try {
    const upload = await lingbotStore.uploadVideo(selectedVideo.value)
    await lingbotStore.createJob({
      siteId: site.id,
      siteName: site.name,
      name: form.name,
      videoUrl: upload.videoUrl,
      fps: form.fps,
      stride: form.stride,
      keyframeInterval: form.keyframeInterval,
      windowSize: form.windowSize,
      outputProfile: form.outputProfile,
      maskSky: form.maskSky,
    })
    dialogVisible.value = false
    ElMessage.success('建图任务已创建')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '建图任务创建失败')
  } finally {
    creating.value = false
  }
}

async function refresh(id: string) {
  try {
    await lingbotStore.refreshJob(id)
    ElMessage.success('任务状态已刷新')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '任务状态刷新失败')
  }
}

function artifactEntries(job: LingBotMapJob) {
  const artifacts = job.artifacts || {}
  return [
    { label: '点云', url: artifacts.pointCloudUrl },
    { label: 'Mesh', url: artifacts.meshUrl },
    { label: '轨迹', url: artifacts.trajectoryUrl },
    { label: '预览', url: artifacts.previewVideoUrl },
  ].filter((item): item is { label: string; url: string } => Boolean(item.url))
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

.artifact-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.video-upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.file-input {
  display: none;
}

.video-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}
</style>
