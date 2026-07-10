<template>
  <div class="route-plan">
    <PageHeader
      title="巡检路线规划"
      description="基于 ROS 建图（YAML/PGM）标注起点、巡检点与导航方向，导出 route.json"
      :breadcrumbs="[{ label: '巡检业务' }, { label: '巡检规划' }]"
    >
      <template #actions>
        <el-select v-model="selectedSiteId" placeholder="选择站点" style="width: 220px" @change="onSiteChange">
          <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button v-if="can('route:edit')" type="primary" :disabled="!selectedSiteId" :loading="creatingRoute" @click="createRoute">
          <el-icon><Plus /></el-icon>
          新建路线
        </el-button>
        <el-button v-if="can('route:edit') && currentRoute" type="success" :loading="savingRoute" @click="saveToPlatform">
          保存到平台
        </el-button>
        <el-button v-if="can('route:edit') && currentRoute" type="danger" plain :loading="deletingRoute" @click="deleteRoute">
          删除路线
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="5">
        <div class="route-list-panel">
          <div class="route-list-head">路线列表</div>
          <div v-if="siteRoutes.length" class="route-list-body">
            <button
              v-for="r in siteRoutes"
              :key="r.id"
              type="button"
              class="route-item"
              :class="{ active: selectedRouteId === r.id }"
              @click="selectRoute(r.id)"
            >
              <span class="route-name">{{ r.name }}</span>
              <span class="route-meta">{{ targetCount(r) }} 点</span>
            </button>
          </div>
          <div v-else class="empty-hint">暂无路线，请先新建</div>
        </div>
      </el-col>

      <el-col :span="19">
        <RosMapRouteEditor
          v-if="currentRoute"
          ref="editorRef"
          :key="currentRoute.id"
          :initial-json="currentRoute.executorJson ?? undefined"
          :default-route-id="currentRoute.id"
          :map-id="currentRoute.mapId"
          @change="onEditorChange"
          @map-files-change="onMapFilesChange"
        />
        <div v-else class="empty-panel">
          <div class="empty-hint">请选择或创建巡检路线</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourcesApi } from '@/api/resources'
import PageHeader from '@/components/PageHeader.vue'
import RosMapRouteEditor from '@/components/RosMapRouteEditor.vue'
import { usePermission } from '@/composables/usePermission'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import type { MapAsset, MapAssetFiles, Route } from '@/types'
import type { RouteExecutorDocument } from '@/types/routeExecutor'

const siteStore = useSiteStore()
const routeStore = useRouteStore()
const { can } = usePermission()

const selectedSiteId = ref(siteStore.sites[0]?.id ?? '')
const selectedRouteId = ref('')
const pendingDoc = ref<RouteExecutorDocument | null>(null)
const editorRef = ref<InstanceType<typeof RosMapRouteEditor> | null>(null)
const creatingRoute = ref(false)
const savingRoute = ref(false)
const deletingRoute = ref(false)
const pendingMapFiles = ref<MapAssetFiles | null>(null)

const siteRoutes = computed(() => routeStore.getRoutesBySite(selectedSiteId.value))
const currentRoute = computed(() => routeStore.getRouteById(selectedRouteId.value) ?? null)

watch(
  () => siteStore.sites.map((site) => site.id),
  (ids) => {
    if (ids.length > 0 && !ids.includes(selectedSiteId.value)) {
      selectedSiteId.value = ids[0]
    }
  },
  { immediate: true },
)

watch(
  siteRoutes,
  (routes) => {
    if (routes.length && !routes.some((route) => route.id === selectedRouteId.value)) {
      selectRoute(routes[0].id)
    } else if (!routes.length) {
      selectedRouteId.value = ''
      pendingDoc.value = null
      pendingMapFiles.value = null
    }
  },
  { immediate: true },
)

function targetCount(route: Route) {
  return route.executorJson?.targets?.length ?? route.checkpoints.length
}

function onSiteChange() {
  selectedRouteId.value = siteRoutes.value[0]?.id ?? ''
  pendingDoc.value = currentRoute.value?.executorJson ?? null
  pendingMapFiles.value = null
}

function selectRoute(id: string) {
  selectedRouteId.value = id
  pendingDoc.value = routeStore.getRouteById(id)?.executorJson ?? null
  pendingMapFiles.value = null
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

async function createRoute() {
  if (creatingRoute.value) return
  creatingRoute.value = true
  try {
    const route = await routeStore.createRoute(selectedSiteId.value, `巡检路线 ${siteRoutes.value.length + 1}`)
    selectRoute(route.id)
    pendingDoc.value = null
    ElMessage.success('路线已创建，请加载 YAML/PGM 地图并开始标注')
  } catch (error) {
    ElMessage.error(errorMessage(error, '路线创建失败'))
  } finally {
    creatingRoute.value = false
  }
}

function onEditorChange(doc: RouteExecutorDocument) {
  pendingDoc.value = doc
}

function onMapFilesChange(files: MapAssetFiles) {
  pendingMapFiles.value = files
}

async function saveToPlatform() {
  if (!currentRoute.value || !pendingDoc.value) {
    ElMessage.warning('请先在地图上标注路线')
    return
  }
  if (!currentRoute.value.mapId && !pendingMapFiles.value) {
    ElMessage.warning('请先导入完整的 YAML/PGM 地图')
    return
  }
  if (savingRoute.value) return
  savingRoute.value = true
  let uploadedAsset: MapAsset | null = null
  try {
    if (pendingMapFiles.value) {
      const form = new FormData()
      form.append('siteId', currentRoute.value.siteId)
      form.append('yaml', pendingMapFiles.value.yaml)
      form.append('pgm', pendingMapFiles.value.pgm)
      uploadedAsset = await resourcesApi.uploadMapAsset(form)
    }
    const mapId = uploadedAsset?.id ?? currentRoute.value.mapId
    if (!mapId) throw new Error('地图资产上传失败')
    await routeStore.saveExecutorRoute(currentRoute.value.id, pendingDoc.value, mapId)
    pendingMapFiles.value = null
    ElMessage.success('路线已保存到平台')
  } catch (error) {
    if (uploadedAsset) {
      try {
        await resourcesApi.removeMapAsset(uploadedAsset.id)
      } catch {
        // A late response failure may still leave the asset referenced by the route.
      }
    }
    ElMessage.error(errorMessage(error, '路线保存失败'))
  } finally {
    savingRoute.value = false
  }
}

async function deleteRoute() {
  if (!currentRoute.value) return
  try {
    await ElMessageBox.confirm('确定删除该路线？', '确认', { type: 'warning' })
  } catch {
    return
  }
  deletingRoute.value = true
  try {
    await routeStore.removeRoute(currentRoute.value.id)
    selectedRouteId.value = siteRoutes.value[0]?.id ?? ''
    pendingDoc.value = currentRoute.value?.executorJson ?? null
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error, '路线删除失败'))
  } finally {
    deletingRoute.value = false
  }
}
</script>

<style scoped>
.route-list-panel {
  min-height: 640px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.route-list-head {
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.route-list-body {
  padding: 8px;
}

.route-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 4px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.route-item:hover {
  background: #f5f7fa;
}

.route-item.active {
  background: #ecfdf5;
  box-shadow: inset 3px 0 0 #0f766e;
}

.route-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-meta {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.empty-panel {
  min-height: 640px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.empty-hint {
  padding: 48px 16px;
  text-align: center;
  color: #909399;
}
</style>
