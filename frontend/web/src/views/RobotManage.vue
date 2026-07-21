<template>
  <div class="robot-page">
    <PageHeader
      title="机器人管理"
      description="机器人资产、站点绑定与运行状态管理"
      :breadcrumbs="[{ label: '资产感知' }, { label: '机器人管理' }]"
    >
      <template #actions>
        <el-button type="primary" @click="router.push('/robots/status')">
          查看在线状态
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="workspace-card">
      <div class="robot-workspace">
        <aside class="robot-nav">
          <el-input v-model="robotKeyword" size="small" placeholder="搜索机器人" clearable class="nav-search">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <div class="nav-list">
            <button
              v-for="item in filteredRobots"
              :key="item.id"
              type="button"
              class="nav-item"
              :class="{ active: selectedRobotId === item.id }"
              @click="selectedRobotId = item.id"
            >
              <span class="nav-dot" :class="robotDotClass(item)" />
              <span class="nav-body">
                <span class="nav-name">{{ item.name }}</span>
                <span class="nav-meta">{{ siteName(item.siteId) }}</span>
              </span>
            </button>
            <div v-if="!filteredRobots.length" class="nav-empty">无匹配机器人</div>
          </div>
          <ListPagination :total="robotStore.total" :page="robotPage" @change="loadRobotPage" />
        </aside>

        <main class="robot-main">
          <template v-if="robot">
            <div class="robot-toolbar">
              <div class="toolbar-info">
                <h3>{{ robot.name }}</h3>
                <p>{{ robot.model }} · {{ robot.serialNo || '序列号未登记' }}</p>
              </div>
              <div class="toolbar-tags">
                <el-tag size="small" effect="light" :type="statusType(robot.status)">{{ platformStatusLabel(robot.status) }}</el-tag>
                <el-tag size="small" effect="plain" :type="heartbeatTagType">{{ heartbeatConnectionLabel }}</el-tag>
                <span class="site-tag">{{ siteName(robot.siteId) }}</span>
              </div>
              <div class="toolbar-actions">
                <el-button plain size="small" class="action-btn action-detail" @click="openBindingDialog">重新绑定站点</el-button>
              </div>
            </div>

            <div class="status-grid">
              <div class="status-tile">
                <span class="dot" :class="heartbeatDotClass" />
                <div>
                  <div class="tile-title">Bridge</div>
                  <div class="tile-value">{{ heartbeatConnectionLabel }}</div>
                </div>
              </div>
              <div class="status-tile">
                <span class="dot" :class="nav2DotClass" />
                <div>
                  <div class="tile-title">Nav2</div>
                  <div class="tile-value">{{ nav2StatusLabel(robot.telemetry?.nav2Status) }}</div>
                </div>
              </div>
              <div class="status-tile">
                <span class="dot" :class="patrolDotClass" />
                <div>
                  <div class="tile-title">巡逻</div>
                  <div class="tile-value">{{ patrolStateLabel(robot.telemetry?.patrolState) }}</div>
                </div>
              </div>
              <div class="status-tile">
                <span class="dot" :class="platformDotClass" />
                <div>
                  <div class="tile-title">平台</div>
                  <div class="tile-value">{{ platformStatusLabel(robot.status) }}</div>
                </div>
              </div>
            </div>

            <el-card shadow="never" class="detail-card">
              <template #header>
                <span class="table-title">设备详情</span>
              </template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="名称">{{ robot.name }}</el-descriptions-item>
                <el-descriptions-item label="型号">{{ robot.model }}</el-descriptions-item>
                <el-descriptions-item label="序列号">{{ robot.serialNo }}</el-descriptions-item>
                <el-descriptions-item label="绑定站点">{{ siteName(robot.siteId) }}</el-descriptions-item>
                <el-descriptions-item label="系统模式">{{ robot.telemetry?.systemMode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="建图状态">{{ mappingStatusLabel(robot.telemetry?.mappingStatus) }}</el-descriptions-item>
                <el-descriptions-item label="CAN 状态">{{ robot.telemetry?.canStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="底盘状态">{{ robot.telemetry?.zlacStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="里程计">{{ sensorFreshness(robot.telemetry?.lastOdomAgeSec) }}</el-descriptions-item>
                <el-descriptions-item label="雷达">{{ sensorFreshness(robot.telemetry?.lastScanAgeSec) }}</el-descriptions-item>
                <el-descriptions-item label="位姿" :span="2">
                  <span v-if="robot.telemetry?.pose">
                    x={{ robot.telemetry.pose.x.toFixed(2) }},
                    y={{ robot.telemetry.pose.y.toFixed(2) }},
                    yaw={{ robot.telemetry.pose.yaw.toFixed(2) }}
                  </span>
                  <span v-else>-</span>
                </el-descriptions-item>
                <el-descriptions-item label="心跳协议">{{ heartbeatStatus?.protocolVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="最近心跳">{{ fmt(heartbeatStatus?.lastHeartbeatAt) }}</el-descriptions-item>
                <el-descriptions-item label="Bridge 诊断" :span="2">{{ heartbeatStatus?.diagnosticSummary || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never" class="detail-card location-card">
              <template #header>
                <div class="location-head">
                  <span class="table-title">GPS 位置与轨迹</span>
                  <div class="location-actions">
                    <el-switch v-model="followRobot" inline-prompt active-text="跟随" inactive-text="跟随" />
                    <el-button size="small" plain @click="fitMapToRobot">定位机器人</el-button>
                    <el-button size="small" plain :disabled="!trackPoints.length" @click="fitMapToTrack">定位轨迹</el-button>
                  </div>
                </div>
              </template>
              <div class="location-meta" v-if="selectedRobotLocation">
                <el-tag size="small" :type="selectedRobotLocation.realtime ? 'success' : 'warning'" effect="plain">
                  {{ locationModeLabel(selectedRobotLocation) }}
                </el-tag>
                <span v-if="selectedRobotLocation.gnssFix">
                  {{ GNSS_FIX_TYPE_LABELS[selectedRobotLocation.gnssFix.fixType] }}
                  · 卫星 {{ selectedRobotLocation.gnssFix.satellites ?? '-' }}
                  · HDOP {{ selectedRobotLocation.gnssFix.hdop ?? '-' }}
                </span>
              </div>
              <div class="track-toolbar">
                <el-date-picker
                  v-model="trackRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  value-format="YYYY-MM-DDTHH:mm:ss.SSS[Z]"
                  size="small"
                />
                <el-button size="small" type="primary" :loading="trackLoading" @click="queryTrack">查询轨迹</el-button>
                <el-button size="small" plain @click="clearTrack">清除</el-button>
              </div>
              <div class="location-map">
                <Map2D
                  v-if="mapCenter"
                  ref="mapRef"
                  :center="mapCenter"
                  :fallback-center="mapCenter"
                  :robot-location="selectedRobotLocation"
                  :robot-label="robot.name"
                  :track-points="trackPoints"
                  :show-track="trackPoints.length > 0"
                  :follow-robot="followRobot"
                />
              </div>
            </el-card>
          </template>
          <el-empty v-else description="请选择或添加机器人设备" :image-size="80" />
        </main>
      </div>
    </el-card>

    <el-dialog v-model="bindingDialogVisible" title="重新绑定机器人站点" width="480px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="仅空闲机器人可以重新绑定；执行中、暂停或人工接管的任务会被服务端拒绝。"
        style="margin-bottom: 16px"
      />
      <el-form label-width="96px">
        <el-form-item label="机器人">
          <span>{{ robot?.name || '-' }}</span>
        </el-form-item>
        <el-form-item label="当前站点">
          <span>{{ robot ? siteName(robot.siteId) : '-' }}</span>
        </el-form-item>
        <el-form-item label="新绑定站点" required>
          <el-select v-model="bindingSiteId" placeholder="请选择站点" style="width: 100%">
            <el-option v-for="site in siteStore.sites" :key="site.id" :label="site.name" :value="site.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="binding-hint">重新绑定只改变平台中的站点归属，不会修改 Bridge 地址、设备身份或历史巡检记录。</p>
      <template #footer>
        <el-button :disabled="bindingSaving" @click="bindingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindingSaving" @click="saveSiteBinding">确认重新绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import Map2D from '@/components/Map2D.vue'
import { resourcesApi } from '@/api/resources'
import { useRobotStore } from '@/stores/robot'
import { useRobotLocationStore } from '@/stores/robotLocation'
import { useSiteStore } from '@/stores/site'
import type { Robot } from '@/types'
import type { RobotLocation, RobotTrackPoint } from '@/types/robotLocation'
import { GNSS_FIX_TYPE_LABELS, locationModeLabel } from '@/utils/robotLocation'
import type { RobotHeartbeatStatus } from '@/types/robotHeartbeat'
import {
  mappingStatusLabel,
  nav2StatusLabel,
  patrolStateLabel,
  PLATFORM_STATUS_LABELS,
  sensorFreshness,
} from '@/utils/robotStatus'
import { connectionLabel, heartbeatVisual } from '@/utils/robotHeartbeatStatus'

const robotStore = useRobotStore()
const locationStore = useRobotLocationStore()
const siteStore = useSiteStore()
const router = useRouter()

const selectedRobotId = ref('')
const robotPage = ref(0)
const robotKeyword = ref('')
const bindingDialogVisible = ref(false)
const bindingSaving = ref(false)
const bindingSiteId = ref('')
const heartbeatStatus = ref<RobotHeartbeatStatus>()
const heartbeatLoading = ref(false)
const heartbeatLoadFailed = ref(false)
const mapRef = ref<InstanceType<typeof Map2D> | null>(null)
const followRobot = ref(false)
const trackLoading = ref(false)
const trackRange = ref<[string, string] | null>(null)
const trackPoints = ref<RobotTrackPoint[]>([])

function defaultTrackRange(): [string, string] {
  const end = new Date()
  const start = new Date(end.getTime() - 60 * 60 * 1000)
  return [start.toISOString(), end.toISOString()]
}

function loadRobotPage(page: number) {
  robotPage.value = page
  void robotStore.load(undefined, { page, size: 20 })
}

const robot = computed(() => robotStore.getRobotById(selectedRobotId.value))
const selectedRobotLocation = computed<RobotLocation | null>(() => {
  const robotId = selectedRobotId.value
  return robotId ? locationStore.getLocation(robotId) ?? null : null
})
const mapCenter = computed(() => {
  const siteCenter = robot.value?.siteId ? siteStore.getSiteById(robot.value.siteId)?.center : undefined
  return siteCenter ?? siteStore.sites[0]?.center ?? { lat: 30.27, lng: 120.15 }
})

const filteredRobots = computed(() => {
  const q = robotKeyword.value.trim().toLowerCase()
  const list = robotStore.robots
  if (!q) return list
  return list.filter(
    (item) => item.name.toLowerCase().includes(q) || item.model.toLowerCase().includes(q) || item.serialNo.toLowerCase().includes(q),
  )
})

watch(
  () => robotStore.robots.map((item) => item.id),
  (ids) => {
    if (!ids.includes(selectedRobotId.value)) selectedRobotId.value = ids[0] ?? ''
  },
  { immediate: true },
)

let heartbeatRequestId = 0

watch(selectedRobotId, (robotId) => {
  void loadHeartbeatStatus(robotId)
  trackPoints.value = []
  followRobot.value = false
  trackRange.value = defaultTrackRange()
  locationStore.stopPolling()
  if (robotId) locationStore.startRobotPolling(robotId)
}, { immediate: true })

const heartbeatTagType = computed(() => {
  if (heartbeatStatus.value) return heartbeatVisual(heartbeatStatus.value)
  return heartbeatLoadFailed.value ? 'danger' : 'info'
})

const heartbeatConnectionLabel = computed(() => {
  if (heartbeatLoading.value) return '查询中'
  if (heartbeatLoadFailed.value || !heartbeatStatus.value) return '状态未知'
  return heartbeatStatus.value.online ? '已连接' : connectionLabel(heartbeatStatus.value.connectionStatus)
})

const heartbeatDotClass = computed(() => {
  if (heartbeatLoading.value) return 'idle'
  if (heartbeatStatus.value?.online) return 'ok'
  if (heartbeatLoadFailed.value) return 'bad'
  return 'idle'
})

const nav2DotClass = computed(() => (robot.value?.telemetry?.nav2Status === 'running' ? 'ok' : 'warn'))

const patrolDotClass = computed(() => {
  const state = robot.value?.telemetry?.patrolState
  if (state === 'running') return 'ok'
  if (state === 'failed') return 'bad'
  return 'idle'
})

const platformDotClass = computed(() => {
  const status = robot.value?.status
  if (status === 'ONLINE') return 'ok'
  if (status === 'BUSY') return 'warn'
  if (status === 'OFFLINE') return 'bad'
  return 'idle'
})

function robotDotClass(item: Robot) {
  if (item.status === 'ONLINE') return 'ok'
  if (item.status === 'BUSY') return 'warn'
  if (item.status === 'OFFLINE') return 'bad'
  return 'idle'
}

function siteName(id?: string) {
  return id ? siteStore.getSiteById(id)?.name ?? '-' : '未绑定'
}

function statusType(s: Robot['status']) {
  return { ONLINE: 'success', BUSY: 'warning', OFFLINE: 'danger' }[s] as 'success' | 'warning' | 'danger'
}

function platformStatusLabel(s: Robot['status']) {
  return PLATFORM_STATUS_LABELS[s]
}

async function loadHeartbeatStatus(robotId: string) {
  const requestId = ++heartbeatRequestId
  heartbeatStatus.value = undefined
  heartbeatLoadFailed.value = false
  if (!robotId) return
  heartbeatLoading.value = true
  try {
    const status = await resourcesApi.getRobotHeartbeatStatus(robotId)
    if (requestId === heartbeatRequestId) heartbeatStatus.value = status
  } catch {
    if (requestId === heartbeatRequestId) heartbeatLoadFailed.value = true
  } finally {
    if (requestId === heartbeatRequestId) heartbeatLoading.value = false
  }
}

let heartbeatPollTimer: number | undefined
onMounted(() => {
  heartbeatPollTimer = window.setInterval(() => void loadHeartbeatStatus(selectedRobotId.value), 15_000)
})
onUnmounted(() => {
  if (heartbeatPollTimer) window.clearInterval(heartbeatPollTimer)
  locationStore.stopPolling()
})

async function queryTrack() {
  if (!selectedRobotId.value) return
  trackLoading.value = true
  try {
    const range = trackRange.value ?? defaultTrackRange()
    trackPoints.value = await locationStore.fetchTrack(selectedRobotId.value, {
      start: range[0],
      end: range[1],
      limit: 2000,
    })
    if (!trackPoints.value.length) ElMessage.info('该时间范围内暂无轨迹点')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '轨迹查询失败')
  } finally {
    trackLoading.value = false
  }
}

function clearTrack() {
  trackPoints.value = []
  if (selectedRobotId.value) locationStore.clearTrack(selectedRobotId.value)
}

function fitMapToRobot() {
  mapRef.value?.fitToRobot()
}

function fitMapToTrack() {
  mapRef.value?.fitToTrack()
}

function openBindingDialog() {
  if (!robot.value) return
  bindingSiteId.value = robot.value.siteId || ''
  bindingDialogVisible.value = true
}

async function saveSiteBinding() {
  const currentRobot = robot.value
  if (!currentRobot) return
  if (!bindingSiteId.value) {
    ElMessage.warning('请选择目标站点')
    return
  }
  if (bindingSiteId.value === currentRobot.siteId) {
    ElMessage.info('机器人已绑定到该站点')
    bindingDialogVisible.value = false
    return
  }
  const targetSiteName = siteName(bindingSiteId.value)
  try {
    await ElMessageBox.confirm(
      `确认将“${currentRobot.name}”从“${siteName(currentRobot.siteId)}”重新绑定到“${targetSiteName}”吗？`,
      '确认重新绑定',
      { type: 'warning', confirmButtonText: '确认绑定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  bindingSaving.value = true
  try {
    const updated = await resourcesApi.updateRobot(currentRobot.id, { siteId: bindingSiteId.value })
    robotStore.applyRemoteRobot(updated)
    bindingDialogVisible.value = false
    ElMessage.success(`已绑定到${targetSiteName}`)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '重新绑定失败，请稍后重试')
  } finally {
    bindingSaving.value = false
  }
}

function fmt(iso?: string | null) {
  if (!iso) return '-'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.workspace-card :deep(.el-card__body) {
  padding: 0;
}

.robot-workspace {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 520px;
}

.robot-nav {
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
  align-items: flex-start;
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
  margin-top: 6px;
}

.nav-dot.ok { background: #12b76a; }
.nav-dot.warn { background: #f59e0b; }
.nav-dot.bad { background: #f04438; }
.nav-dot.idle { background: #c0c4cc; }

.nav-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-name {
  font-size: 13px;
  color: var(--pi-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-meta {
  font-size: 11px;
  color: var(--pi-muted);
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

.robot-nav :deep(.el-pagination) {
  margin-top: auto;
  justify-content: center;
  flex-wrap: wrap;
}

.robot-main {
  padding: 16px 18px 18px;
  min-width: 0;
}

.robot-toolbar {
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
  gap: 8px;
  flex-wrap: wrap;
}

.site-tag {
  font-size: 12px;
  color: var(--pi-muted);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.toolbar-actions :deep(.action-btn) {
  margin: 0;
  padding: 5px 12px;
  height: 28px;
  border-radius: 6px;
  font-weight: 500;
}

.toolbar-actions :deep(.action-detail) {
  background: #e6f4ff;
  border-color: #91caff;
  color: #1677ff;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.status-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
  background: #fbfdff;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot.ok { background: #12b76a; }
.dot.warn { background: #f59e0b; }
.dot.bad { background: #f04438; }
.dot.warn { background: #f59e0b; }
.dot.idle { background: #c0c4cc; }

.tile-title {
  font-size: 11px;
  color: var(--pi-muted);
}

.tile-value {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--pi-text);
}

.detail-card {
  border: 1px solid var(--pi-border-soft);
  border-radius: 10px;
}

.detail-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--pi-border-soft);
  background: #fafbfc;
}

.binding-hint {
  margin: 0;
  color: var(--pi-muted);
  font-size: 13px;
  line-height: 1.6;
}

.location-card {
  margin-top: 14px;
}

.location-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.location-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.location-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--pi-muted);
}

.track-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.location-map {
  height: 360px;
  border: 1px solid var(--pi-border-soft);
  border-radius: 8px;
  overflow: hidden;
}

@media (max-width: 960px) {
  .robot-workspace {
    grid-template-columns: 1fr;
  }

  .robot-nav {
    border-right: none;
    border-bottom: 1px solid var(--pi-border-soft);
  }

  .nav-list {
    max-height: 160px;
  }

  .toolbar-actions {
    margin-left: 0;
    width: 100%;
  }

  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
