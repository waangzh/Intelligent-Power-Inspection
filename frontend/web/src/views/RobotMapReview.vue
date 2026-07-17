<template>
  <div class="map-review-page">
    <PageHeader
      title="机器人建图审核"
      description="核验机器人上传的 ROS 栅格地图；审核结果只控制后续可选范围，不会替换既有路线或部署。"
      :breadcrumbs="[{ label: '巡检业务' }, { label: '机器人建图审核' }]"
    >
      <template #actions>
        <el-button :loading="loading" @click="refresh">
          <el-icon><Refresh /></el-icon>
          刷新台账
        </el-button>
      </template>
    </PageHeader>

    <section class="review-banner" aria-label="审核说明">
      <div class="banner-copy">
        <span class="eyebrow">MAP INTAKE / HUMAN GATE</span>
        <strong>地图先验收，路线后选择</strong>
        <p>待审核和已驳回资产始终与路线运行链路隔离。</p>
      </div>
      <div class="banner-metric"><b>{{ assets.length }}</b><span>当前筛选结果</span></div>
      <div class="banner-metric"><b>{{ totalPixels }}</b><span>像素总量</span></div>
      <div class="banner-rule"><i />不会自动变更<br>路线 · 修订 · 部署</div>
    </section>

    <el-card shadow="never" class="ledger-card">
      <template #header>
        <div class="ledger-header">
          <div>
            <strong>建图资产审核台账</strong>
            <small>SHA-256 仅显示摘要，完整值可在详情中核对</small>
          </div>
          <div class="filters">
            <el-select v-model="siteFilter" clearable placeholder="全部站点" class="site-filter" aria-label="按站点筛选">
              <el-option v-for="site in siteStore.sites" :key="site.id" :label="site.name" :value="site.id" />
            </el-select>
            <el-segmented v-model="statusFilter" :options="statusOptions" aria-label="按审核状态筛选" />
          </div>
        </div>
      </template>

      <el-alert v-if="loadError" type="error" :closable="false" show-icon class="load-alert" :title="loadError" />
      <el-table v-else :data="assets" v-loading="loading" class="review-table" empty-text="当前条件下没有机器人地图">
        <el-table-column label="地图 / 站点" min-width="205" fixed="left">
          <template #default="{ row }: { row: MapAsset }">
            <div class="identity-cell">
              <strong>{{ row.id }}</strong>
              <span>{{ siteName(row.siteId) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源机器人" min-width="170">
          <template #default="{ row }: { row: MapAsset }">
            <div class="identity-cell"><strong>{{ robotName(row.sourceRobotId) }}</strong><span>{{ row.sourceRobotId || '-' }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="地图文件" min-width="185">
          <template #default="{ row }: { row: MapAsset }">
            <div class="file-stack"><span>YAML · {{ row.yamlName }}</span><span>PGM · {{ row.pgmName }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="145">
          <template #default="{ row }: { row: MapAsset }">
            <div class="spec-cell"><strong>{{ row.width }} × {{ row.height }}</strong><span>{{ row.resolution }} m/px</span></div>
          </template>
        </el-table-column>
        <el-table-column label="SHA-256 摘要" min-width="180">
          <template #default="{ row }: { row: MapAsset }">
            <div class="hash-stack"><code>Y {{ shortMapHash(row.yamlSha256) }}</code><code>P {{ shortMapHash(row.pgmSha256) }}</code></div>
          </template>
        </el-table-column>
        <el-table-column label="上传 / 建图时间" min-width="190">
          <template #default="{ row }: { row: MapAsset }">
            <div class="time-stack"><span>{{ formatTime(row.createdAt) }}</span><small>建图 {{ formatTime(row.capturedAt) }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="状态 / 意见" min-width="190">
          <template #default="{ row }: { row: MapAsset }">
            <div class="review-state">
              <div class="state-tags">
                <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                <el-tag v-if="row.filesReady === false" size="small" type="danger">文件缺失</el-tag>
              </div>
              <span>{{ row.reviewComment || '暂无审核意见' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }: { row: MapAsset }">
            <el-button link type="primary" @click="openDetail(row)">详情 / 文件</el-button>
            <template v-if="can('route:edit') && row.status === 'PENDING_REVIEW'">
              <el-button link type="success" :disabled="row.filesReady === false" :loading="reviewingId === row.id" @click="approve(row)">通过</el-button>
              <el-button link type="danger" :loading="reviewingId === row.id" @click="reject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="地图资产核验" size="min(860px, 92vw)" destroy-on-close>
      <template v-if="selectedAsset">
        <div class="drawer-heading">
          <div><span class="eyebrow">MAP ASSET</span><strong>{{ selectedAsset.id }}</strong></div>
          <el-tag :type="statusType(selectedAsset.status)">{{ statusLabel(selectedAsset.status) }}</el-tag>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="站点">{{ siteName(selectedAsset.siteId) }} · {{ selectedAsset.siteId }}</el-descriptions-item>
          <el-descriptions-item label="来源机器人">{{ robotName(selectedAsset.sourceRobotId) }} · {{ selectedAsset.sourceRobotId }}</el-descriptions-item>
          <el-descriptions-item label="分辨率 / 尺寸">{{ selectedAsset.resolution }} m/px · {{ selectedAsset.width }} × {{ selectedAsset.height }}</el-descriptions-item>
          <el-descriptions-item label="YAML SHA-256"><code class="full-hash">{{ selectedAsset.yamlSha256 }}</code></el-descriptions-item>
          <el-descriptions-item label="PGM SHA-256"><code class="full-hash">{{ selectedAsset.pgmSha256 }}</code></el-descriptions-item>
          <el-descriptions-item label="审核意见">{{ selectedAsset.reviewComment || '-' }}</el-descriptions-item>
        </el-descriptions>
        <section class="map-preview-panel" aria-label="ROS 栅格地图预览">
          <div class="preview-heading">
            <div><strong>地图预览</strong><span>使用与路线规划相同的 YAML + PGM 解析方式</span></div>
            <el-button size="small" :loading="previewLoading" @click="loadPreview(selectedAsset)">重新加载</el-button>
          </div>
          <div v-loading="previewLoading" class="preview-stage">
            <canvas v-show="previewReady" ref="previewCanvasRef" aria-label="ROS 栅格地图画布" />
            <el-result v-if="previewError" icon="error" title="地图文件无法预览" :sub-title="previewError" />
            <div v-else-if="!previewReady && !previewLoading" class="preview-empty">暂无可预览的栅格地图</div>
          </div>
          <div v-if="previewReady" class="preview-meta">
            <span><b>{{ selectedAsset.width }} × {{ selectedAsset.height }}</b> px</span>
            <span><b>{{ selectedAsset.resolution }}</b> m/px</span>
            <span><b>{{ formatOrigin(selectedAsset.origin) }}</b> origin</span>
          </div>
        </section>
        <div class="file-actions">
          <el-button :disabled="!yamlText" @click="yamlVisible = !yamlVisible">{{ yamlVisible ? '收起 YAML' : '查看 YAML 原文' }}</el-button>
          <el-button :disabled="selectedAsset.filesReady === false" @click="downloadYaml(selectedAsset)">下载 YAML</el-button>
          <el-button :disabled="selectedAsset.filesReady === false" @click="downloadPgm(selectedAsset)">下载 PGM</el-button>
        </div>
        <div v-if="yamlVisible && yamlText" class="yaml-preview"><div>YAML 原文</div><pre>{{ yamlText }}</pre></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { resourcesApi } from '@/api/resources'
import { usePermission } from '@/composables/usePermission'
import { useRobotStore } from '@/stores/robot'
import { useSiteStore } from '@/stores/site'
import type { MapAsset, MapAssetStatus } from '@/types/mapAsset'
import { fetchMapAssetFiles } from '@/utils/mapAsset'
import { shortMapHash } from '@/utils/mapAssetReview'
import { createDefaultMapState, parsePgm, parseYaml, rebuildMapBitmap } from '@/utils/rosMap'

const statusOptions = [
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '已通过', value: 'AVAILABLE' },
  { label: '已驳回', value: 'REJECTED' },
]
const siteStore = useSiteStore()
const robotStore = useRobotStore()
const { can } = usePermission()
const assets = ref<MapAsset[]>([])
const statusFilter = ref<MapAssetStatus>('PENDING_REVIEW')
const siteFilter = ref('')
const loading = ref(false)
const loadError = ref('')
const reviewingId = ref('')
const detailVisible = ref(false)
const selectedAsset = ref<MapAsset>()
const yamlText = ref('')
const yamlVisible = ref(false)
const previewCanvasRef = ref<HTMLCanvasElement | null>(null)
const previewLoading = ref(false)
const previewReady = ref(false)
const previewError = ref('')

const totalPixels = computed(() => new Intl.NumberFormat('zh-CN', { notation: 'compact' })
  .format(assets.value.reduce((sum, asset) => sum + asset.width * asset.height, 0)))

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    assets.value = await resourcesApi.listMapAssets({ source: 'ROBOT', status: statusFilter.value, siteId: siteFilter.value || undefined })
  } catch (error) {
    assets.value = []
    loadError.value = error instanceof Error ? error.message : '待审核地图加载失败'
  } finally {
    loading.value = false
  }
}

function openDetail(asset: MapAsset) {
  selectedAsset.value = asset
  yamlText.value = ''
  yamlVisible.value = false
  previewReady.value = false
  previewError.value = ''
  detailVisible.value = true
  void nextTick(() => loadPreview(asset))
}

async function approve(asset: MapAsset) {
  if (asset.filesReady === false) {
    ElMessage.error('地图文件不完整，请让机器人使用新的 Idempotency-Key 重新上传后再审核')
    return
  }
  try {
    await ElMessageBox.confirm('通过后地图可被路线规划主动选择，但不会自动替换任何已有路线或部署。确认通过？', '审核通过确认', {
      type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消',
    })
  } catch { return }
  await submitReview(asset, 'APPROVE', '人工审核通过')
}

async function reject(asset: MapAsset) {
  try {
    const result = await ElMessageBox.prompt('请填写可复核的驳回原因', '驳回地图', {
      type: 'warning', inputType: 'textarea', inputPlaceholder: '例如：地图边界与现场不一致',
      inputValidator: value => Boolean(value?.trim()) || '驳回原因不能为空',
      confirmButtonText: '确认驳回', cancelButtonText: '取消',
    })
    await submitReview(asset, 'REJECT', result.value.trim())
  } catch { /* 用户取消 */ }
}

async function submitReview(asset: MapAsset, action: 'APPROVE' | 'REJECT', comment: string) {
  reviewingId.value = asset.id
  try {
    await resourcesApi.reviewMapAsset(asset.id, { action, comment })
    ElMessage.success(action === 'APPROVE' ? '地图已通过审核' : '地图已驳回')
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核提交失败')
  } finally {
    reviewingId.value = ''
  }
}

async function loadPreview(asset: MapAsset) {
  previewLoading.value = true
  previewReady.value = false
  previewError.value = ''
  yamlText.value = ''
  try {
    const files = await fetchMapAssetFiles(asset.id)
    const parsedPgm = parsePgm(files.pgmBuffer)
    const map = { ...createDefaultMapState(), ...parseYaml(files.yamlText), ...parsedPgm }
    await nextTick()
    if (!previewCanvasRef.value || selectedAsset.value?.id !== asset.id) return
    rebuildMapBitmap(map, previewCanvasRef.value)
    yamlText.value = files.yamlText
    previewReady.value = true
  } catch (error) {
    previewError.value = error instanceof Error && error.message.includes('不存在')
      ? '该记录只有元数据，YAML/PGM 实体文件已不可读取。请让机器人使用新的 Idempotency-Key 重新上传。'
      : error instanceof Error ? error.message : '地图预览加载失败'
  } finally {
    previewLoading.value = false
  }
}

async function downloadYaml(asset: MapAsset) { await download(resourcesApi.getMapAssetYaml(asset.id), asset.yamlName) }
async function downloadPgm(asset: MapAsset) { await download(resourcesApi.getMapAssetPgm(asset.id), asset.pgmName) }
async function download(blobPromise: Promise<Blob> | Blob, filename: string) {
  try {
    const blob = await blobPromise
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url; anchor.download = filename; anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '文件下载失败') }
}

function siteName(id: string) { return siteStore.getSiteById(id)?.name ?? id }
function robotName(id?: string | null) { return id ? robotStore.getRobotById(id)?.name ?? id : '-' }
function statusLabel(status: MapAssetStatus) { return ({ PENDING_REVIEW: '待审核', AVAILABLE: '已通过', REJECTED: '已驳回' })[status] }
function statusType(status: MapAssetStatus) { return ({ PENDING_REVIEW: 'warning', AVAILABLE: 'success', REJECTED: 'danger' } as const)[status] }
function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}
function formatOrigin(origin: [number, number, number]) { return origin.map(value => Number(value).toFixed(2)).join(', ') }

watch([statusFilter, siteFilter], () => void refresh())
onMounted(() => void refresh())
</script>

<style scoped>
.map-review-page { --review-ink: #173347; --review-cyan: #0a95a7; --review-line: #d9e5ea; }
.review-banner { display: grid; grid-template-columns: minmax(280px, 1.6fr) repeat(2, minmax(120px, .55fr)) minmax(210px, .8fr); margin-bottom: 18px; color: #ecf9fb; background: linear-gradient(112deg, #173648 0%, #155869 62%, #113d52 100%); border: 1px solid #2a7180; box-shadow: 0 10px 24px rgb(16 61 76 / 14%); }
.banner-copy, .banner-metric, .banner-rule { min-height: 104px; padding: 20px 24px; border-right: 1px solid rgb(225 249 253 / 16%); display: flex; flex-direction: column; justify-content: center; }
.eyebrow { color: #80dbe7; font-size: 10px; font-weight: 800; letter-spacing: .15em; }.banner-copy strong { margin: 5px 0 3px; font-size: 22px; letter-spacing: .03em; }.banner-copy p { margin: 0; color: #b8d7de; font-size: 12px; }.banner-metric b { color: #83e4c0; font-size: 30px; font-variant-numeric: tabular-nums; }.banner-metric span { margin-top: 5px; color: #b8d7de; font-size: 12px; }.banner-rule { border: 0; color: #c4dce2; font-size: 12px; line-height: 1.7; }.banner-rule i { width: 34px; height: 3px; margin-bottom: 10px; background: #f1b85b; }
.ledger-card { border-color: var(--review-line); }.ledger-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; }.ledger-header strong, .ledger-header small { display: block; }.ledger-header strong { color: var(--review-ink); letter-spacing: .04em; }.ledger-header small { margin-top: 4px; color: #80919a; }.filters { display: flex; gap: 10px; }.site-filter { width: 180px; }.load-alert { margin-bottom: 14px; }
.review-table :deep(th.el-table__cell) { background: #f2f7f8; color: #58717d; font-size: 12px; letter-spacing: .04em; }.identity-cell strong, .identity-cell span, .file-stack span, .spec-cell strong, .spec-cell span, .time-stack span, .time-stack small, .review-state span { display: block; }.identity-cell strong { color: #223e50; }.identity-cell span, .spec-cell span, .time-stack small { margin-top: 3px; color: #87969e; font-size: 12px; }.identity-cell strong, .hash-stack code, .full-hash { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }.file-stack, .hash-stack, .time-stack, .review-state { display: grid; gap: 4px; }.file-stack span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.hash-stack code { color: #426475; font-size: 11px; }.review-state span { color: #71838d; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.drawer-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }.drawer-heading strong { display: block; margin-top: 5px; color: var(--review-ink); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 17px; }.full-hash { word-break: break-all; font-size: 11px; }.file-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }.yaml-preview { margin-top: 16px; border: 1px solid #d5e3e8; background: #f5f9fa; }.yaml-preview > div { padding: 9px 12px; border-bottom: 1px solid #d5e3e8; color: #55717e; font-size: 12px; font-weight: 700; }.yaml-preview pre { max-height: 340px; margin: 0; padding: 14px; overflow: auto; color: #17394b; font: 12px/1.65 ui-monospace, SFMono-Regular, Menlo, monospace; }
.state-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.map-preview-panel { margin-top: 18px; overflow: hidden; border: 1px solid #cddde3; background: #eef4f5; }
.preview-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 11px 14px; border-bottom: 1px solid #cddde3; background: #f8fbfb; }
.preview-heading strong, .preview-heading span { display: block; }
.preview-heading strong { color: var(--review-ink); }
.preview-heading span { margin-top: 3px; color: #78909a; font-size: 11px; }
.preview-stage { min-height: 360px; display: flex; align-items: center; justify-content: center; padding: 18px; background-color: #173347; background-image: linear-gradient(rgb(255 255 255 / 4%) 1px, transparent 1px), linear-gradient(90deg, rgb(255 255 255 / 4%) 1px, transparent 1px); background-size: 20px 20px; }
.preview-stage canvas { display: block; max-width: 100%; max-height: 520px; width: auto; height: auto; background: #fff; box-shadow: 0 12px 30px rgb(0 0 0 / 28%); image-rendering: pixelated; }
.preview-stage :deep(.el-result) { padding: 24px; }
.preview-stage :deep(.el-result__title p) { color: #f2f7f8; }
.preview-stage :deep(.el-result__subtitle p), .preview-empty { max-width: 560px; color: #b9cbd2; line-height: 1.7; text-align: center; }
.preview-meta { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid #cddde3; background: #f8fbfb; }
.preview-meta span { padding: 9px 12px; color: #71848d; font-size: 11px; text-align: center; border-right: 1px solid #dce7ea; }
.preview-meta span:last-child { border: 0; }
.preview-meta b { color: #294b5a; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
@media (max-width: 900px) { .review-banner { grid-template-columns: 1fr 1fr; }.banner-copy { grid-column: 1 / -1; }.banner-rule { display: none; }.ledger-header { align-items: flex-start; flex-direction: column; }.filters { width: 100%; }.site-filter { flex: 1; }.preview-meta { grid-template-columns: 1fr; }.preview-meta span { border-right: 0; border-bottom: 1px solid #dce7ea; } }
</style>
