<template>
  <div class="scene-review-page">
    <PageHeader title="机器人三维建图审核" description="验收 ZED 离线重建点云；三维场景与 ROS 二维导航地图保持独立。" :breadcrumbs="[{ label: '巡检业务' }, { label: '三维建图审核' }]">
      <template #actions><el-button :loading="loading" @click="refresh"><el-icon><Refresh /></el-icon>刷新资产</el-button></template>
    </PageHeader>

    <section class="scene-hero">
      <div><span>SCENE INTAKE / Z-UP</span><h2>点云进入业务前，先经过人工闸门</h2><p>审核只改变三维资产可用状态，不会覆盖二维地图、路线或部署。</p></div>
      <dl><dt>{{ assets.length }}</dt><dd>筛选资产</dd></dl>
      <dl><dt>{{ compactPoints }}</dt><dd>点数量</dd></dl>
      <aside><i />RIGHT HANDED<br><b>METER · Z UP</b></aside>
    </section>

    <el-card shadow="never" class="ledger-card">
      <template #header>
        <div class="ledger-heading">
          <div><strong>三维场景资产台账</strong><small>预览按需加载，避免原始大文件占用浏览器内存</small></div>
          <div class="filters">
            <el-select v-model="siteFilter" clearable placeholder="全部站点"><el-option v-for="site in siteStore.sites" :key="site.id" :label="site.name" :value="site.id" /></el-select>
            <el-segmented v-model="statusFilter" :options="statusOptions" />
          </div>
        </div>
      </template>
      <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" class="load-alert" />
      <el-table v-else v-loading="loading" :data="assets" empty-text="当前条件下没有三维场景资产">
        <el-table-column label="场景 / 站点" min-width="205" fixed="left"><template #default="{ row }: { row: SceneAsset }"><div class="stack identity"><strong>{{ row.id }}</strong><span>{{ siteName(row.siteId) }}</span></div></template></el-table-column>
        <el-table-column label="来源机器人" min-width="165"><template #default="{ row }: { row: SceneAsset }"><div class="stack"><strong>{{ robotName(row.sourceRobotId) }}</strong><span>{{ row.sourceRobotId || '-' }}</span></div></template></el-table-column>
        <el-table-column label="重建任务" min-width="190"><template #default="{ row }: { row: SceneAsset }"><div class="stack mono"><strong>{{ row.sourceReconstructSessionId }}</strong><span>{{ row.reconstructProfile || '默认 profile' }}</span></div></template></el-table-column>
        <el-table-column label="文件 / 点数" min-width="180"><template #default="{ row }: { row: SceneAsset }"><div class="stack"><strong>{{ row.originalName }}</strong><span>{{ formatSceneFileSize(row.fileSize) }} · {{ formatPoints(row.pointCount) }} 点</span></div></template></el-table-column>
        <el-table-column label="坐标约定" min-width="160"><template #default="{ row }: { row: SceneAsset }"><div class="stack mono"><strong>{{ row.coordinateSystem }}</strong><span>{{ row.unit }}</span></div></template></el-table-column>
        <el-table-column label="SHA-256 摘要" min-width="175"><template #default="{ row }: { row: SceneAsset }"><code>{{ shortSceneHash(row.modelSha256) }}</code></template></el-table-column>
        <el-table-column label="重建 / 上传时间" min-width="190"><template #default="{ row }: { row: SceneAsset }"><div class="stack"><strong>{{ formatTime(row.reconstructedAt) }}</strong><span>{{ formatTime(row.createdAt) }}</span></div></template></el-table-column>
        <el-table-column label="状态" min-width="155"><template #default="{ row }: { row: SceneAsset }"><div class="state"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag><el-tag v-if="!row.filesReady" size="small" type="danger">文件已清理</el-tag></div></template></el-table-column>
        <el-table-column label="操作" width="225" fixed="right"><template #default="{ row }: { row: SceneAsset }"><el-button link type="primary" @click="openDetail(row)">详情 / 预览</el-button><template v-if="can('route:edit') && row.status === 'PENDING_REVIEW'"><el-button link type="success" :disabled="!row.filesReady" :loading="reviewingId === row.id" @click="approve(row)">通过</el-button><el-button link type="danger" :loading="reviewingId === row.id" @click="reject(row)">驳回</el-button></template></template></el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="三维场景核验" size="min(1040px, 96vw)" destroy-on-close @closed="clearPreview">
      <template v-if="selectedAsset">
        <div class="drawer-title"><div><span>SCENE ASSET</span><strong>{{ selectedAsset.id }}</strong></div><el-tag :type="statusType(selectedAsset.status)">{{ statusLabel(selectedAsset.status) }}</el-tag></div>
        <div class="fact-grid">
          <article><span>站点</span><b>{{ siteName(selectedAsset.siteId) }}</b><small>{{ selectedAsset.siteId }}</small></article>
          <article><span>机器人</span><b>{{ robotName(selectedAsset.sourceRobotId) }}</b><small>{{ selectedAsset.sourceRobotId }}</small></article>
          <article><span>点数量</span><b>{{ formatPoints(selectedAsset.pointCount) }}</b><small v-if="selectedAsset.pointCountMismatch">上报 {{ formatPoints(selectedAsset.reportedPointCount || 0) }}</small><small v-else>PLY header 已核验</small></article>
          <article><span>文件大小</span><b>{{ formatSceneFileSize(selectedAsset.fileSize) }}</b><small>{{ selectedAsset.originalName }}</small></article>
        </div>
        <section class="preview-block">
          <div class="preview-head"><div><strong>点云空间预览</strong><span>{{ selectedAsset.previewReady ? '使用降采样预览文件' : '当前由原始 PLY 提供预览，建议服务端生成降采样文件' }}</span></div><el-button type="primary" :disabled="!selectedAsset.filesReady" :loading="previewLoading" @click="loadPreview(selectedAsset)">{{ previewBlob ? '重新加载' : '加载点云预览' }}</el-button></div>
          <PointCloudViewer :blob="previewBlob" :point-count="selectedAsset.pointCount" />
        </section>
        <el-descriptions :column="2" border size="small" class="details">
          <el-descriptions-item label="重建 session">{{ selectedAsset.sourceReconstructSessionId }}</el-descriptions-item><el-descriptions-item label="重建 profile">{{ selectedAsset.reconstructProfile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="坐标系 / 单位">{{ selectedAsset.coordinateSystem }} / {{ selectedAsset.unit }}</el-descriptions-item><el-descriptions-item label="场景 frame">{{ selectedAsset.sceneFrame || '-' }} → {{ selectedAsset.referenceFrame || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型 SHA-256" :span="2"><code class="full-hash">{{ selectedAsset.modelSha256 }}</code></el-descriptions-item><el-descriptions-item label="Metadata SHA-256" :span="2"><code class="full-hash">{{ selectedAsset.metadataSha256 }}</code></el-descriptions-item>
          <el-descriptions-item label="审核意见" :span="2">{{ selectedAsset.reviewComment || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="file-actions"><el-button :disabled="!selectedAsset.filesReady" @click="downloadMetadata(selectedAsset)">下载 metadata</el-button><el-button :disabled="!selectedAsset.filesReady" @click="downloadModel(selectedAsset)">下载原始 PLY</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PointCloudViewer from '@/components/PointCloudViewer.vue'
import { resourcesApi } from '@/api/resources'
import { subscribeTopic } from '@/api/realtime'
import { usePermission } from '@/composables/usePermission'
import { useRobotStore } from '@/stores/robot'
import { useSiteStore } from '@/stores/site'
import type { SceneAsset, SceneAssetStatus } from '@/types/sceneAsset'
import { formatSceneFileSize, shortSceneHash } from '@/utils/sceneAsset'

const statusOptions = [{ label: '待审核', value: 'PENDING_REVIEW' }, { label: '已通过', value: 'AVAILABLE' }, { label: '已驳回', value: 'REJECTED' }]
const siteStore = useSiteStore(); const robotStore = useRobotStore(); const { can } = usePermission()
const assets = ref<SceneAsset[]>([]); const statusFilter = ref<SceneAssetStatus>('PENDING_REVIEW'); const siteFilter = ref('')
const loading = ref(false); const loadError = ref(''); const reviewingId = ref('')
const detailVisible = ref(false); const selectedAsset = ref<SceneAsset>(); const previewBlob = ref<Blob | null>(null); const previewLoading = ref(false)
let unsubscribe: (() => void) | undefined
const compactPoints = computed(() => new Intl.NumberFormat('zh-CN', { notation: 'compact' }).format(assets.value.reduce((sum, item) => sum + item.pointCount, 0)))

async function refresh() { loading.value = true; loadError.value = ''; try { assets.value = await resourcesApi.listSceneAssets({ source: 'ROBOT', status: statusFilter.value, siteId: siteFilter.value || undefined, assetKind: 'POINT_CLOUD' }) } catch (error) { assets.value = []; loadError.value = message(error, '三维资产加载失败') } finally { loading.value = false } }
function openDetail(asset: SceneAsset) { selectedAsset.value = asset; previewBlob.value = null; detailVisible.value = true }
async function loadPreview(asset: SceneAsset) { if (asset.fileSize > 200 * 1024 * 1024 && !asset.previewReady) { try { await ElMessageBox.confirm(`当前将加载 ${formatSceneFileSize(asset.fileSize)} 原始点云，可能占用较多内存。是否继续？`, '大文件预览确认', { type: 'warning' }) } catch { return } } previewLoading.value = true; try { previewBlob.value = await resourcesApi.getSceneAssetPreview(asset.id) } catch (error) { ElMessage.error(message(error, '点云预览加载失败')) } finally { previewLoading.value = false } }
function clearPreview() { previewBlob.value = null; selectedAsset.value = undefined }
async function approve(asset: SceneAsset) { try { await ElMessageBox.confirm('通过后资产可用于三维展示，但不会自动关联导航地图或路线。确认通过？', '审核通过确认', { type: 'warning' }) } catch { return } await submitReview(asset, 'APPROVE', '人工审核通过') }
async function reject(asset: SceneAsset) { try { const result = await ElMessageBox.prompt('请填写可复核的驳回原因', '驳回三维资产', { inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '驳回原因不能为空' }); await submitReview(asset, 'REJECT', result.value.trim()) } catch { /* 用户取消 */ } }
async function submitReview(asset: SceneAsset, action: 'APPROVE' | 'REJECT', comment: string) { reviewingId.value = asset.id; try { await resourcesApi.reviewSceneAsset(asset.id, { action, comment }); ElMessage.success(action === 'APPROVE' ? '三维资产已通过审核' : '三维资产已驳回'); detailVisible.value = false; await refresh() } catch (error) { ElMessage.error(message(error, '审核提交失败')) } finally { reviewingId.value = '' } }
async function downloadMetadata(asset: SceneAsset) { await download(resourcesApi.getSceneAssetMetadata(asset.id), `${asset.id}-metadata.json`) }
async function downloadModel(asset: SceneAsset) { await download(resourcesApi.getSceneAssetModel(asset.id), asset.originalName) }
async function download(promise: Promise<Blob>, filename: string) { try { const blob = await promise; const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url) } catch (error) { ElMessage.error(message(error, '文件下载失败')) } }
function siteName(id: string) { return siteStore.getSiteById(id)?.name ?? id }
function robotName(id?: string | null) { return id ? robotStore.getRobotById(id)?.name ?? id : '-' }
function formatPoints(value: number) { return new Intl.NumberFormat('zh-CN').format(value) }
function formatTime(value?: string | null) { if (!value) return '-'; const date = new Date(value); return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false }) }
function statusLabel(status: SceneAssetStatus) { return ({ PROCESSING: '处理中', PENDING_REVIEW: '待审核', AVAILABLE: '已通过', REJECTED: '已驳回', FAILED: '失败', DELETED: '已删除' })[status] }
function statusType(status: SceneAssetStatus) { return ({ PROCESSING: 'info', PENDING_REVIEW: 'warning', AVAILABLE: 'success', REJECTED: 'danger', FAILED: 'danger', DELETED: 'info' } as const)[status] }
function message(error: unknown, fallback: string) { return error instanceof Error ? error.message : fallback }
watch([statusFilter, siteFilter], () => void refresh())
onMounted(() => { void refresh(); unsubscribe = subscribeTopic('/topic/scene-assets', () => void refresh()) })
onUnmounted(() => unsubscribe?.())
</script>

<style scoped>
.scene-review-page { --ink: #163844; --line: #d6e3e5; }.scene-hero { margin-bottom: 18px; display: grid; grid-template-columns: 1.8fr .55fr .55fr .75fr; color: #e5f5f3; border: 1px solid #29616a; background: linear-gradient(105deg, #0b222a, #12434a 64%, #0d3037); box-shadow: 0 14px 30px rgb(8 47 53 / 16%); }.scene-hero > * { min-height: 116px; margin: 0; padding: 20px 24px; display: flex; flex-direction: column; justify-content: center; border-right: 1px solid rgb(220 255 251 / 13%); }.scene-hero span { color: #5fc5c4; font-size: 9px; font-weight: 800; letter-spacing: .17em; }.scene-hero h2 { margin: 6px 0 4px; font-size: 21px; letter-spacing: .03em; }.scene-hero p { margin: 0; color: #9bbcc0; font-size: 12px; }.scene-hero dt { color: #7fe0bd; font: 700 29px ui-monospace, monospace; }.scene-hero dd { margin: 5px 0 0; color: #91b0b5; font-size: 11px; }.scene-hero aside { border: 0; color: #72999f; font: 10px/1.8 ui-monospace, monospace; letter-spacing: .08em; }.scene-hero aside i { width: 35px; height: 3px; margin-bottom: 10px; background: #e8af56; }.scene-hero aside b { color: #bad2d4; }.ledger-card { border-color: var(--line); }.ledger-heading { display: flex; justify-content: space-between; align-items: center; gap: 18px; }.ledger-heading strong, .ledger-heading small { display: block; }.ledger-heading strong { color: var(--ink); }.ledger-heading small { margin-top: 4px; color: #7d9297; }.filters { display: flex; gap: 10px; }.filters .el-select { width: 180px; }.load-alert { margin-bottom: 14px; }.stack { display: grid; gap: 4px; }.stack strong { color: #264753; font-weight: 650; }.stack span { color: #819298; font-size: 11px; }.identity strong, .mono, code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }.mono strong, code { font-size: 11px; }.state { display: flex; flex-wrap: wrap; gap: 5px; }.drawer-title { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }.drawer-title span, .drawer-title strong { display: block; }.drawer-title span { color: #55aab3; font-size: 9px; letter-spacing: .16em; }.drawer-title strong { margin-top: 4px; color: var(--ink); font: 700 17px ui-monospace, monospace; }.fact-grid { margin-bottom: 16px; display: grid; grid-template-columns: repeat(4, 1fr); border: 1px solid var(--line); }.fact-grid article { padding: 13px 15px; display: grid; gap: 4px; border-right: 1px solid var(--line); }.fact-grid article:last-child { border: 0; }.fact-grid span, .fact-grid small { color: #7e9298; font-size: 10px; }.fact-grid b { color: #244854; font-size: 14px; overflow: hidden; text-overflow: ellipsis; }.preview-block { margin-bottom: 16px; }.preview-head { padding: 11px 14px; display: flex; justify-content: space-between; align-items: center; gap: 12px; border: 1px solid #234c55; border-bottom: 0; background: #f1f7f6; }.preview-head strong, .preview-head span { display: block; }.preview-head strong { color: var(--ink); }.preview-head span { margin-top: 3px; color: #718b91; font-size: 10px; }.details { margin-top: 16px; }.full-hash { word-break: break-all; }.file-actions { margin-top: 16px; display: flex; gap: 8px; }
@media (max-width: 900px) { .scene-hero { grid-template-columns: 1fr 1fr; }.scene-hero > div { grid-column: 1 / -1; }.scene-hero aside { display: none; }.ledger-heading { align-items: flex-start; flex-direction: column; }.filters { width: 100%; }.filters .el-select { flex: 1; }.fact-grid { grid-template-columns: 1fr 1fr; }.fact-grid article:nth-child(2) { border-right: 0; }.fact-grid article:nth-child(-n+2) { border-bottom: 1px solid var(--line); } }
</style>
