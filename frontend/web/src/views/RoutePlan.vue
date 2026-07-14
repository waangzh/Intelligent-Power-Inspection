<template>
  <div class="route-plan">
    <PageHeader
      title="巡检路线规划"
      description="基于 ROS 建图（YAML/PGM）标注起点、巡检点与导航方向，导出 route.json"
      :breadcrumbs="[{ label: '巡检业务' }, { label: '巡检规划' }]"
    >
      <template #actions>
        <el-select v-model="selectedSiteId" placeholder="选择站点" style="width: 220px" :disabled="savingRoute" @change="onSiteChange">
          <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button v-if="can('route:edit')" type="primary" :disabled="!selectedSiteId || savingRoute" :loading="creatingRoute" @click="createRoute">
          <el-icon><Plus /></el-icon>
          新建路线
        </el-button>
        <el-tag v-if="currentRoute" class="draft-status" :type="draftSaveTagType" effect="plain">
          {{ draftSaveLabel }}
        </el-tag>
        <el-button v-if="can('route:edit') && currentRoute" type="success" :loading="savingRoute" @click="saveDraft">
          保存草稿
        </el-button>
        <el-button v-if="can('route:edit') && currentRoute" type="warning" plain :title="publishBlockReason" :disabled="!canCreateRevision" :loading="creatingRevision" @click="createRevision">
          创建路线修订
        </el-button>
        <el-button v-if="can('route:edit') && currentRoute" type="danger" plain :disabled="savingRoute" :loading="deletingRoute" @click="deleteRoute">
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
              :disabled="savingRoute || deletingRoute"
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
          :initial-json="editorInitialJson ?? undefined"
          :default-route-id="currentRoute.id"
          :default-route-name="currentRoute.name"
          :map-id="draftState?.mapAssetId ?? currentRoute.mapId"
          @change="onEditorChange"
          @map-files-change="onMapFilesChange"
        />
        <el-card v-if="draftValidation" class="validation-panel" shadow="never">
          <template #header>
            <div class="validation-header">
              <span>发布前检查</span>
              <el-tag :type="draftValidation.publishable ? 'success' : draftValidation.valid ? 'warning' : 'danger'" effect="light">
                {{ draftValidation.publishable ? '允许发布' : draftValidation.valid ? '不可发布' : '存在错误' }}
              </el-tag>
            </div>
            <small v-if="draftValidation.checkedAt" class="checked-at">最近校验：{{ draftValidation.checkedAt }}</small>
          </template>
          <el-empty v-if="!draftValidation.issues.length" description="未发现校验问题" :image-size="48" />
          <ul v-else class="validation-issues">
            <li v-for="issue in draftValidation.issues" :key="`${issue.severity}:${issue.code}:${issue.jsonPointer}`">
              <el-tag size="small" :type="issue.severity === 'ERROR' ? 'danger' : 'warning'">{{ issue.severity }}</el-tag>
              <code>{{ issue.code }}</code>
              <code>{{ issue.jsonPointer || '/' }}</code>
              <span>{{ issue.message }}</span>
            </li>
          </ul>
        </el-card>
        <el-card v-if="currentRoute && can('task:dispatch')" class="deployment-panel" shadow="never">
          <template #header>
            <div class="deployment-header">
              <div><strong>部署到 Robot Bridge</strong><small>就绪仅表示 Bridge 已缓存路线和地图包，等待机器人通过心跳拉取；不代表机器人已安装或开始执行。</small></div>
              <el-tag effect="plain" type="info">{{ deployments.length }} 条部署记录</el-tag>
            </div>
          </template>
          <div class="deployment-controls">
            <el-select v-model="selectedRevisionId" placeholder="选择路线修订" :loading="deploymentLoading" class="deployment-select">
              <el-option v-for="revision in revisions" :key="revision.id" :label="`r${revision.revisionNo} · ${shortHash(revision.contentSha256)}`" :value="revision.id" />
            </el-select>
            <el-select v-model="selectedDeploymentRobotId" placeholder="选择符合条件的机器人" :loading="deploymentLoading" class="deployment-select">
              <el-option v-for="robot in deploymentRobotOptions" :key="robot.id" :label="`${robot.name} · ${robot.eligibility.reason}`" :value="robot.id" :disabled="!robot.eligibility.eligible" />
            </el-select>
            <el-button type="primary" :loading="creatingDeployment" :disabled="!canCreateDeployment" @click="createDeployment">发起部署</el-button>
          </div>
          <el-alert v-if="selectedDeploymentRobot && !selectedDeploymentRobot.eligibility.eligible" class="deployment-alert" type="warning" :closable="false" :title="selectedDeploymentRobot.eligibility.reason" />
          <el-alert v-else-if="!deploymentRobotOptions.some((robot) => robot.eligibility.eligible)" class="deployment-alert" type="warning" :closable="false" title="当前站点没有满足“已注册、Bridge 已配置、在线且可达”的机器人" />
          <el-alert v-if="deploymentError" class="deployment-alert" type="error" :closable="false" :title="deploymentError" />
          <el-empty v-if="!deployments.length && !deploymentLoading" description="尚无路线部署记录" :image-size="48" />
          <el-table v-else :data="deployments" v-loading="deploymentLoading" size="small" class="deployment-table">
            <el-table-column label="修订 / 机器人" min-width="180">
              <template #default="{ row }"><div class="deployment-identity"><strong>{{ revisionLabel(row.routeRevisionId) }}</strong><span>{{ robotName(row.robotId) }}</span></div></template>
            </el-table-column>
            <el-table-column label="状态" width="136"><template #default="{ row }"><el-tag size="small" :type="deploymentStateType(row.state)">{{ deploymentStateLabel(row.state) }}</el-tag></template></el-table-column>
            <el-table-column label="最近尝试" min-width="164"><template #default="{ row }">{{ formatTime(row.lastAttemptAt) }}<small v-if="row.attemptCount">第 {{ row.attemptCount }} 次</small></template></el-table-column>
            <el-table-column label="路线 / 地图哈希" min-width="205"><template #default="{ row }"><code>{{ shortHash(row.routeContentSha256) }}</code><code>{{ shortHash(row.mapImageSha256) }}</code></template></el-table-column>
            <el-table-column label="错误摘要" min-width="210"><template #default="{ row }"><span v-if="row.errorCode" class="deployment-error">{{ row.errorCode }} · {{ row.errorMessage }}</span><span v-else>-</span></template></el-table-column>
          </el-table>
        </el-card>
        <div v-if="!draftValidation" class="empty-panel">
          <div class="empty-hint">请选择或创建巡检路线</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave } from 'vue-router'
import { resourcesApi } from '@/api/resources'
import PageHeader from '@/components/PageHeader.vue'
import RosMapRouteEditor from '@/components/RosMapRouteEditor.vue'
import { usePermission } from '@/composables/usePermission'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import type { MapAsset, MapAssetUploadFiles, Route, RouteRevision } from '@/types'
import type { RouteDeployment } from '@/types/routeDeployment'
import type { PersistedRouteDraftReport, RouteDraftValidationReport, RouteExecutorDocument } from '@/types/routeExecutor'
import type { RobotHeartbeatStatus } from '@/types/robotHeartbeat'
import { applySavedRouteDraft, keepLocalDraftAfterSaveFailure, restoreRouteDraft, routePublishBlockReason, serializeRouteDocument, type DraftSaveState } from '@/utils/route/draftPersistence'
import { DEPLOYMENT_STATE_LABELS, deploymentEligibility, deploymentStateType, shortHash, shouldPollDeployment } from '@/utils/routeDeployment'
import { useRobotStore } from '@/stores/robot'

const siteStore = useSiteStore()
const routeStore = useRouteStore()
const { can } = usePermission()
const robotStore = useRobotStore()

const selectedSiteId = ref(siteStore.sites[0]?.id ?? '')
const selectedRouteId = ref('')
const pendingDoc = shallowRef<RouteExecutorDocument | null>(null)
const editorInitialJson = shallowRef<RouteExecutorDocument | null>(null)
const editorRef = ref<InstanceType<typeof RosMapRouteEditor> | null>(null)
const creatingRoute = ref(false)
const savingRoute = ref(false)
const deletingRoute = ref(false)
const creatingRevision = ref(false)
const pendingMapFiles = ref<MapAssetUploadFiles | null>(null)
const draftValidation = shallowRef<(RouteDraftValidationReport | PersistedRouteDraftReport) | null>(null)
const draftState = shallowRef<PersistedRouteDraftReport | null>(null)
const draftSaveState = ref<DraftSaveState>('unsaved')
const hasUnsavedChanges = ref(false)
const persistedDocument = shallowRef<string | null>(null)
const switchingSite = ref(false)
const revisions = ref<RouteRevision[]>([])
const deployments = ref<RouteDeployment[]>([])
const deploymentRobotStatuses = ref<RobotHeartbeatStatus[]>([])
const selectedRevisionId = ref('')
const selectedDeploymentRobotId = ref('')
const deploymentLoading = ref(false)
const creatingDeployment = ref(false)
const deploymentError = ref('')

const siteRoutes = computed<Route[]>(() => routeStore.getRoutesBySite(selectedSiteId.value))
const currentRoute = computed<Route | null>(() => routeStore.getRouteById(selectedRouteId.value) ?? null)
const draftSaveLabel = computed(() => ({ unsaved: '未保存', saving: '保存中', saved: '已保存', failed: '保存失败' })[draftSaveState.value])
const draftSaveTagType = computed(() => ({ unsaved: 'warning', saving: 'info', saved: 'success', failed: 'danger' })[draftSaveState.value])
const publishBlockReason = computed(() => routePublishBlockReason(Boolean(currentRoute.value), hasUnsavedChanges.value, draftState.value))
const canCreateRevision = computed(() => Boolean(can('route:edit') && !publishBlockReason.value))
const deploymentRobotOptions = computed(() => deploymentRobotStatuses.value.map((status) => {
  const robot = robotStore.getRobotById(status.robotId)
  return {
    id: status.robotId,
    name: robot?.name || status.displayName || status.robotId,
    eligibility: deploymentEligibility(status, Boolean(robot && robot.siteId === currentRoute.value?.siteId)),
  }
}))
const selectedDeploymentRobot = computed(() => deploymentRobotOptions.value.find((robot) => robot.id === selectedDeploymentRobotId.value) ?? null)
const canCreateDeployment = computed(() => Boolean(
  can('task:dispatch') && selectedRevisionId.value && selectedDeploymentRobot.value?.eligibility.eligible && !creatingDeployment.value,
))

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
      if (!switchingSite.value) void selectRoute(routes[0].id)
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

async function onSiteChange() {
  const previousSiteId = currentRoute.value?.siteId ?? selectedSiteId.value
  switchingSite.value = true
  try {
    if (!(await confirmDiscardChanges())) {
      selectedSiteId.value = previousSiteId
      return
    }
    selectedRouteId.value = siteRoutes.value[0]?.id ?? ''
    await loadDraft(selectedRouteId.value)
  } finally {
    switchingSite.value = false
  }
}

async function selectRoute(id: string) {
  if (id === selectedRouteId.value) return
  if (!(await confirmDiscardChanges())) return
  selectedRouteId.value = id
  await loadDraft(id)
}

async function loadDraft(routeId: string) {
  pendingMapFiles.value = null
  pendingDoc.value = null
  editorInitialJson.value = null
  draftValidation.value = null
  draftState.value = null
  persistedDocument.value = null
  hasUnsavedChanges.value = false
  draftSaveState.value = 'unsaved'
  revisions.value = []
  deployments.value = []
  selectedRevisionId.value = ''
  selectedDeploymentRobotId.value = ''
  deploymentError.value = ''
  if (!routeId) {
    return
  }
  try {
    const state = await resourcesApi.getRouteDraft(routeId)
    if (routeId !== selectedRouteId.value) return
    draftState.value = state
    draftValidation.value = state
    const restored = restoreRouteDraft(state, routeStore.getRouteById(routeId)?.executorJson ?? null)
    pendingDoc.value = restored.document
    editorInitialJson.value = restored.document
    persistedDocument.value = restored.persistedDocument
    draftSaveState.value = restored.state
    hasUnsavedChanges.value = false
    void loadDeploymentData(routeId)
  } catch (error) {
    if (routeId !== selectedRouteId.value) return
    pendingDoc.value = routeStore.getRouteById(routeId)?.executorJson ?? null
    editorInitialJson.value = pendingDoc.value
    draftSaveState.value = 'failed'
    ElMessage.error(errorMessage(error, '草稿加载失败'))
    void loadDeploymentData(routeId)
  }
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

async function createRoute() {
  if (creatingRoute.value || savingRoute.value) return
  if (!(await confirmDiscardChanges())) return
  creatingRoute.value = true
  try {
    const route = await routeStore.createRoute(selectedSiteId.value, `巡检路线 ${siteRoutes.value.length + 1}`)
    selectedRouteId.value = route.id
    await loadDraft(route.id)
    ElMessage.success('路线已创建，请加载 YAML/PGM 地图并开始标注')
  } catch (error) {
    ElMessage.error(errorMessage(error, '路线创建失败'))
  } finally {
    creatingRoute.value = false
  }
}

function onEditorChange(doc: RouteExecutorDocument) {
  pendingDoc.value = doc
  draftValidation.value = null
  hasUnsavedChanges.value = serializeRouteDocument(doc) !== persistedDocument.value
  if (hasUnsavedChanges.value) draftSaveState.value = 'unsaved'
}

function onMapFilesChange(files: MapAssetUploadFiles) {
  pendingMapFiles.value = files
  hasUnsavedChanges.value = true
  draftSaveState.value = 'unsaved'
}

async function saveDraft() {
  const route = currentRoute.value
  const document = pendingDoc.value
  if (!route || !document) {
    ElMessage.warning('请先在地图上标注路线')
    return
  }
  if (!route.mapId && !pendingMapFiles.value && !draftState.value?.mapAssetId) {
    ElMessage.warning('请先导入完整的 YAML/PGM 地图')
    return
  }
  if (savingRoute.value) return
  savingRoute.value = true
  draftSaveState.value = 'saving'
  let uploadedAsset: MapAsset | null = null
  try {
    if (pendingMapFiles.value) {
      const form = new FormData()
      form.append('siteId', route.siteId)
      form.append('yaml', pendingMapFiles.value.yaml)
      form.append('pgm', pendingMapFiles.value.pgm)
      uploadedAsset = await resourcesApi.uploadMapAsset(form)
    }
    const mapId = uploadedAsset?.id ?? draftState.value?.mapAssetId ?? route.mapId
    if (!mapId) throw new Error('地图资产上传失败')
    const saved = await resourcesApi.saveRouteDraft(
      route.id,
      document,
      draftState.value?.draft?.version,
      mapId,
    )
    const normalizedDocument = applySavedRouteDraft(saved)
    await routeStore.saveExecutorRoute(route.id, normalizedDocument, mapId)
    if (selectedRouteId.value !== route.id) return
    draftState.value = saved
    draftValidation.value = saved
    pendingDoc.value = normalizedDocument
    editorInitialJson.value = normalizedDocument
    persistedDocument.value = serializeRouteDocument(normalizedDocument)
    pendingMapFiles.value = null
    hasUnsavedChanges.value = false
    draftSaveState.value = 'saved'
    ElMessage[saved.publishable ? 'success' : 'warning'](saved.publishable ? '草稿已保存，可创建路线修订' : '草稿已保存，但存在 ERROR，暂不可发布')
  } catch (error) {
    draftSaveState.value = 'failed'
    pendingDoc.value = keepLocalDraftAfterSaveFailure(pendingDoc.value)
    if (uploadedAsset) {
      try {
        await resourcesApi.removeMapAsset(uploadedAsset.id)
      } catch {
        // 请求超时后资产可能已被草稿引用，交由后端引用保护处理。
      }
    }
    ElMessage.error(errorMessage(error, '草稿保存失败'))
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
    const nextRouteId = siteRoutes.value[0]?.id ?? ''
    selectedRouteId.value = nextRouteId
    await loadDraft(nextRouteId)
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error, '路线删除失败'))
  } finally {
    deletingRoute.value = false
  }
}

async function createRevision() {
  if (!currentRoute.value || creatingRevision.value) return
  if (!canCreateRevision.value) {
    ElMessage.warning(publishBlockReason.value || '草稿尚不可发布')
    return
  }
  creatingRevision.value = true
  try {
    const check = await resourcesApi.getRouteDraftCheck(currentRoute.value.id)
    draftState.value = check
    draftValidation.value = check
    if (!check.publishable) {
      ElMessage.error('发布前检查未通过，请处理 ERROR 或地图身份不一致的问题')
      return
    }
    const revision = await resourcesApi.createRouteRevision(currentRoute.value.id)
    await loadDeploymentData(currentRoute.value.id)
    selectedRevisionId.value = revision.id
    ElMessage.success(`已创建路线修订 r${revision.revisionNo}`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '创建路线修订失败'))
  } finally {
    creatingRevision.value = false
  }
}

async function loadDeploymentData(routeId: string) {
  deploymentLoading.value = true
  deploymentError.value = ''
  try {
    const [loadedRevisions, statusPage] = await Promise.all([
      resourcesApi.listRouteRevisions(routeId),
      resourcesApi.listRobotHeartbeatStatus({ size: 100, sort: 'displayName', direction: 'asc' }),
      robotStore.robots.length ? Promise.resolve() : robotStore.load(),
    ])
    if (routeId !== selectedRouteId.value) return
    revisions.value = loadedRevisions
    deploymentRobotStatuses.value = statusPage.items
    if (!loadedRevisions.some((revision) => revision.id === selectedRevisionId.value)) {
      selectedRevisionId.value = loadedRevisions[0]?.id ?? ''
    }
    const groups = await Promise.all(loadedRevisions.map((revision) => resourcesApi.listRouteDeployments(revision.id)))
    if (routeId !== selectedRouteId.value) return
    deployments.value = groups.flat().sort((left, right) => right.createdAt.localeCompare(left.createdAt))
  } catch (error) {
    if (routeId === selectedRouteId.value) deploymentError.value = errorMessage(error, '无法加载路线部署信息')
  } finally {
    if (routeId === selectedRouteId.value) deploymentLoading.value = false
  }
}

async function createDeployment() {
  const revisionId = selectedRevisionId.value
  const robot = selectedDeploymentRobot.value
  if (!revisionId || !robot?.eligibility.eligible || creatingDeployment.value) {
    ElMessage.warning(robot?.eligibility.reason || '请选择可部署的机器人和路线修订')
    return
  }
  creatingDeployment.value = true
  deploymentError.value = ''
  try {
    const idempotencyKey = `route-deploy:${revisionId}:${robot.id}:${Date.now()}:${Math.random().toString(36).slice(2, 10)}`
    const deployment = await resourcesApi.createRouteDeployment(revisionId, robot.id, idempotencyKey)
    deployments.value = [deployment, ...deployments.value.filter((item) => item.id !== deployment.id)]
    ElMessage.success('部署请求已提交，正在由后台同步到 Bridge')
  } catch (error) {
    deploymentError.value = errorMessage(error, '部署请求失败')
    ElMessage.error(deploymentError.value)
  } finally {
    creatingDeployment.value = false
  }
}

async function refreshDeploymentDetails() {
  if (!deployments.value.some((deployment) => shouldPollDeployment(deployment.state))) return
  try {
    const refreshed = await Promise.all(deployments.value.map((deployment) =>
      shouldPollDeployment(deployment.state) ? resourcesApi.getRouteDeployment(deployment.id) : Promise.resolve(deployment),
    ))
    deployments.value = refreshed.sort((left, right) => right.createdAt.localeCompare(left.createdAt))
  } catch (error) {
    deploymentError.value = errorMessage(error, '部署状态刷新失败')
  }
}

function deploymentStateLabel(state: RouteDeployment['state']) {
  return DEPLOYMENT_STATE_LABELS[state]
}

function revisionLabel(revisionId: string) {
  const revision = revisions.value.find((item) => item.id === revisionId)
  return revision ? `r${revision.revisionNo}` : revisionId
}

function robotName(robotId: string) {
  return robotStore.getRobotById(robotId)?.name || deploymentRobotStatuses.value.find((item) => item.robotId === robotId)?.displayName || robotId
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}

async function confirmDiscardChanges() {
  if (!hasUnsavedChanges.value) return true
  try {
    await ElMessageBox.confirm('当前路线有未保存的本地编辑，是否放弃？', '未保存的草稿', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value) return
  event.preventDefault()
  event.returnValue = ''
}

let deploymentPollTimer: number | undefined
onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  deploymentPollTimer = window.setInterval(() => void refreshDeploymentDetails(), 5000)
})
onUnmounted(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  if (deploymentPollTimer) window.clearInterval(deploymentPollTimer)
})
onBeforeRouteLeave(() => confirmDiscardChanges())
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

.validation-panel {
  margin-top: 16px;
}

.deployment-panel {
  margin-top: 16px;
}

.deployment-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.deployment-header strong,
.deployment-header small {
  display: block;
}

.deployment-header small {
  max-width: 680px;
  margin-top: 5px;
  color: #7a8895;
  font-size: 12px;
  line-height: 1.55;
}

.deployment-controls {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.deployment-select {
  width: min(290px, 42%);
}

.deployment-alert {
  margin-bottom: 12px;
}

.deployment-table code,
.deployment-identity span,
.deployment-table small {
  display: block;
  color: #7a8895;
  font-size: 12px;
}

.deployment-table code + code {
  margin-top: 4px;
}

.deployment-identity strong {
  color: #263a4a;
}

.deployment-error {
  color: #c45656;
  line-height: 1.45;
}

.validation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.draft-status {
  margin-right: 4px;
}

.checked-at {
  display: block;
  margin-top: 6px;
  color: #909399;
  font-weight: 400;
}

.validation-issues {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.validation-issues li {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 8px 10px;
  border-radius: 4px;
  background: #f8fafc;
  color: #303133;
}

.validation-issues code {
  color: #606266;
}

@media (max-width: 760px) {
  .deployment-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .deployment-select {
    width: 100%;
  }
}
</style>
