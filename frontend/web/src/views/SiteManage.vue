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

    <el-dialog v-model="siteDialogVisible" :title="editingSite ? '编辑站点' : '新建站点'" width="480px">
      <el-form :model="siteForm" label-width="90px">
        <el-form-item label="站点名称" required>
          <el-input v-model="siteForm.name" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="siteForm.address" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="siteForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="中心纬度">
          <el-input-number v-model="siteForm.center.lat" :step="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="中心经度">
          <el-input-number v-model="siteForm.center.lng" :step="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设备建图">
          <el-tag :type="siteForm.deviceMapUploaded ? 'success' : 'info'" size="small">
            {{ siteForm.deviceMapUploaded ? '设备端已上传地图' : '等待设备端推送地图数据' }}
          </el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="siteDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSite">保存</el-button>
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

const siteForm = reactive({
  name: '',
  address: '',
  description: '',
  center: { lat: 30.2741, lng: 120.1551 },
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
      center: { lat: 30.2741, lng: 120.1551 },
      deviceMapUploaded: false,
    })
  }
  siteDialogVisible.value = true
}

function saveSite() {
  if (!siteForm.name.trim()) {
    ElMessage.warning('请填写站点名称')
    return
  }
  if (editingSite.value) {
    siteStore.updateSite(editingSite.value.id, {
      name: siteForm.name,
      address: siteForm.address,
      description: siteForm.description,
      center: { ...siteForm.center },
      deviceMapUploaded: siteForm.deviceMapUploaded,
    })
    ElMessage.success('站点已更新')
  } else {
    const site = siteStore.addSite({
      name: siteForm.name,
      address: siteForm.address,
      description: siteForm.description,
      center: { ...siteForm.center },
      deviceMapUploaded: siteForm.deviceMapUploaded,
    })
    currentSite.value = site
    ElMessage.success('站点已创建')
  }
  siteDialogVisible.value = false
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
