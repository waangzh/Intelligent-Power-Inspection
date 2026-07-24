<template>
  <section class="monitor-scene" :class="{ compact }" aria-label="三维场景">
    <div v-if="asset" class="scene-badges">
      <span>静态场景模型</span>
      <span :class="{ muted: !poseOverlayAvailable }">
        {{ poseOverlayAvailable ? '机器人位姿实时叠加' : '位姿叠加未校准' }}
      </span>
    </div>

    <PointCloudViewer
      v-if="sceneBlob"
      :blob="sceneBlob"
      :point-count="asset?.pointCount"
      :robot-pose="robotPose"
      :scene-to-reference-transform="asset?.sceneToReferenceTransform"
    />

    <div v-else v-loading="loading" class="scene-empty">
      <el-icon :size="34"><DataAnalysis /></el-icon>
      <template v-if="loadError">
        <strong>三维场景加载失败</strong>
        <span>{{ loadError }}</span>
        <el-button size="small" plain @click="refresh"><el-icon><RefreshRight /></el-icon>重试</el-button>
      </template>
      <template v-else-if="asset && requiresManualLoad">
        <strong>场景原始文件较大</strong>
        <span>当前资产没有降采样预览，自动加载已暂停</span>
        <el-button size="small" type="primary" @click="confirmOriginalLoad">加载原始点云</el-button>
      </template>
      <template v-else-if="!loading">
        <strong>暂无可用三维场景</strong>
        <span>当前站点尚无已审核且文件完整的点云资产</span>
      </template>
    </div>

    <footer v-if="asset" class="scene-meta">
      <span><small>点云更新时间</small>{{ formatTime(asset.reconstructedAt) }}</span>
      <span><small>场景版本</small>{{ asset.id }}</span>
      <span><small>坐标系</small>{{ asset.coordinateSystem }} / Z-Up</span>
      <span><small>坐标关联</small>{{ frameLabel }}</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DataAnalysis, RefreshRight } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import PointCloudViewer from '@/components/PointCloudViewer.vue'
import { resourcesApi } from '@/api/resources'
import type { SceneAsset } from '@/types'

const props = withDefaults(defineProps<{
  active: boolean
  siteId?: string
  robotId?: string
  robotPose?: { x: number; y: number; yaw: number } | null
  compact?: boolean
}>(), {
  siteId: '',
  robotId: '',
  robotPose: null,
  compact: false,
})

const emit = defineEmits<{
  stateChange: [state: { assetId: string; updatedAt: string; coordinateSystem: string; poseOverlayAvailable: boolean } | null]
}>()

const asset = ref<SceneAsset | null>(null)
const sceneBlob = ref<Blob | null>(null)
const loading = ref(false)
const loadError = ref('')
const requiresManualLoad = ref(false)
let requestVersion = 0
let loadedKey = ''

const poseOverlayAvailable = computed(() => Boolean(
  props.robotPose
  && asset.value?.referenceFrame === (props.robotPose ? 'map' : undefined)
  && asset.value?.sceneToReferenceTransform?.length === 16,
))

const frameLabel = computed(() => {
  if (!asset.value?.sceneFrame || !asset.value.referenceFrame) return '未配置'
  return `${asset.value.sceneFrame} → ${asset.value.referenceFrame}`
})

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function message(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function publishState() {
  const current = asset.value
  if (!current) {
    emit('stateChange', null)
    return
  }
  emit('stateChange', {
    assetId: current.id,
    updatedAt: current.reconstructedAt,
    coordinateSystem: current.coordinateSystem,
    poseOverlayAvailable: poseOverlayAvailable.value,
  })
}

async function loadPreview(forceOriginal = false) {
  const current = asset.value
  if (!current) return
  if (!forceOriginal && !current.previewReady && current.fileSize > 200 * 1024 * 1024) {
    requiresManualLoad.value = true
    return
  }
  const version = requestVersion
  try {
    sceneBlob.value = await resourcesApi.getSceneAssetPreview(current.id)
    if (version !== requestVersion) sceneBlob.value = null
  } catch (error) {
    if (version === requestVersion) loadError.value = message(error, '无法读取点云预览')
  }
}

async function loadScene(force = false) {
  if (!props.active || !props.siteId) return
  const key = `${props.siteId}:${props.robotId}`
  if (!force && loadedKey === key && (asset.value || loadError.value)) return
  loadedKey = key
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  sceneBlob.value = null
  requiresManualLoad.value = false
  try {
    const assets = await resourcesApi.listSceneAssets({
      source: 'ROBOT',
      status: 'AVAILABLE',
      siteId: props.siteId,
      assetKind: 'POINT_CLOUD',
    })
    if (version !== requestVersion) return
    const sorted = [...assets]
      .filter(item => item.filesReady)
      .sort((a, b) => new Date(b.reconstructedAt).getTime() - new Date(a.reconstructedAt).getTime())
    asset.value = sorted.find(item => item.sourceRobotId === props.robotId) ?? sorted[0] ?? null
    publishState()
    await loadPreview()
  } catch (error) {
    if (version === requestVersion) {
      asset.value = null
      loadError.value = message(error, '无法查询三维场景资产')
      publishState()
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function confirmOriginalLoad() {
  const current = asset.value
  if (!current) return
  try {
    await ElMessageBox.confirm(
      `原始点云约 ${(current.fileSize / 1024 / 1024).toFixed(0)} MB，加载可能占用较多内存。是否继续？`,
      '加载原始点云',
      { type: 'warning' },
    )
  } catch {
    return
  }
  loading.value = true
  requiresManualLoad.value = false
  await loadPreview(true)
  loading.value = false
}

function refresh() {
  loadedKey = ''
  void loadScene(true)
}

watch(
  () => [props.active, props.siteId, props.robotId],
  () => void loadScene(),
  { immediate: true },
)
watch(poseOverlayAvailable, publishState)
</script>

<style scoped>
.monitor-scene {
  position: relative;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  height: 100%;
  min-height: 420px;
  overflow: hidden;
  color: #d5e7ea;
  background: #07171d;
}

.monitor-scene :deep(.cloud-viewer) { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; height: 100%; border: 0; }
.monitor-scene :deep(.viewer-stage) { height: 100%; min-height: 360px; }

.scene-badges {
  position: absolute;
  z-index: 4;
  top: 72px;
  left: 14px;
  display: flex;
  gap: 7px;
  pointer-events: none;
}

.scene-badges span {
  padding: 5px 8px;
  border: 1px solid rgba(103, 207, 196, .34);
  border-radius: 4px;
  color: #d6f5f0;
  font-size: 10px;
  font-weight: 700;
  background: rgba(7, 23, 29, .82);
}

.scene-badges span.muted { border-color: rgba(255, 194, 71, .34); color: #ffd78b; }

.scene-empty {
  display: flex;
  min-height: 420px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 28px;
  color: #638c93;
  text-align: center;
  background: linear-gradient(135deg, #07171d, #0b242a);
}

.scene-empty strong { color: #c8e0e3; font-size: 14px; }
.scene-empty span { max-width: 360px; font-size: 12px; }
.scene-empty .el-button { margin-top: 4px; }

.scene-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid #1d4148;
  background: #0a2026;
}

.scene-meta span { min-width: 0; padding: 9px 12px; color: #c1d8db; font: 600 11px/1.3 ui-monospace, monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scene-meta span + span { border-left: 1px solid #1d4148; }
.scene-meta small { display: block; margin-bottom: 3px; color: #638c93; font: 10px/1.2 "Segoe UI", sans-serif; }

.monitor-scene.compact .scene-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.monitor-scene.compact .scene-meta span:nth-child(3) { border-left: 0; border-top: 1px solid #1d4148; }
.monitor-scene.compact .scene-meta span:nth-child(4) { border-top: 1px solid #1d4148; }

@media (max-width: 700px) {
  .scene-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .scene-meta span:nth-child(3) { border-left: 0; border-top: 1px solid #1d4148; }
  .scene-meta span:nth-child(4) { border-top: 1px solid #1d4148; }
}
</style>
