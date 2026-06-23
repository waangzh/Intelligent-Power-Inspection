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
        <el-card shadow="never" class="route-list-card">
          <template #header>路线列表</template>
          <el-menu :default-active="selectedRouteId" @select="selectRoute">
            <el-menu-item v-for="r in siteRoutes" :key="r.id" :index="r.id">
              <span>{{ r.name }}</span>
              <el-tag size="small" style="margin-left: 8px">{{ targetCount(r) }} 点</el-tag>
            </el-menu-item>
          </el-menu>
          <div v-if="!siteRoutes.length" class="empty-hint">暂无路线，请先新建</div>
        </el-card>
      </el-col>

      <el-col :span="19">
        <RosMapRouteEditor
          v-if="currentRoute"
          :key="currentRoute.id"
          :initial-json="currentRoute.executorJson ?? undefined"
          :default-route-id="currentRoute.id"
          @change="onEditorChange"
        />
        <el-card v-else shadow="never">
          <div class="empty-hint">请选择或创建巡检路线</div>
        </el-card>
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
.route-list-card {
  min-height: 640px;
}

.empty-hint {
  padding: 48px 16px;
  text-align: center;
  color: #909399;
}
</style>
