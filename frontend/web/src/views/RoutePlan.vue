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
        <el-button v-if="can('route:edit')" type="primary" :disabled="!selectedSiteId" @click="createRoute">
          <el-icon><Plus /></el-icon>
          新建路线
        </el-button>
        <el-button v-if="can('route:edit') && currentRoute" type="success" @click="saveToPlatform">
          保存到平台
        </el-button>
        <el-button v-if="can('route:edit') && currentRoute" type="danger" plain @click="deleteRoute">
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
          @change="onEditorChange"
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
import PageHeader from '@/components/PageHeader.vue'
import RosMapRouteEditor from '@/components/RosMapRouteEditor.vue'
import { usePermission } from '@/composables/usePermission'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import type { Route } from '@/types'
import type { RouteExecutorDocument } from '@/types/routeExecutor'

const siteStore = useSiteStore()
const routeStore = useRouteStore()
const { can } = usePermission()

const selectedSiteId = ref(siteStore.sites[0]?.id ?? '')
const selectedRouteId = ref('')
const pendingDoc = ref<RouteExecutorDocument | null>(null)
const editorRef = ref<InstanceType<typeof RosMapRouteEditor> | null>(null)

const siteRoutes = computed(() => routeStore.getRoutesBySite(selectedSiteId.value))
const currentRoute = computed(() => routeStore.getRouteById(selectedRouteId.value) ?? null)

watch(
  siteRoutes,
  (routes) => {
    if (!selectedRouteId.value && routes.length) {
      selectRoute(routes[0].id)
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
}

function selectRoute(id: string) {
  selectedRouteId.value = id
  pendingDoc.value = routeStore.getRouteById(id)?.executorJson ?? null
}

function createRoute() {
  const route = routeStore.createRoute(selectedSiteId.value, `巡检路线 ${siteRoutes.value.length + 1}`)
  selectRoute(route.id)
  pendingDoc.value = null
  ElMessage.success('路线已创建，请加载 YAML/PGM 地图并开始标注')
}

function onEditorChange(doc: RouteExecutorDocument) {
  pendingDoc.value = doc
}

function saveToPlatform() {
  if (!currentRoute.value || !pendingDoc.value) {
    ElMessage.warning('请先在地图上标注路线')
    return
  }
  routeStore.saveExecutorRoute(currentRoute.value.id, pendingDoc.value)
  ElMessage.success('路线已保存到平台')
}

function deleteRoute() {
  if (!currentRoute.value) return
  ElMessageBox.confirm('确定删除该路线？', '确认', { type: 'warning' })
    .then(() => {
      routeStore.removeRoute(currentRoute.value!.id)
      selectedRouteId.value = siteRoutes.value[0]?.id ?? ''
      pendingDoc.value = currentRoute.value?.executorJson ?? null
      ElMessage.success('已删除')
    })
    .catch(() => {})
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
