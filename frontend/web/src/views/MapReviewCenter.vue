<template>
  <div class="review-center">
    <PageHeader title="建图审核中心" description="统一审核二维栅格地图与三维场景资产；审核结果不会直接修改既有路线或部署。" :breadcrumbs="[{ label: '巡检作业' }, { label: '建图审核中心' }]">
      <template #actions><el-button :loading="loading" @click="refresh"><el-icon><Refresh /></el-icon>刷新资产</el-button></template>
    </PageHeader>
    <section class="filter-bar">
      <label><span>站点</span><el-select v-model="siteFilter" clearable placeholder="全部站点"><el-option v-for="site in siteStore.sites" :key="site.id" :label="site.name" :value="site.id" /></el-select></label>
      <label><span>来源机器人</span><el-select v-model="robotFilter" clearable placeholder="全部机器人"><el-option v-for="robot in robotOptions" :key="robot.id" :label="robot.name" :value="robot.id" /></el-select></label>
      <label><span>资产类型</span><el-segmented v-model="typeFilter" :options="typeOptions" /></label>
      <label class="status-filter"><span>审核状态</span><el-segmented v-model="statusFilter" :options="statusOptions" /></label>
      <div class="status-metrics">
        <button v-for="metric in metrics" :key="metric.value" type="button" :class="{ active: statusFilter === metric.value }" @click="statusFilter = metric.value">
          <el-icon :class="metric.tone"><component :is="metric.icon" /></el-icon><span>{{ metric.label }}<b>{{ metric.count }}</b></span>
        </button>
      </div>
    </section>
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" class="load-alert" />
    <section v-loading="loading" class="review-workbench">
      <aside class="asset-queue">
        <header><div><strong>{{ statusLabel(statusFilter) }}资产列表</strong><small>（{{ filteredItems.length }}）</small></div><el-button text circle title="刷新资产" @click="refresh"><el-icon><Refresh /></el-icon></el-button></header>
        <div v-if="pagedItems.length" class="queue-list">
          <button v-for="item in pagedItems" :key="item.key" type="button" class="asset-row" :class="{ selected: selectedItem?.key === item.key }" @click="selectItem(item)">
            <span class="asset-thumb" :class="item.kind"><el-icon><component :is="item.kind === 'map' ? 'Picture' : 'DataAnalysis'" /></el-icon><small>{{ item.kind === 'map' ? '2D' : '3D' }}</small></span>
            <span class="asset-summary">
              <span class="summary-top"><el-tag size="small">{{ item.kind === 'map' ? '二维地图' : '三维场景' }}</el-tag><em :class="statusClass(item.asset.status)">{{ statusLabel(item.asset.status) }}</em></span>
              <strong :title="item.asset.id">{{ item.asset.id }}</strong>
              <span>站点：{{ siteName(item.asset.siteId) }} <i /> 机器人：{{ robotName(item.asset.sourceRobotId) }}</span>
              <span>上传时间：{{ formatTime(item.asset.createdAt) }}</span>
            </span>
          </button>
        </div>
        <el-empty v-else description="当前筛选条件下暂无资产" :image-size="72" />
        <footer v-if="filteredItems.length"><span>共 {{ filteredItems.length }} 条</span><el-pagination v-model:current-page="currentPage" small background layout="prev, pager, next" :page-size="pageSize" :total="filteredItems.length" /></footer>
      </aside>
      <main class="asset-stage">
        <template v-if="selectedItem">
          <header class="stage-header"><div><strong>{{ selectedItem.asset.id }}</strong><small>{{ selectedItem.kind === 'map' ? 'ROS 二维栅格地图' : 'Z-UP 三维点云场景' }}</small></div><el-segmented v-model="typeFilter" :options="stageTypeOptions" /></header>
          <div v-if="selectedMap" class="map-viewport">
            <div class="viewport-toolbar"><span>视图图层：</span><el-checkbox v-model="occupancyLayerVisible">占据栅格</el-checkbox><el-checkbox v-model="boundaryLayerVisible">地图边界</el-checkbox><el-checkbox v-model="gridLayerVisible">坐标网格</el-checkbox><el-button text circle title="重新加载地图" :loading="mapPreviewLoading" @click="loadMapPreview(selectedMap)"><el-icon><Refresh /></el-icon></el-button></div>
            <div ref="mapCanvasWrapRef" v-loading="mapPreviewLoading" class="map-canvas-wrap" :class="{ 'grid-hidden': !gridLayerVisible }" @wheel="handleMapWheel">
              <canvas v-show="mapPreviewReady && occupancyLayerVisible" ref="previewCanvasRef" :class="{ 'show-boundary': boundaryLayerVisible }" :style="mapCanvasStyle" aria-label="ROS 栅格地图预览" />
              <el-result v-if="mapPreviewError" icon="error" title="地图文件无法预览" :sub-title="mapPreviewError" />
              <div v-else-if="!mapPreviewReady && !mapPreviewLoading" class="viewport-empty">暂无可预览的栅格地图</div>
              <div class="map-scale"><span>分辨率：{{ selectedMap.resolution }} m/px</span><span>坐标系：ROS (map)</span><b>5 m</b></div>
              <div class="map-meta"><span>{{ selectedMap.width }} × {{ selectedMap.height }} px</span><span>origin {{ formatOrigin(selectedMap.origin) }}</span></div>
              <div class="map-view-controls" role="toolbar" aria-label="二维地图显示控制">
                <el-button class="fit-button" :disabled="!mapPreviewReady" @click="fitMapToWindow"><el-icon><Aim /></el-icon>适应窗口</el-button>
                <el-button class="icon-control" :disabled="!mapPreviewReady || mapZoom <= minimumMapZoom" title="缩小地图" aria-label="缩小地图" @click="adjustMapZoom(-mapZoomStep)"><el-icon><Minus /></el-icon></el-button>
                <el-select v-model="mapZoom" class="zoom-select" :disabled="!mapPreviewReady" aria-label="地图显示比例">
                  <el-option v-for="value in mapZoomOptions" :key="value" :label="`${value}%`" :value="value" />
                </el-select>
                <el-button class="icon-control" :disabled="!mapPreviewReady || mapZoom >= maximumMapZoom" title="放大地图" aria-label="放大地图" @click="adjustMapZoom(mapZoomStep)"><el-icon><Plus /></el-icon></el-button>
                <el-button class="icon-control" :disabled="!mapPreviewReady" :title="isMapFullscreen ? '退出全屏' : '全屏显示'" :aria-label="isMapFullscreen ? '退出全屏' : '全屏显示'" @click="toggleMapFullscreen"><el-icon><FullScreen /></el-icon></el-button>
              </div>
            </div>
          </div>
          <div v-else-if="selectedScene" class="scene-viewport">
            <div class="scene-load-bar"><div><strong>点云空间预览</strong><small>{{ selectedScene.previewReady ? '使用降采样预览文件' : '当前预览将读取原始点云文件' }}</small></div><el-button type="primary" :disabled="!selectedScene.filesReady" :loading="scenePreviewLoading" @click="loadScenePreview(selectedScene)">{{ scenePreviewBlob ? '重新加载' : '加载点云预览' }}</el-button></div>
            <PointCloudViewer :blob="scenePreviewBlob" :point-count="selectedScene.pointCount" />
          </div>
        </template>
        <el-empty v-else description="请从左侧选择待审核资产" />
      </main>
      <aside class="review-inspector">
        <template v-if="selectedItem">
          <section class="inspector-section asset-facts"><h3>资产信息</h3>
            <dl v-if="selectedMap">
              <dt>资产类型</dt><dd class="accent">二维地图</dd><dt>SHA-256 摘要</dt><dd><code>Y {{ shortMapHash(selectedMap.yamlSha256) }}</code></dd>
              <dt>来源机器人</dt><dd>{{ robotName(selectedMap.sourceRobotId) }}</dd><dt>PGM 摘要</dt><dd><code>P {{ shortMapHash(selectedMap.pgmSha256) }}</code></dd>
              <dt>所属站点</dt><dd>{{ siteName(selectedMap.siteId) }}</dd><dt>上传人</dt><dd>机器人自动上传</dd>
              <dt>地图文件</dt><dd>YAML：{{ selectedMap.yamlName }}<br>PGM：{{ selectedMap.pgmName }}</dd><dt>上传时间</dt><dd>{{ formatTime(selectedMap.createdAt) }}</dd>
              <dt>分辨率</dt><dd>{{ selectedMap.resolution }} m/px</dd><dt>建图时间</dt><dd>{{ formatTime(selectedMap.capturedAt) }}</dd>
              <dt>地图尺寸</dt><dd>{{ selectedMap.width }} × {{ selectedMap.height }} 像素</dd><dt>坐标系</dt><dd>ROS (map)</dd>
            </dl>
            <dl v-else-if="selectedScene">
              <dt>资产类型</dt><dd class="accent">三维场景</dd><dt>模型摘要</dt><dd><code>{{ shortSceneHash(selectedScene.modelSha256) }}</code></dd>
              <dt>来源机器人</dt><dd>{{ robotName(selectedScene.sourceRobotId) }}</dd><dt>Metadata 摘要</dt><dd><code>{{ shortSceneHash(selectedScene.metadataSha256) }}</code></dd>
              <dt>所属站点</dt><dd>{{ siteName(selectedScene.siteId) }}</dd><dt>上传时间</dt><dd>{{ formatTime(selectedScene.createdAt) }}</dd>
              <dt>模型文件</dt><dd>{{ selectedScene.originalName }}</dd><dt>重建时间</dt><dd>{{ formatTime(selectedScene.reconstructedAt) }}</dd>
              <dt>点数量</dt><dd>{{ formatPoints(selectedScene.pointCount) }}</dd><dt>文件大小</dt><dd>{{ formatSceneFileSize(selectedScene.fileSize) }}</dd>
              <dt>坐标系</dt><dd>{{ selectedScene.coordinateSystem }}</dd><dt>单位</dt><dd>{{ selectedScene.unit }}</dd>
            </dl>
            <div v-if="selectedMap" class="file-actions"><el-button size="small" :disabled="!yamlText" @click="yamlVisible = !yamlVisible"><el-icon><Document /></el-icon>{{ yamlVisible ? '收起 YAML' : '查看 YAML' }}</el-button><el-button size="small" :disabled="selectedMap.filesReady === false" @click="downloadMapYaml(selectedMap)">YAML</el-button><el-button size="small" :disabled="selectedMap.filesReady === false" @click="downloadMapPgm(selectedMap)">PGM</el-button></div>
            <div v-else-if="selectedScene" class="file-actions"><el-button size="small" :disabled="!selectedScene.filesReady" @click="downloadSceneMetadata(selectedScene)">Metadata</el-button><el-button size="small" :disabled="!selectedScene.filesReady" @click="downloadSceneModel(selectedScene)">原始模型</el-button></div>
            <pre v-if="selectedMap && yamlVisible && yamlText" class="yaml-source">{{ yamlText }}</pre>
          </section>
          <section class="inspector-section validation-list"><h3>自动校验（{{ validationItems.length }} 项）</h3><div v-for="check in validationItems" :key="check.label"><span><el-icon><CircleCheck /></el-icon>{{ check.label }}</span><em :class="check.ok ? 'pass' : 'warn'">{{ check.ok ? '通过' : '需关注' }}</em></div></section>
          <section class="inspector-section review-form">
            <h3>审核结论</h3>
            <el-radio-group v-model="reviewChoice" :disabled="!canReview"><el-radio value="APPROVE">通过</el-radio><el-radio value="REJECT">退回整改</el-radio></el-radio-group>
            <el-input v-model="reviewDraft" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="通过时可选，驳回时必填" :disabled="!canReview" />
            <div v-if="selectedItem.asset.reviewComment" class="previous-review"><b>最近审核意见</b><span>{{ selectedItem.asset.reviewComment }}</span></div>
            <div class="review-meta"><span>审核人：{{ currentReviewer }}</span><span>审核时间：{{ formatTime(selectedItem.asset.reviewedAt) }}</span></div>
            <footer><el-button @click="resetReviewDraft">取消</el-button><el-button v-if="canReview" :type="reviewChoice === 'APPROVE' ? 'success' : 'warning'" :loading="reviewingId === selectedItem.asset.id" @click="submitSelectedReview">{{ reviewChoice === 'APPROVE' ? '通过' : '退回整改' }}</el-button></footer>
          </section>
        </template>
        <el-empty v-else description="暂无资产信息" />
      </aside>
    </section>
    <p class="review-note"><el-icon><InfoFilled /></el-icon>审核结果仅影响资产的可用状态，不会直接修改或覆盖现有路线与部署。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PointCloudViewer from '@/components/PointCloudViewer.vue'
import { resourcesApi } from '@/api/resources'
import { subscribeTopic } from '@/api/realtime'
import { usePermission } from '@/composables/usePermission'
import { useAuthStore } from '@/stores/auth'
import { useRobotStore } from '@/stores/robot'
import { useSiteStore } from '@/stores/site'
import type { MapAsset, MapAssetStatus } from '@/types/mapAsset'
import type { SceneAsset, SceneAssetStatus } from '@/types/sceneAsset'
import { fetchMapAssetFiles } from '@/utils/mapAsset'
import { shortMapHash } from '@/utils/mapAssetReview'
import { createDefaultMapState, parsePgm, parseYaml, rebuildMapBitmap } from '@/utils/rosMap'
import { formatSceneFileSize, shortSceneHash } from '@/utils/sceneAsset'

type ReviewStatus = 'PENDING_REVIEW' | 'AVAILABLE' | 'REJECTED'
type AssetTypeFilter = 'all' | 'map' | 'scene'
type ReviewItem = { key: string; kind: 'map'; asset: MapAsset } | { key: string; kind: 'scene'; asset: SceneAsset }
const typeOptions = [{ label: '全部', value: 'all' }, { label: '二维地图', value: 'map' }, { label: '三维场景', value: 'scene' }]
const stageTypeOptions = [{ label: '二维地图', value: 'map' }, { label: '三维场景', value: 'scene' }]
const statusOptions = [{ label: '待审核', value: 'PENDING_REVIEW' }, { label: '已通过', value: 'AVAILABLE' }, { label: '已驳回', value: 'REJECTED' }]
const route = useRoute()
const siteStore = useSiteStore(); const robotStore = useRobotStore(); const authStore = useAuthStore(); const { can } = usePermission()
const mapAssets = ref<MapAsset[]>([]); const sceneAssets = ref<SceneAsset[]>([])
const typeFilter = ref<AssetTypeFilter>(route.query.type === 'scene' ? 'scene' : 'all'); const statusFilter = ref<ReviewStatus>('PENDING_REVIEW'); const siteFilter = ref(''); const robotFilter = ref(''); const selectedKey = ref(''); const currentPage = ref(1); const pageSize = 10
const loading = ref(false); const loadError = ref(''); const reviewingId = ref(''); const reviewChoice = ref<'APPROVE' | 'REJECT'>('APPROVE'); const reviewDraft = ref('')
const previewCanvasRef = ref<HTMLCanvasElement | null>(null); const mapPreviewLoading = ref(false); const mapPreviewReady = ref(false); const mapPreviewError = ref(''); const yamlText = ref(''); const yamlVisible = ref(false); const scenePreviewBlob = ref<Blob | null>(null); const scenePreviewLoading = ref(false)
const mapCanvasWrapRef = ref<HTMLElement | null>(null); const occupancyLayerVisible = ref(true); const boundaryLayerVisible = ref(true); const gridLayerVisible = ref(true); const isMapFullscreen = ref(false)
const minimumMapZoom = 25; const maximumMapZoom = 200; const mapZoomStep = 25; const mapZoomOptions = [25, 50, 75, 100, 125, 150, 175, 200]; const mapZoom = ref(100)
let mapLoadVersion = 0; let lastMapWheelAt = 0; let unsubscribeMaps: (() => void) | undefined; let unsubscribeScenes: (() => void) | undefined

const allItems = computed<ReviewItem[]>(() => [...mapAssets.value.map(asset => ({ key: `map:${asset.id}`, kind: 'map' as const, asset })), ...sceneAssets.value.map(asset => ({ key: `scene:${asset.id}`, kind: 'scene' as const, asset }))].sort((a, b) => new Date(b.asset.createdAt).getTime() - new Date(a.asset.createdAt).getTime()))
const filteredItems = computed(() => allItems.value.filter(item => item.asset.status === statusFilter.value && (typeFilter.value === 'all' || item.kind === typeFilter.value) && (!robotFilter.value || item.asset.sourceRobotId === robotFilter.value)))
const pagedItems = computed(() => filteredItems.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const selectedItem = computed(() => filteredItems.value.find(item => item.key === selectedKey.value))
const selectedMap = computed<MapAsset | undefined>(() => selectedItem.value?.kind === 'map' ? selectedItem.value.asset : undefined)
const selectedScene = computed<SceneAsset | undefined>(() => selectedItem.value?.kind === 'scene' ? selectedItem.value.asset : undefined)
const counts = computed(() => ({ pending: allItems.value.filter(x => x.asset.status === 'PENDING_REVIEW').length, approved: allItems.value.filter(x => x.asset.status === 'AVAILABLE').length, rejected: allItems.value.filter(x => x.asset.status === 'REJECTED').length }))
const metrics = computed(() => [{ value: 'PENDING_REVIEW' as const, label: '待审核', count: counts.value.pending, tone: 'pending', icon: 'Clock' }, { value: 'AVAILABLE' as const, label: '已通过', count: counts.value.approved, tone: 'approved', icon: 'CircleCheckFilled' }, { value: 'REJECTED' as const, label: '已驳回', count: counts.value.rejected, tone: 'rejected', icon: 'CircleCloseFilled' }])
const robotOptions = computed(() => [...new Set(allItems.value.map(x => x.asset.sourceRobotId).filter((id): id is string => Boolean(id)))].map(id => ({ id, name: robotName(id) })))
const canReview = computed(() => can('route:edit') && selectedItem.value?.asset.status === 'PENDING_REVIEW')
const currentReviewer = computed(() => selectedItem.value?.asset.reviewedBy || authStore.user?.displayName || '-')
const mapCanvasStyle = computed(() => ({ transform: `scale(${mapZoom.value / 100})` }))
const validationItems = computed(() => {
  if (selectedMap.value) return [
    { label: '文件完整性校验', ok: selectedMap.value.filesReady !== false }, { label: '命名规范校验', ok: Boolean(selectedMap.value.yamlName && selectedMap.value.pgmName) },
    { label: '坐标系与分辨率校验', ok: selectedMap.value.resolution > 0 && selectedMap.value.origin.length === 3 }, { label: '预览读取可用性', ok: mapPreviewReady.value && !mapPreviewError.value }, { label: '路线影响隔离校验', ok: true },
  ]
  if (selectedScene.value) return [
    { label: '文件完整性校验', ok: selectedScene.value.filesReady }, { label: '模型哈希校验', ok: Boolean(selectedScene.value.modelSha256 && selectedScene.value.metadataSha256) },
    { label: '坐标系与单位校验', ok: Boolean(selectedScene.value.coordinateSystem && selectedScene.value.unit) }, { label: '点数量一致性校验', ok: !selectedScene.value.pointCountMismatch }, { label: '二维地图隔离校验', ok: true },
  ]
  return []
})

async function refresh() {
  loading.value = true; loadError.value = ''
  const previousKey = selectedKey.value
  try {
    const [pendingMaps, availableMaps, rejectedMaps, scenes] = await Promise.all([
      resourcesApi.listMapAssets({ source: 'ROBOT', status: 'PENDING_REVIEW', siteId: siteFilter.value || undefined }),
      resourcesApi.listMapAssets({ source: 'ROBOT', status: 'AVAILABLE', siteId: siteFilter.value || undefined }),
      resourcesApi.listMapAssets({ source: 'ROBOT', status: 'REJECTED', siteId: siteFilter.value || undefined }),
      resourcesApi.listSceneAssets({ source: 'ROBOT', siteId: siteFilter.value || undefined, assetKind: 'POINT_CLOUD' }),
    ])
    mapAssets.value = [...pendingMaps, ...availableMaps, ...rejectedMaps]
    sceneAssets.value = scenes
    await nextTick()
    selectItem(filteredItems.value.find(item => item.key === previousKey) || filteredItems.value[0])
  } catch (error) {
    mapAssets.value = []; sceneAssets.value = []; selectedKey.value = ''
    loadError.value = message(error, '建图资产加载失败')
  } finally { loading.value = false }
}
function selectItem(item?: ReviewItem) {
  if (!item) { selectedKey.value = ''; clearPreviews(); return }
  const changed = selectedKey.value !== item.key
  selectedKey.value = item.key
  if (!changed) {
    if (item.kind === 'map' && !mapPreviewReady.value && !mapPreviewLoading.value) {
      void nextTick(() => loadMapPreview(item.asset))
    }
    return
  }
  clearPreviews(); resetReviewDraft()
  if (item.kind === 'map') void nextTick(() => loadMapPreview(item.asset))
}
function clearPreviews() { mapLoadVersion += 1; mapPreviewReady.value = false; mapPreviewError.value = ''; yamlText.value = ''; yamlVisible.value = false; scenePreviewBlob.value = null; mapZoom.value = 100 }

function fitMapToWindow() { mapZoom.value = 100 }
function adjustMapZoom(delta: number) { mapZoom.value = Math.min(maximumMapZoom, Math.max(minimumMapZoom, mapZoom.value + delta)) }
function handleMapWheel(event: WheelEvent) {
  if (!mapPreviewReady.value || event.deltaY === 0 || (event.target instanceof Element && event.target.closest('.map-view-controls'))) return
  event.preventDefault()
  const now = performance.now()
  if (now - lastMapWheelAt < 80) return
  lastMapWheelAt = now
  adjustMapZoom(event.deltaY < 0 ? mapZoomStep : -mapZoomStep)
}
async function toggleMapFullscreen() {
  if (!mapCanvasWrapRef.value) return
  try {
    if (document.fullscreenElement === mapCanvasWrapRef.value) await document.exitFullscreen()
    else await mapCanvasWrapRef.value.requestFullscreen()
  } catch { ElMessage.warning('当前浏览器无法切换地图全屏显示') }
}
function updateMapFullscreenState() { isMapFullscreen.value = document.fullscreenElement === mapCanvasWrapRef.value }

async function loadMapPreview(asset: MapAsset) {
  const version = ++mapLoadVersion
  mapPreviewLoading.value = true; mapPreviewReady.value = false; mapPreviewError.value = ''; yamlText.value = ''
  try {
    const files = await fetchMapAssetFiles(asset.id)
    const map = { ...createDefaultMapState(), ...parseYaml(files.yamlText), ...parsePgm(files.pgmBuffer) }
    await nextTick()
    if (version !== mapLoadVersion || !previewCanvasRef.value || selectedMap.value?.id !== asset.id) return
    rebuildMapBitmap(map, previewCanvasRef.value)
    yamlText.value = files.yamlText; mapPreviewReady.value = true
  } catch (error) {
    if (version !== mapLoadVersion) return
    mapPreviewError.value = error instanceof Error && error.message.includes('不存在') ? '该记录只有元数据，YAML/PGM 文件已不可读取，请让机器人重新上传。' : message(error, '地图预览加载失败')
  } finally { if (version === mapLoadVersion) mapPreviewLoading.value = false }
}
async function loadScenePreview(asset: SceneAsset) {
  if (asset.fileSize > 200 * 1024 * 1024 && !asset.previewReady) {
    try { await ElMessageBox.confirm(`当前将加载 ${formatSceneFileSize(asset.fileSize)} 原始点云，可能占用较多内存。是否继续？`, '大文件预览确认', { type: 'warning' }) } catch { return }
  }
  scenePreviewLoading.value = true
  try { scenePreviewBlob.value = await resourcesApi.getSceneAssetPreview(asset.id) }
  catch (error) { ElMessage.error(message(error, '点云预览加载失败')) }
  finally { scenePreviewLoading.value = false }
}

async function submitSelectedReview() {
  const item = selectedItem.value
  if (!item || !canReview.value) return
  if (reviewChoice.value === 'APPROVE') {
    if ((item.kind === 'map' && item.asset.filesReady === false) || (item.kind === 'scene' && !item.asset.filesReady)) { ElMessage.error('资产文件不完整，请修复后再审核'); return }
    const hint = item.kind === 'map' ? '通过后地图可被路线规划主动选择，但不会自动替换已有路线或部署。' : '通过后资产可用于三维展示，但不会自动关联导航地图或路线。'
    try { await ElMessageBox.confirm(`${hint}确认通过？`, '审核通过确认', { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消' }) } catch { return }
  } else if (!reviewDraft.value.trim()) {
    try {
      const result = await ElMessageBox.prompt('请填写可复核的驳回原因', item.kind === 'map' ? '驳回地图' : '驳回三维资产', { type: 'warning', inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '驳回原因不能为空' })
      reviewDraft.value = result.value.trim()
    } catch { return }
  }
  reviewingId.value = item.asset.id
  const comment = reviewDraft.value.trim() || '人工审核通过'
  try {
    if (item.kind === 'map') await resourcesApi.reviewMapAsset(item.asset.id, { action: reviewChoice.value, comment })
    else await resourcesApi.reviewSceneAsset(item.asset.id, { action: reviewChoice.value, comment })
    ElMessage.success(reviewChoice.value === 'APPROVE' ? '资产已通过审核' : '资产已退回整改')
    await refresh()
  } catch (error) { ElMessage.error(message(error, '审核提交失败')) }
  finally { reviewingId.value = '' }
}

function resetReviewDraft() { reviewChoice.value = 'APPROVE'; reviewDraft.value = '' }
async function downloadMapYaml(asset: MapAsset) { await download(resourcesApi.getMapAssetYaml(asset.id), asset.yamlName) }
async function downloadMapPgm(asset: MapAsset) { await download(resourcesApi.getMapAssetPgm(asset.id), asset.pgmName) }
async function downloadSceneMetadata(asset: SceneAsset) { await download(resourcesApi.getSceneAssetMetadata(asset.id), `${asset.id}-metadata.json`) }
async function downloadSceneModel(asset: SceneAsset) { await download(resourcesApi.getSceneAssetModel(asset.id), asset.originalName) }
async function download(promise: Promise<Blob>, filename: string) {
  try { const blob = await promise; const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url) }
  catch (error) { ElMessage.error(message(error, '文件下载失败')) }
}
function siteName(id: string) { return siteStore.getSiteById(id)?.name ?? id }
function robotName(id?: string | null) { return id ? robotStore.getRobotById(id)?.name ?? id : '-' }
function formatPoints(value: number) { return new Intl.NumberFormat('zh-CN').format(value) }
function formatOrigin(origin: [number, number, number]) { return origin.map(value => Number(value).toFixed(2)).join(', ') }
function formatTime(value?: string | null) { if (!value) return '-'; const date = new Date(value); return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false }) }
function statusLabel(status: MapAssetStatus | SceneAssetStatus) { return ({ PROCESSING: '处理中', PENDING_REVIEW: '待审核', AVAILABLE: '已通过', REJECTED: '已驳回', FAILED: '失败', DELETED: '已删除' })[status] }
function statusClass(status: MapAssetStatus | SceneAssetStatus) { return ({ PROCESSING: 'pending', PENDING_REVIEW: 'pending', AVAILABLE: 'approved', REJECTED: 'rejected', FAILED: 'rejected', DELETED: 'muted' })[status] }
function message(error: unknown, fallback: string) { return error instanceof Error ? error.message : fallback }

watch(siteFilter, () => { currentPage.value = 1; void refresh() })
watch([typeFilter, statusFilter, robotFilter], () => { currentPage.value = 1; const next = filteredItems.value[0]; if (selectedItem.value?.key !== next?.key) selectItem(next) })
watch(currentPage, () => { if (!pagedItems.value.some(item => item.key === selectedKey.value)) selectItem(pagedItems.value[0]) })
onMounted(() => { void refresh(); document.addEventListener('fullscreenchange', updateMapFullscreenState); unsubscribeMaps = subscribeTopic('/topic/map-assets', () => void refresh()); unsubscribeScenes = subscribeTopic('/topic/scene-assets', () => void refresh()) })
onUnmounted(() => { document.removeEventListener('fullscreenchange', updateMapFullscreenState); unsubscribeMaps?.(); unsubscribeScenes?.(); clearPreviews() })
</script>
<style scoped>
.review-center{--ink:#102a56;--muted:#6f8099;--line:#e0e8f2;min-width:0}.status-metrics{margin-left:auto;display:flex;align-self:stretch}.status-metrics button{min-width:94px;padding:3px 10px;display:flex;align-items:center;justify-content:center;gap:9px;border:0;border-left:1px solid #e4ebf3;background:transparent;color:#647790;cursor:pointer}.status-metrics button.active{background:#f6f9fe}.status-metrics .el-icon{font-size:20px}.status-metrics span,.status-metrics b{display:block}.status-metrics span{font-size:11px;text-align:left}.status-metrics b{color:#132747;font-size:21px;line-height:1}.pending{color:#ef920d}.approved{color:#0ca968}.rejected{color:#e5484d}
.filter-bar{min-height:62px;margin-bottom:10px;padding:7px 14px;display:flex;align-items:center;gap:16px;border:1px solid var(--line);border-radius:8px;background:#fff}.filter-bar label{display:flex;align-items:center;gap:8px;color:#526986;font-size:12px;white-space:nowrap}.filter-bar .el-select{width:150px}.load-alert{margin-bottom:10px}.review-workbench{min-height:650px;height:calc(100vh - 242px);display:grid;grid-template-columns:minmax(300px,21%) minmax(430px,1fr) minmax(340px,27%);overflow:hidden;border:1px solid var(--line);border-radius:8px;background:#fff;box-shadow:0 4px 16px rgb(34 68 112/7%)}.asset-queue,.asset-stage,.review-inspector{min-width:0;min-height:0}.asset-queue{display:flex;flex-direction:column;border-right:1px solid var(--line)}.asset-queue>header,.stage-header{min-height:48px;padding:0 14px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line)}.asset-queue header strong{color:var(--ink);font-size:13px}.asset-queue header small{color:var(--muted)}.queue-list{flex:1;min-height:0;padding:7px;overflow:auto}.asset-row{width:100%;min-height:92px;margin-bottom:7px;padding:9px;display:grid;grid-template-columns:82px minmax(0,1fr);gap:10px;border:1px solid #e5ebf3;border-radius:7px;background:#fff;color:inherit;text-align:left;cursor:pointer;transition:150ms ease}.asset-row:hover{border-color:#a9c7ef;transform:translateY(-1px)}.asset-row.selected{border-color:#1780f5;box-shadow:0 0 0 1px #1780f5,0 5px 12px rgb(23 104 242/10%)}
.asset-thumb{position:relative;display:grid;place-items:center;overflow:hidden;border-radius:5px;background:#4e7479;color:#fff;font-size:28px}.asset-thumb.scene{background:#101820}.asset-thumb:before{position:absolute;content:'';inset:0;background-image:linear-gradient(rgb(255 255 255/12%) 1px,transparent 1px),linear-gradient(90deg,rgb(255 255 255/12%) 1px,transparent 1px);background-size:12px 12px}.asset-thumb .el-icon{z-index:1}.asset-thumb small{position:absolute;z-index:1;right:4px;top:4px;padding:1px 4px;border-radius:3px;background:rgb(5 20 38/72%);font-size:9px}.asset-summary{min-width:0;display:flex;flex-direction:column;justify-content:center;gap:5px}.summary-top{display:flex;align-items:center;justify-content:space-between;gap:6px}.summary-top em{font-size:11px;font-style:normal}.asset-summary>strong{overflow:hidden;color:#203856;font:700 12px ui-monospace,monospace;text-overflow:ellipsis;white-space:nowrap}.asset-summary>span:not(.summary-top){overflow:hidden;color:#6f8099;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.asset-summary i{display:inline-block;height:9px;margin:0 6px;border-left:1px solid #ccd7e4}.asset-queue>footer{min-height:48px;padding:7px 9px;display:flex;align-items:center;justify-content:space-between;border-top:1px solid var(--line);color:var(--muted);font-size:11px}.asset-queue :deep(.el-pagination){margin:0}.asset-stage{display:flex;flex-direction:column;background:#eef3f7}.stage-header{flex:0 0 48px;background:#fff}.stage-header strong,.stage-header small{display:block}.stage-header strong{max-width:420px;overflow:hidden;color:#152f52;font:700 14px ui-monospace,monospace;text-overflow:ellipsis;white-space:nowrap}.stage-header small{margin-top:2px;color:#8796aa}.map-viewport,.scene-viewport{flex:1;min-height:0;display:flex;flex-direction:column}
.viewport-toolbar{min-height:42px;padding:6px 12px;display:flex;align-items:center;gap:10px;border-bottom:1px solid #d8e1e9;background:#f9fbfd;color:#65788f;font-size:11px}.viewport-toolbar .el-button{margin-left:auto}.viewport-toolbar :deep(.el-checkbox){margin-right:2px}.viewport-toolbar :deep(.el-checkbox__label){padding-left:5px;font-size:11px}.map-canvas-wrap{position:relative;flex:1;min-height:480px;padding:22px;display:flex;align-items:center;justify-content:center;overflow:hidden;background-color:#6e8f91;background-image:linear-gradient(rgb(255 255 255/12%) 1px,transparent 1px),linear-gradient(90deg,rgb(255 255 255/12%) 1px,transparent 1px);background-size:24px 24px}.map-canvas-wrap.grid-hidden{background-image:none}.map-canvas-wrap:fullscreen{min-height:100vh;padding:72px 24px 92px}.map-canvas-wrap canvas{display:block;max-width:92%;max-height:calc(100% - 96px);width:auto;height:auto;background:#fff;box-shadow:0 8px 22px rgb(18 43 58/22%);image-rendering:pixelated;transform-origin:center;transition:transform 160ms ease}.map-canvas-wrap canvas.show-boundary{outline:2px solid #f1c65b;outline-offset:2px}.map-canvas-wrap :deep(.el-result__title p),.map-canvas-wrap :deep(.el-result__subtitle p),.viewport-empty{color:#f4f8fa}.map-scale,.map-meta{position:absolute;z-index:3;top:12px;padding:8px 10px;display:grid;gap:3px;border-radius:5px;background:rgb(25 57 66/76%);color:#e9f0f2;font-size:10px;pointer-events:none}.map-scale{left:12px}.map-scale b{width:52px;padding-top:5px;border-bottom:2px solid #fff;text-align:center}.map-meta{right:12px;text-align:right;font-family:ui-monospace,monospace}.map-view-controls{position:absolute;z-index:5;left:50%;bottom:18px;max-width:calc(100% - 24px);padding:8px;display:flex;align-items:center;gap:8px;overflow-x:auto;border:1px solid rgb(214 224 235/92%);border-radius:8px;background:rgb(255 255 255/96%);box-shadow:0 5px 18px rgb(31 55 82/18%);transform:translateX(-50%);white-space:nowrap}.map-view-controls .el-button{height:34px;margin:0}.map-view-controls .fit-button{padding-inline:13px}.map-view-controls .icon-control{width:34px;padding:0}.map-view-controls .zoom-select{width:84px;flex:0 0 84px}.map-view-controls .zoom-select :deep(.el-select__wrapper){min-height:34px;box-shadow:0 0 0 1px #dce5ef inset}.scene-load-bar{min-height:58px;padding:8px 12px;display:flex;align-items:center;justify-content:space-between;gap:12px;background:#f7fafc}.scene-load-bar strong,.scene-load-bar small{display:block}.scene-load-bar strong{color:#163553;font-size:13px}.scene-load-bar small{margin-top:2px;color:#76899c}.scene-viewport :deep(.cloud-viewer){flex:1;min-height:0;border:0}.scene-viewport :deep(.viewer-stage){height:calc(100% - 96px);min-height:470px}
.review-inspector{overflow:auto;background:#fbfcfe;border-left:1px solid var(--line)}.inspector-section{padding:12px 16px;border-bottom:1px solid var(--line);background:#fff}.inspector-section h3{margin:0 0 10px;color:#173252;font-size:13px}.asset-facts dl{margin:0;display:grid;grid-template-columns:74px minmax(80px,1fr) 86px minmax(80px,1fr);gap:8px 9px;font-size:10px;line-height:1.45}.asset-facts dt{color:#77889e}.asset-facts dd{min-width:0;margin:0;overflow-wrap:anywhere;color:#415873}.asset-facts .accent{color:#1768f2;font-weight:700}.asset-facts code{color:#566b84;font:9px ui-monospace,monospace}.file-actions{margin-top:12px;display:flex;flex-wrap:wrap;gap:6px}.file-actions .el-button{margin:0}.yaml-source{max-height:170px;margin:10px 0 0;padding:10px;overflow:auto;border:1px solid #dce5ee;border-radius:5px;background:#f5f8fb;color:#28435e;font:10px/1.55 ui-monospace,monospace;white-space:pre-wrap}.validation-list{display:grid;gap:8px}.validation-list h3{margin-bottom:2px}.validation-list>div{display:flex;align-items:center;justify-content:space-between;color:#50657d;font-size:11px}.validation-list>div span{display:flex;align-items:center;gap:6px}.validation-list em{font-style:normal}.validation-list .pass{color:#09965c}.validation-list .warn{color:#e48300}.review-form .el-radio-group{margin-bottom:9px;display:flex;gap:11px}.previous-review{margin-top:9px;display:grid;gap:3px;color:#60738b;font-size:10px}.review-meta{margin-top:10px;display:grid;gap:4px;color:#8090a4;font-size:10px}.review-form footer{margin-top:10px;display:flex;justify-content:flex-end;gap:8px}.review-form footer .el-button{min-width:88px}.review-note{margin:9px 2px 0;display:flex;align-items:center;gap:6px;color:#71839b;font-size:11px}
@media(max-width:1450px){.filter-bar{flex-wrap:wrap}.status-metrics{width:100%;min-height:48px;justify-content:flex-end;border-top:1px solid var(--line);padding-top:6px}.review-workbench{height:auto;grid-template-columns:310px minmax(500px,1fr)}.review-inspector{grid-column:1/-1;display:grid;grid-template-columns:1.35fr .8fr 1fr;border-top:1px solid var(--line);border-left:0}.inspector-section{border-right:1px solid var(--line);border-bottom:0}}
@media(max-width:980px){.status-metrics{overflow-x:auto}.filter-bar{align-items:stretch;flex-direction:column;gap:9px}.filter-bar label{justify-content:space-between}.filter-bar .el-select{width:min(72vw,340px)}.status-filter{margin-left:0}.review-workbench{display:flex;height:auto;flex-direction:column}.asset-queue{max-height:470px;border-right:0;border-bottom:1px solid var(--line)}.asset-stage{min-height:560px}.review-inspector{display:block}.inspector-section{border-right:0;border-bottom:1px solid var(--line)}}
@media(max-width:640px){.status-metrics{justify-content:flex-start}.status-metrics button{min-width:94px;padding-inline:8px}.asset-facts dl{grid-template-columns:72px minmax(0,1fr)}.map-canvas-wrap{min-height:400px;padding:10px}.map-canvas-wrap canvas{max-width:96%;max-height:calc(100% - 104px)}.map-view-controls{bottom:10px}.viewport-toolbar{overflow-x:auto}.stage-header strong{max-width:50vw}}
</style>
