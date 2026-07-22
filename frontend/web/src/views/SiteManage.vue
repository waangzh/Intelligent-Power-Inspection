<template>
  <div class="site-page">
    <PageHeader title="站点与区域管理" description="变电站站点信息与区域划分" :breadcrumbs="[{ label: '巡检业务' }, { label: '站点管理' }]">
      <template #actions>
        <el-button v-if="can('site:edit')" type="primary" @click="openSiteDialog()">
          <el-icon><Plus /></el-icon>
          新建站点
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="workspace-card">
      <div class="site-workspace">
        <aside class="site-nav">
          <el-input
            v-model="siteKeyword"
            size="small"
            placeholder="搜索站点"
            clearable
            class="nav-search"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <div class="nav-list">
            <button
              v-for="site in filteredSites"
              :key="site.id"
              type="button"
              class="nav-item"
              :class="{ active: currentSite?.id === site.id }"
              @click="selectSite(site)"
            >
              <span class="nav-dot" :class="mapUploaded(site) ? 'ok' : 'idle'" />
              <span class="nav-name">{{ site.name }}</span>
            </button>
            <div v-if="!filteredSites.length" class="nav-empty">无匹配站点</div>
          </div>
          <ListPagination :total="siteStore.siteTotal" :page="sitePage" @change="loadSitePage" />
        </aside>

        <main class="site-main">
          <template v-if="currentSite">
            <div class="site-toolbar">
              <div class="toolbar-info">
                <h3>{{ currentSite.name }}</h3>
                <p>{{ currentSite.address || '未填写地址' }}</p>
              </div>
              <div class="toolbar-tags">
                <el-tag size="small" effect="light" :type="mapUploaded(currentSite) ? 'success' : 'info'">
                  {{ mapUploaded(currentSite) ? '已建图' : '待上传' }}
                </el-tag>
                <span class="coord">{{ currentSite.center.lat.toFixed(4) }}, {{ currentSite.center.lng.toFixed(4) }}</span>
              </div>
              <div v-if="can('site:edit')" class="toolbar-actions">
                <el-button plain size="small" class="action-btn action-detail" @click="openSiteDialog(currentSite)">编辑</el-button>
                <el-button plain size="small" class="action-btn action-danger" @click="removeSite(currentSite.id)">删除</el-button>
                <el-button type="primary" size="small" @click="openAreaDialog()">
                  <el-icon><Plus /></el-icon>
                  添加区域
                </el-button>
              </div>
            </div>

            <div class="map-shell">
              <Map2D :center="currentSite.center" :fallback-center="currentSite.center" :areas="areas" />
            </div>

            <div class="area-section">
              <div class="area-section-head">
                <span class="table-title">区域划分</span>
                <span class="record-count">{{ siteStore.areaTotal }} 个区域</span>
              </div>
              <el-table :data="areas" size="small">
                <el-table-column prop="name" label="区域名称" min-width="140" />
                <el-table-column label="顶点数" width="80" align="center">
                  <template #default="{ row }">{{ row.polygon.length }}</template>
                </el-table-column>
                <el-table-column label="操作" width="90" class-name="actions-col">
                  <template #default="{ row }">
                    <div class="row-actions">
                      <el-button
                        v-if="can('site:edit')"
                        plain
                        size="small"
                        class="action-btn action-danger"
                        @click="siteStore.removeArea(row.id)"
                      >删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <ListPagination :total="siteStore.areaTotal" :page="areaPage" @change="loadAreaPage" />
            </div>
          </template>
          <el-empty v-else description="请选择左侧站点" :image-size="80" />
        </main>
      </div>
    </el-card>

    <el-dialog
      v-model="siteDialogVisible"
      :title="editingSite ? '编辑站点' : '新建站点'"
      width="560px"
      class="site-dialog"
      :close-on-click-modal="false"
    >
      <div class="dialog-intro">
        <strong>站点基础信息</strong>
        <span>中心坐标用于地图定位与区域初始化，保存前请确认经纬度顺序。</span>
      </div>

      <el-form :model="siteForm" label-position="top" class="site-form">
        <div class="form-grid">
          <el-form-item label="站点名称" required class="form-field-full">
            <el-input
              v-model="siteForm.name"
              maxlength="80"
              show-word-limit
              clearable
              placeholder="例如：城东 220kV 变电站"
            />
          </el-form-item>
          <el-form-item label="地址" class="form-field-full">
            <el-input
              v-model="siteForm.address"
              maxlength="160"
              clearable
              placeholder="填写省、市、区及详细位置"
            />
          </el-form-item>
          <el-form-item label="站点描述" class="form-field-full">
            <el-input
              v-model="siteForm.description"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="简要说明电压等级、设备区域或巡检范围"
            />
          </el-form-item>
        </div>

        <section class="coordinate-panel" :class="{ invalid: coordinateIssue }">
          <div class="coordinate-heading">
            <div>
              <strong>地理坐标</strong>
              <span>WGS-84 坐标系 · 不会自动交换经纬度</span>
            </div>
            <span class="coordinate-order">Lat / Lng</span>
          </div>
          <div class="coordinate-grid">
            <el-form-item label="中心纬度（Lat）" required>
              <el-input
                v-model.number="siteForm.center.lat"
                type="number"
                min="-90"
                max="90"
                step="0.000001"
                inputmode="decimal"
              >
                <template #suffix>°</template>
              </el-input>
              <span class="field-range">合法范围：-90 ～ 90</span>
            </el-form-item>
            <el-form-item label="中心经度（Lng）" required>
              <el-input
                v-model.number="siteForm.center.lng"
                type="number"
                min="-180"
                max="180"
                step="0.000001"
                inputmode="decimal"
              >
                <template #suffix>°</template>
              </el-input>
              <span class="field-range">合法范围：-180 ～ 180</span>
            </el-form-item>
          </div>
          <el-alert
            v-if="coordinateIssue"
            :title="coordinateIssue.title"
            :description="coordinateIssue.description"
            :type="coordinateIssue.type"
            :closable="false"
            show-icon
          />
          <p v-else class="coordinate-help">示例：杭州约为纬度 30.274100，经度 120.155100。</p>
        </section>

        <div class="map-status-row">
          <div>
            <strong>设备建图</strong>
            <span>{{ siteForm.deviceMapUploaded ? '设备端已推送地图，可用于后续路线规划。' : '状态由设备端上报，不能在此手动修改。' }}</span>
          </div>
          <el-tag :type="siteForm.deviceMapUploaded ? 'success' : 'info'" size="small" effect="light">
            {{ siteForm.deviceMapUploaded ? '已上传' : '等待上传' }}
          </el-tag>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="siteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="siteSaving" :disabled="!canSaveSite" @click="saveSite">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="areaDialogVisible" title="添加区域" width="480px">
      <el-form :model="areaForm" label-width="90px">
        <el-form-item label="区域名称" required>
          <el-input v-model="areaForm.name" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="演示版使用默认矩形区域，完整版可在地图上绘制多边形"
          style="margin-bottom: 12px"
        />
      </el-form>
      <template #footer>
        <el-button @click="areaDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveArea">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import { usePermission } from '@/composables/usePermission'
import { useSiteStore } from '@/stores/site'
import type { Site } from '@/types'
import {
  defaultGeoCenter,
  isSuspectedCoordinateSwap,
  isValidGeoCoordinate,
} from '@/utils/geoCoordinate'

const siteStore = useSiteStore()
const { can } = usePermission()
const currentSite = ref<Site | null>(siteStore.sites[0] ?? null)
const sitePage = ref(0)
const areaPage = ref(0)
const siteKeyword = ref('')

watch(
  () => siteStore.sites,
  (sites) => {
    if (!currentSite.value && sites.length > 0) {
      currentSite.value = sites[0]
      void siteStore.loadAreas(sites[0].id)
      return
    }
    if (currentSite.value && !sites.some((site) => site.id === currentSite.value?.id)) {
      currentSite.value = sites[0] ?? null
    }
  },
  { immediate: true },
)

const siteDialogVisible = ref(false)
const areaDialogVisible = ref(false)
const editingSite = ref<Site | null>(null)
const siteSaving = ref(false)

const siteForm = reactive({
  name: '',
  address: '',
  description: '',
  center: defaultGeoCenter(),
  deviceMapUploaded: false,
})

const areaForm = reactive({ name: '' })

const areas = computed(() =>
  currentSite.value ? siteStore.getAreasBySite(currentSite.value.id) : [],
)

const filteredSites = computed(() => {
  const q = siteKeyword.value.trim()
  if (!q) return siteStore.sites
  return siteStore.sites.filter(
    (site) => site.name.includes(q) || site.address.includes(q),
  )
})

const coordinateIssue = computed(() => {
  const center = siteForm.center
  if (isSuspectedCoordinateSwap(center)) {
    return {
      type: 'warning' as const,
      title: '坐标疑似填写反了',
      description: '纬度超过 90，而经度落在纬度范围内。请核对原始数据后手动更正，系统不会自动交换。',
    }
  }
  if (!isValidGeoCoordinate(center)) {
    const latitudeInvalid = !Number.isFinite(center.lat) || Math.abs(center.lat) > 90
    return {
      type: 'error' as const,
      title: '中心坐标超出合法范围',
      description: latitudeInvalid
        ? '中心纬度必须在 -90 到 90 之间。'
        : '中心经度必须在 -180 到 180 之间。',
    }
  }
  return null
})

const canSaveSite = computed(() => Boolean(siteForm.name.trim()) && !coordinateIssue.value)

function mapUploaded(site: Site) {
  return Boolean(site.deviceMapUploaded || site.lingbotMapId)
}

function selectSite(site: Site) {
  currentSite.value = site
  areaPage.value = 0
  void siteStore.loadAreas(site.id)
}

function loadSitePage(page: number) {
  sitePage.value = page
  void siteStore.loadSites({ page, size: 20 })
}

function loadAreaPage(page: number) {
  if (!currentSite.value) return
  areaPage.value = page
  void siteStore.loadAreas(currentSite.value.id, { page, size: 20 })
}

function openSiteDialog(site?: Site) {
  editingSite.value = site ?? null
  if (site) {
    Object.assign(siteForm, {
      name: site.name,
      address: site.address,
      description: site.description,
      center: { ...site.center },
      deviceMapUploaded: mapUploaded(site),
    })
  } else {
    Object.assign(siteForm, {
      name: '',
      address: '',
      description: '',
      center: defaultGeoCenter(),
      deviceMapUploaded: false,
    })
  }
  siteDialogVisible.value = true
}

async function saveSite() {
  if (!siteForm.name.trim()) {
    ElMessage.warning('请填写站点名称')
    return
  }
  if (coordinateIssue.value) {
    ElMessage.warning(coordinateIssue.value.description)
    return
  }

  const payload = {
    name: siteForm.name.trim(),
    address: siteForm.address.trim(),
    description: siteForm.description.trim(),
    center: { ...siteForm.center },
    deviceMapUploaded: siteForm.deviceMapUploaded,
  }

  siteSaving.value = true
  try {
    if (editingSite.value) {
      currentSite.value = await siteStore.updateSite(editingSite.value.id, payload)
      ElMessage.success('站点已更新')
    } else {
      currentSite.value = await siteStore.addSite(payload)
      ElMessage.success('站点已创建')
    }
    siteDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '站点保存失败，请稍后重试')
  } finally {
    siteSaving.value = false
  }
}

function removeSite(id: string) {
  ElMessageBox.confirm('删除站点将同时删除其区域数据，是否继续？', '确认', { type: 'warning' })
    .then(() => {
      siteStore.removeSite(id)
      if (currentSite.value?.id === id) currentSite.value = siteStore.sites[0] ?? null
      ElMessage.success('已删除')
    })
    .catch(() => {})
}

function openAreaDialog() {
  areaForm.name = ''
  areaDialogVisible.value = true
}

function saveArea() {
  if (!currentSite.value || !areaForm.name.trim()) {
    ElMessage.warning('请填写区域名称')
    return
  }
  const c = currentSite.value.center
  const d = 0.0004
  siteStore.addArea({
    siteId: currentSite.value.id,
    name: areaForm.name,
    polygon: [
      { lat: c.lat + d, lng: c.lng - d },
      { lat: c.lat + d, lng: c.lng + d },
      { lat: c.lat - d, lng: c.lng + d },
      { lat: c.lat - d, lng: c.lng - d },
    ],
  })
  areaDialogVisible.value = false
  ElMessage.success('区域已添加')
}
</script>

<style scoped>
.workspace-card :deep(.el-card__body) {
  padding: 0;
}

.site-workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: 520px;
}

.site-nav {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 12px;
  border-right: 1px solid var(--pi-border-soft);
  background: #fafbfc;
}

.nav-search {
  flex-shrink: 0;
}

.nav-list {
  flex: 1;
  min-height: 120px;
  max-height: 480px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.nav-item:hover {
  background: #eef2f8;
}

.nav-item.active {
  background: #e6f4ff;
}

.nav-item.active .nav-name {
  color: var(--pi-primary);
  font-weight: 700;
}

.nav-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.nav-dot.ok {
  background: #12b76a;
}

.nav-dot.idle {
  background: #c0c4cc;
}

.nav-name {
  font-size: 13px;
  color: var(--pi-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-empty {
  padding: 16px 8px;
  font-size: 12px;
  color: var(--pi-muted);
  text-align: center;
}

.site-nav :deep(.el-pagination) {
  margin-top: auto;
  justify-content: center;
  flex-wrap: wrap;
}

.site-main {
  padding: 16px 18px 18px;
  min-width: 0;
}

.site-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px 20px;
  margin-bottom: 14px;
}

.toolbar-info {
  flex: 1;
  min-width: 180px;
}

.toolbar-info h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--pi-text);
}

.toolbar-info p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--pi-muted);
}

.toolbar-tags {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.coord {
  font-size: 11px;
  color: var(--pi-muted);
  font-family: Consolas, 'Courier New', monospace;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.map-shell {
  height: 400px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--pi-border-soft);
}

.area-section {
  margin-top: 16px;
}

.area-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.dialog-intro {
  display: grid;
  gap: 4px;
  margin: -2px 0 18px;
  padding: 12px 14px;
  border-left: 3px solid var(--pi-primary);
  border-radius: 0 8px 8px 0;
  background: #f5f8fc;
}

.dialog-intro strong {
  color: var(--pi-text);
  font-size: 14px;
}

.dialog-intro span {
  color: var(--pi-muted);
  font-size: 12px;
  line-height: 1.55;
}

.site-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.site-form :deep(.el-form-item__label) {
  margin-bottom: 7px;
  color: #405674;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.3;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.form-field-full {
  grid-column: 1 / -1;
}

.coordinate-panel {
  margin-top: 2px;
  padding: 14px;
  border: 1px solid #dce6f3;
  border-radius: 10px;
  background: #f8fafd;
  transition: border-color 0.2s, background 0.2s;
}

.coordinate-panel.invalid {
  border-color: #e6a23c;
  background: #fffaf2;
}

.coordinate-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 13px;
}

.coordinate-heading div {
  display: grid;
  gap: 3px;
}

.coordinate-heading strong,
.map-status-row strong {
  color: var(--pi-text);
  font-size: 13px;
}

.coordinate-heading div span,
.map-status-row div span {
  color: var(--pi-muted);
  font-size: 11px;
  line-height: 1.5;
}

.coordinate-order {
  flex: none;
  padding: 3px 7px;
  border: 1px solid #d8e3f0;
  border-radius: 5px;
  background: #fff;
  color: #5d7391;
  font: 11px/1.2 Consolas, 'Courier New', monospace;
}

.coordinate-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.coordinate-grid :deep(.el-form-item) {
  margin-bottom: 10px;
}

.field-range {
  display: block;
  margin-top: 5px;
  color: #8190a5;
  font-size: 10px;
}

.coordinate-help {
  margin: 1px 0 0;
  color: #6d7f97;
  font-size: 11px;
  line-height: 1.5;
}

.coordinate-panel :deep(.el-alert) {
  margin-top: 3px;
  align-items: flex-start;
}

.coordinate-panel :deep(.el-alert__description) {
  line-height: 1.5;
}

.map-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 9px;
  background: #fff;
}

.map-status-row div {
  display: grid;
  gap: 3px;
}

@media (max-width: 640px) {
  .form-grid,
  .coordinate-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .site-workspace {
    grid-template-columns: 1fr;
  }

  .site-nav {
    border-right: none;
    border-bottom: 1px solid var(--pi-border-soft);
    max-height: none;
  }

  .nav-list {
    max-height: 160px;
  }

  .toolbar-actions {
    margin-left: 0;
    width: 100%;
  }

  .map-shell {
    height: 320px;
  }
}
</style>
