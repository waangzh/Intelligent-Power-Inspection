<template>
  <div class="monitor-page">
    <PageHeader
      title="实时监控"
      description="基于 ROS 建图的机器人位姿追踪（对接实机 mobile bridge）"
      :breadcrumbs="[{ label: '监控中心' }, { label: '实时监控' }]"
    />

    <button v-if="latestAlarm" type="button" class="alarm-ribbon" @click="router.push('/alarms')">
      <span class="alarm-icon"><el-icon><Bell /></el-icon></span>
      <strong>最新告警</strong>
      <span class="alarm-message">{{ latestAlarm.message }}</span>
      <time>{{ formatRelativeTime(latestAlarm.createdAt) }}</time>
      <el-icon><ArrowRight /></el-icon>
    </button>

    <section v-if="selectedRobot" class="operation-strip" :class="{ interrupted: !bridgeOnline }">
      <div class="mission-summary">
        <el-icon><Tickets /></el-icon>
        <div>
          <small>当前任务</small>
          <strong>{{ robotTask?.name ?? '暂无执行中任务' }}</strong>
        </div>
        <el-tag v-if="robotTask" size="small" type="success" effect="light">执行中</el-tag>
        <div v-if="robotTask" class="mission-progress">
          <span>进度 <b>{{ taskProgress }}%</b></span>
          <el-progress :percentage="taskProgress" :stroke-width="5" :show-text="false" />
        </div>
      </div>

      <div class="strip-divider" />
      <div class="strip-stat robot-selector">
        <el-icon><Cpu /></el-icon>
        <div><small>机器人</small>
          <el-select v-model="selectedRobotId" size="small" aria-label="选择机器人">
            <el-option v-for="robot in robotStore.robots" :key="robot.id" :label="robot.name" :value="robot.id" />
          </el-select>
        </div>
      </div>
      <div class="strip-stat"><el-icon><MapLocation /></el-icon><div><small>地图版本</small><strong>{{ mapVersion }}</strong><span>更新于 {{ mapUpdatedAt }}</span></div></div>
      <div class="strip-stat"><el-icon><DataAnalysis /></el-icon><div><small>场景版本</small><strong>{{ sceneMeta?.assetId ?? '按需加载' }}</strong><span>{{ sceneMeta ? `更新于 ${formatClock(sceneMeta.updatedAt)}` : '静态点云资产' }}</span></div></div>
      <div class="strip-stat"><el-icon><Connection /></el-icon><div><small>Bridge 连接</small><strong :class="bridgeOnline ? 'ok' : 'bad'">{{ bridgeOnline ? '在线' : '离线' }}</strong></div></div>
      <div class="strip-stat"><el-icon><Monitor /></el-icon><div><small>平台连接</small><strong :class="platformOnline ? 'ok' : 'bad'">{{ platformOnline ? '在线' : '离线' }}</strong></div></div>
      <div class="strip-stat"><el-icon><Timer /></el-icon><div><small>数据延迟</small><strong :class="latencyClass">{{ dataLatency }}</strong></div></div>
    </section>

    <div v-if="selectedRobot" class="monitor-workspace">
      <section class="tracking-panel">
        <header class="tracking-header">
          <div class="tracking-title">
            <strong>实时空间追踪 · {{ selectedRobot.name }}</strong>
            <span>{{ selectedRobot.model }} · map 坐标系</span>
          </div>

          <el-segmented v-model="viewMode" :options="viewOptions" class="view-switch" />

          <div class="canvas-actions">
            <el-button size="small" plain title="地图居中" @click="recenterMap"><el-icon><Aim /></el-icon><span>居中</span></el-button>
            <el-button size="small" :type="followRobot ? 'primary' : 'default'" plain title="跟随机器人" @click="followRobot = !followRobot"><el-icon><Location /></el-icon><span>跟随机器人</span></el-button>
            <el-tooltip content="当前未接入 map 坐标历史轨迹" placement="bottom">
              <span><el-button size="small" plain disabled><el-icon><Clock /></el-icon><span>轨迹回放</span></el-button></span>
            </el-tooltip>
            <el-button size="small" plain title="全屏" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon><span>全屏</span></el-button>
          </div>
        </header>

        <div ref="canvasShellRef" class="tracking-canvas" :class="`mode-${viewMode}`">
          <RosSlamMonitorMap
            v-if="viewMode === '2d'"
            ref="mapRef"
            :route="displayRoute"
            :robot-position="robotPos"
            :task-progress="taskProgress"
            :active-target-id="selectedRobot.telemetry?.activeTargetId"
            :alarm-checkpoint-ids="alarmCheckpointIds"
            :route-abnormal="selectedRobot.telemetry?.patrolState === 'failed'"
            :offline="!bridgeOnline"
            :last-position-at="lastPositionClock"
            :tracking-alert="trackingAlert"
          />

          <MonitorSceneView
            v-else-if="viewMode === '3d'"
            :active="true"
            :site-id="selectedRobot.siteId"
            :robot-id="selectedRobot.id"
            :robot-pose="selectedRobot.telemetry?.pose"
            @state-change="sceneMeta = $event"
          />

          <template v-else>
            <div class="split-pane">
              <div class="pane-label"><strong>二维导航地图</strong><span>路线、位姿、巡检点</span></div>
              <RosSlamMonitorMap
                ref="mapRef"
                :route="displayRoute"
                :robot-position="robotPos"
                :task-progress="taskProgress"
                :active-target-id="selectedRobot.telemetry?.activeTargetId"
                :alarm-checkpoint-ids="alarmCheckpointIds"
                :route-abnormal="selectedRobot.telemetry?.patrolState === 'failed'"
                :offline="!bridgeOnline"
                :last-position-at="lastPositionClock"
                :tracking-alert="trackingAlert"
              />
            </div>
            <div class="split-pane">
              <div class="pane-label dark"><strong>三维场景</strong><span>点云、设备、空间关系</span></div>
              <MonitorSceneView
                :active="true"
                compact
                :site-id="selectedRobot.siteId"
                :robot-id="selectedRobot.id"
                :robot-pose="selectedRobot.telemetry?.pose"
                @state-change="sceneMeta = $event"
              />
            </div>
          </template>
        </div>

        <footer class="canvas-meta">
          <span>地图：<b>{{ mapVersion }}</b></span>
          <span>更新：<b>{{ mapUpdatedAt }}</b></span>
          <span>分辨率：<b>{{ mapResolution }}</b></span>
          <span>任务路线：<b>{{ displayRoute?.name ?? '未关联' }}</b></span>
          <span class="sync-state"><i :class="bridgeOnline ? 'ok' : 'bad'" />{{ bridgeOnline ? '实时同步' : '数据中断' }}</span>
        </footer>
      </section>

      <aside class="right-rail">
        <section class="telemetry-panel">
          <header class="panel-head">
            <div><strong>实时遥测</strong><span>运行链路与关键状态</span></div>
            <el-tag size="small" effect="light">{{ selectedRobot.model }}</el-tag>
          </header>

          <div class="telemetry-block">
            <div class="block-title">连接状态</div>
            <div class="status-grid">
              <div v-for="item in connectionStates" :key="item.label" class="status-tile">
                <i :class="item.tone" />
                <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
              </div>
            </div>
          </div>

          <div class="telemetry-block">
            <div class="block-title">传感器状态</div>
            <div class="sensor-list">
              <div v-for="sensor in sensorStates" :key="sensor.label" class="sensor-row">
                <span>{{ sensor.label }}</span>
                <div v-if="sensor.age != null" class="freshness-track"><i :style="{ width: `${sensorPercent(sensor.age)}%` }" :class="sensorTone(sensor.age)" /></div>
                <div v-else class="freshness-empty">无数据</div>
                <strong :class="sensor.age == null ? 'muted' : sensorTone(sensor.age)">{{ sensorFreshnessLabel(sensor.age) }}</strong>
              </div>
            </div>
          </div>

          <div class="telemetry-columns">
            <div class="telemetry-block metrics-block">
              <div class="block-title">机器人状态</div>
              <dl class="metric-list">
                <dt>电量</dt><dd class="muted">未上报</dd>
                <dt>当前速度</dt><dd>{{ velocityLabel }}</dd>
                <dt>网络延迟</dt><dd>{{ dataLatency }}</dd>
                <dt>最近心跳</dt><dd>{{ heartbeatLabel }}</dd>
              </dl>
            </div>
            <div class="telemetry-block metrics-block">
              <div class="block-title">定位状态</div>
              <dl class="metric-list">
                <dt>定位状态</dt><dd :class="localizationTone">{{ localizationLabel }}</dd>
                <dt>定位置信度</dt><dd class="muted">未上报</dd>
                <dt>建图状态</dt><dd>{{ mappingStatusLabel(selectedRobot.telemetry?.mappingStatus) }}</dd>
                <dt>坐标系</dt><dd>{{ selectedRobot.telemetry?.pose?.frame || 'map' }}</dd>
              </dl>
            </div>
          </div>

          <div class="pose-block">
            <div class="block-title">当前位姿（map）</div>
            <div v-if="selectedRobot.telemetry?.pose" class="pose-grid">
              <span><small>X</small><code>{{ selectedRobot.telemetry.pose.x.toFixed(3) }}</code></span>
              <span><small>Y</small><code>{{ selectedRobot.telemetry.pose.y.toFixed(3) }}</code></span>
              <span><small>θ</small><code>{{ selectedRobot.telemetry.pose.yaw.toFixed(3) }}</code></span>
            </div>
            <div v-else class="pose-empty">无定位数据 · 最近收到：{{ lastPositionClock }}</div>
          </div>
        </section>

        <section class="camera-panel" :class="{ collapsed: !cameraExpanded }">
          <header class="panel-head">
            <div><strong>前视相机</strong><span>{{ cameraExpanded ? '实时视频与缺陷识别' : '视频区域已折叠' }}</span></div>
            <el-button text circle :title="cameraExpanded ? '折叠相机' : '展开相机'" @click="cameraExpanded = !cameraExpanded">
              <el-icon><ArrowUp v-if="cameraExpanded" /><ArrowDown v-else /></el-icon>
            </el-button>
          </header>
          <div v-if="cameraExpanded" class="video-shell">
            <div class="video-empty">
              <el-icon :size="34"><VideoCameraFilled /></el-icon>
              <strong>等待视频流</strong>
              <span>相机接入后显示实时画面</span>
            </div>
            <span class="video-live"><i />LIVE</span>
            <span class="video-time">{{ videoClock }}</span>
            <span class="video-robot">{{ selectedRobot.name }}</span>
          </div>
        </section>
      </aside>
    </div>

    <el-card v-else shadow="never"><div class="empty-hint">暂无机器人数据</div></el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Aim,
  ArrowDown,
  ArrowRight,
  ArrowUp,
  Bell,
  Clock,
  Connection,
  Cpu,
  DataAnalysis,
  FullScreen,
  Location,
  MapLocation,
  Monitor,
  Tickets,
  Timer,
  VideoCameraFilled,
} from '@element-plus/icons-vue'
import MonitorSceneView from '@/components/MonitorSceneView.vue'
import PageHeader from '@/components/PageHeader.vue'
import RosSlamMonitorMap from '@/components/RosSlamMonitorMap.vue'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useTaskStore } from '@/stores/task'
import type { RouteExecutorDocument } from '@/types/routeExecutor'
import {
  bridgeReachable,
  mappingStatusLabel,
  nav2StatusLabel,
} from '@/utils/robotStatus'

type ViewMode = '2d' | '3d' | 'dual'
type SceneState = { assetId: string; updatedAt: string; coordinateSystem: string; poseOverlayAvailable: boolean }

const router = useRouter()
const robotStore = useRobotStore()
const taskStore = useTaskStore()
const routeStore = useRouteStore()
const alarmStore = useAlarmStore()

const selectedRobotId = ref('')
const viewMode = ref<ViewMode>('2d')
const followRobot = ref(false)
const cameraExpanded = ref(true)
const videoClock = ref('')
const mapRef = ref<{ recenter: () => void } | null>(null)
const canvasShellRef = ref<HTMLElement | null>(null)
const sceneMeta = ref<SceneState | null>(null)
const viewOptions = [
  { label: '二维运行图', value: '2d' },
  { label: '三维场景', value: '3d' },
  { label: '双视图', value: 'dual' },
]
let clockTimer: ReturnType<typeof setInterval> | undefined

const selectedRobot = computed(() => {
  return robotStore.robots.find(robot => robot.id === selectedRobotId.value) ?? robotStore.robots[0]
})

const bridgeOnline = computed(() => selectedRobot.value ? bridgeReachable(selectedRobot.value) : false)
const platformOnline = computed(() => selectedRobot.value?.status === 'ONLINE' || selectedRobot.value?.status === 'BUSY')

const robotTask = computed(() => {
  const robot = selectedRobot.value
  if (!robot?.currentTaskId) return null
  return taskStore.getTaskById(robot.currentTaskId) ?? null
})

const taskProgress = computed(() => {
  const task = robotTask.value
  if (!task) return 0
  return Math.round(taskStore.executionFor(task.id)?.progress ?? task.progress)
})

const displayRoute = computed(() => {
  const routeId = robotTask.value?.routeId
  if (routeId) return routeStore.getRouteById(routeId) ?? null
  const siteId = selectedRobot.value?.siteId
  if (!siteId) return null
  const siteRoutes = routeStore.getRoutesBySite(siteId)
  return siteRoutes.find(route => route.mapId || route.executorJson?.map_snapshot) ?? siteRoutes[0] ?? null
})

const robotPos = computed(() => selectedRobot.value?.position ?? null)
const latestAlarm = computed(() => alarmStore.alarms[0] ?? null)
const alarmCheckpointIds = computed(() => {
  const taskId = robotTask.value?.id
  return alarmStore.alarms
    .filter(alarm => alarm.checkpointId && (!taskId || alarm.taskId === taskId))
    .map(alarm => alarm.checkpointId as string)
})

const mapVersion = computed(() => {
  const route = displayRoute.value
  if (route?.mapId) return route.mapId
  const doc = route?.executorJson as (RouteExecutorDocument & { map?: { yaml?: string } }) | null | undefined
  return doc?.map?.yaml?.replace(/\.(yaml|yml)$/i, '') ?? '未关联'
})

const mapUpdatedAt = computed(() => formatClock(displayRoute.value?.createdAt))
const mapResolution = computed(() => {
  const doc = displayRoute.value?.executorJson as (RouteExecutorDocument & { map?: { resolution?: number }; map_snapshot?: { resolution?: number } }) | null | undefined
  const resolution = doc?.map?.resolution ?? doc?.map_snapshot?.resolution
  return resolution == null ? '未上报' : `${resolution} m/pixel`
})

const dataDelaySeconds = computed(() => {
  const telemetry = selectedRobot.value?.telemetry
  const ages = [telemetry?.lastOdomAgeSec, telemetry?.lastScanAgeSec].filter((age): age is number => age != null)
  return ages.length ? Math.max(...ages) : null
})

const dataLatency = computed(() => dataDelaySeconds.value == null ? '无数据' : `${dataDelaySeconds.value.toFixed(1)}s`)
const latencyClass = computed(() => dataDelaySeconds.value == null ? 'muted' : dataDelaySeconds.value <= 3 ? 'ok' : dataDelaySeconds.value <= 10 ? 'warn' : 'bad')
const lastPositionClock = computed(() => formatClock(selectedRobot.value?.telemetry?.bridgeSyncedAt ?? selectedRobot.value?.lastOnlineAt))
const heartbeatLabel = computed(() => formatRelativeTime(selectedRobot.value?.telemetry?.bridgeSyncedAt ?? selectedRobot.value?.lastOnlineAt))

const localizationLabel = computed(() => {
  const telemetry = selectedRobot.value?.telemetry
  if (!telemetry?.pose) return '无定位数据'
  if ((telemetry.lastOdomAgeSec ?? Infinity) > 10) return '数据已过期'
  return '正常'
})
const localizationTone = computed(() => localizationLabel.value === '正常' ? 'ok' : 'bad')

const trackingAlert = computed(() => {
  if (!selectedRobot.value?.telemetry?.pose) return '定位数据异常：当前无有效位姿'
  if ((selectedRobot.value.telemetry.lastOdomAgeSec ?? 0) > 10) return '定位数据异常：里程计数据已过期'
  if (selectedRobot.value.telemetry.patrolState === 'failed') return '路线执行异常：请检查阻塞或导航告警'
  return ''
})

const connectionStates = computed(() => {
  const robot = selectedRobot.value
  if (!robot) return []
  return [
    { label: 'Bridge', value: bridgeOnline.value ? '在线' : '离线', tone: bridgeOnline.value ? 'ok' : 'bad' },
    { label: 'Nav2', value: nav2StatusLabel(robot.telemetry?.nav2Status), tone: robot.telemetry?.nav2Status === 'running' ? 'ok' : 'warn' },
    { label: '定位', value: localizationLabel.value, tone: localizationTone.value },
    { label: '平台', value: platformOnline.value ? '在线' : '离线', tone: platformOnline.value ? 'ok' : 'bad' },
  ]
})

const sensorStates = computed(() => [
  { label: '里程计', age: selectedRobot.value?.telemetry?.lastOdomAgeSec },
  { label: '激光雷达', age: selectedRobot.value?.telemetry?.lastScanAgeSec },
  { label: '相机', age: null },
  { label: 'IMU', age: null },
])

const velocityLabel = computed(() => {
  const velocity = selectedRobot.value?.telemetry?.velocity?.linear_x
  return velocity == null ? '未上报' : `${velocity.toFixed(2)} m/s`
})

watch(() => robotStore.robots, robots => {
  if (!robots.length) selectedRobotId.value = ''
  else if (!robots.some(robot => robot.id === selectedRobotId.value)) selectedRobotId.value = robots[0].id
}, { immediate: true, deep: true })

watch(robotPos, () => {
  if (followRobot.value) mapRef.value?.recenter()
})

watch(selectedRobotId, () => {
  sceneMeta.value = null
  followRobot.value = false
})

function recenterMap() {
  mapRef.value?.recenter()
}

async function toggleFullscreen() {
  const element = canvasShellRef.value
  if (!element) return
  if (document.fullscreenElement) await document.exitFullscreen().catch(() => {})
  else await element.requestFullscreen().catch(() => {})
}

function formatClock(value?: string | null) {
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未知'
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

function formatRelativeTime(value?: string | null) {
  if (!value) return '未知'
  const timestamp = new Date(value).getTime()
  if (Number.isNaN(timestamp)) return '未知'
  const diffSeconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000))
  if (diffSeconds < 60) return `${diffSeconds} 秒前`
  if (diffSeconds < 3600) return `${Math.floor(diffSeconds / 60)} 分钟前`
  return `${Math.floor(diffSeconds / 3600)} 小时前`
}

function sensorPercent(age: number) {
  if (age <= 1) return 100
  if (age <= 3) return 86
  if (age <= 10) return 48
  return 18
}

function sensorTone(age: number) {
  if (age <= 3) return 'ok'
  if (age <= 10) return 'warn'
  return 'bad'
}

function sensorFreshnessLabel(age: number | null | undefined) {
  if (age == null) return `最近收到：${lastPositionClock.value}`
  return `${age.toFixed(1)} 秒前`
}

function tickClock() {
  videoClock.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

onMounted(() => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.monitor-page {
  --monitor-line: #e2eaf3;
  --monitor-soft: #f6f9fc;
  --monitor-ink: #152c49;
  color: var(--monitor-ink);
}

.alarm-ribbon {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 9px;
  width: min(440px, 100%);
  min-height: 44px;
  margin: -64px 0 17px auto;
  padding: 7px 12px;
  border: 1px solid #e6edf5;
  border-radius: 7px;
  color: #49627f;
  text-align: left;
  background: #fff;
  box-shadow: 0 5px 16px rgba(31, 50, 73, .07);
  cursor: pointer;
}

.alarm-ribbon:hover { border-color: #bfd2eb; }
.alarm-icon { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 50%; color: #1768f2; background: #eef5ff; }
.alarm-ribbon strong { font-size: 12px; }
.alarm-message { overflow: hidden; color: #f26b2b; font-size: 12px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.alarm-ribbon time { color: #8a9ab0; font-size: 11px; white-space: nowrap; }

.operation-strip {
  display: grid;
  grid-template-columns: minmax(280px, 1.5fr) 1px repeat(6, minmax(105px, .72fr));
  align-items: center;
  min-height: 82px;
  margin-bottom: 16px;
  padding: 11px 15px;
  border: 1px solid var(--monitor-line);
  border-left: 3px solid #35a872;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(31, 50, 73, .06);
}

.operation-strip.interrupted { border-left-color: #f04438; }
.mission-summary { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 5px 10px; padding-right: 17px; }
.mission-summary > .el-icon { grid-row: 1 / 3; align-self: start; margin-top: 3px; color: #45627f; font-size: 19px; }
.mission-summary small,
.strip-stat small { display: block; color: #7a8ca3; font-size: 10px; line-height: 1.3; }
.mission-summary strong { display: block; overflow: hidden; color: #193552; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.mission-progress { grid-column: 2 / 4; display: grid; grid-template-columns: auto minmax(80px, 1fr); align-items: center; gap: 9px; }
.mission-progress span { color: #778ba4; font-size: 10px; }
.mission-progress b { color: #244465; font-size: 12px; }
.strip-divider { width: 1px; height: 45px; background: var(--monitor-line); }

.strip-stat { display: flex; align-items: flex-start; gap: 8px; min-width: 0; padding: 4px 10px; }
.strip-stat > .el-icon { margin-top: 2px; flex: 0 0 auto; color: #4f6783; font-size: 16px; }
.strip-stat div { min-width: 0; }
.strip-stat strong { display: block; overflow: hidden; margin-top: 2px; color: #284562; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.strip-stat span { display: block; overflow: hidden; margin-top: 2px; color: #8a9ab0; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.strip-stat strong.ok { color: #118653; }
.strip-stat strong.warn { color: #b76a05; }
.strip-stat strong.bad { color: #d92d20; }
.strip-stat strong.muted { color: #8b9aab; }
.robot-selector :deep(.el-select) { width: 105px; margin-top: 1px; }
.robot-selector :deep(.el-select__wrapper) { min-height: 24px; padding: 2px 7px; box-shadow: none !important; }

.monitor-workspace {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(310px, 3fr);
  align-items: start;
  gap: 16px;
}

.tracking-panel,
.telemetry-panel,
.camera-panel {
  overflow: hidden;
  border: 1px solid var(--monitor-line);
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(31, 50, 73, .06);
}

.tracking-header {
  display: grid;
  grid-template-columns: minmax(210px, 1fr) auto minmax(330px, 1fr);
  align-items: center;
  gap: 16px;
  min-height: 82px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--monitor-line);
}

.tracking-title strong,
.panel-head strong { display: block; color: #172f4e; font-size: 14px; }
.tracking-title span,
.panel-head span { display: block; margin-top: 3px; color: #8495aa; font-size: 10px; }
.view-switch { justify-self: center; }
.view-switch :deep(.el-segmented__item) { min-width: 92px; font-size: 11px; font-weight: 700; }
.canvas-actions { display: flex; justify-content: flex-end; gap: 6px; }
.canvas-actions :deep(.el-button) { margin: 0; border-radius: 5px; }

.tracking-canvas {
  display: grid;
  min-height: 650px;
  background: #f5f8fb;
}

.tracking-canvas.mode-dual { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; background: #ccd8e5; }
.tracking-canvas:fullscreen { min-height: 100vh; }
.split-pane { position: relative; min-width: 0; overflow: hidden; background: #fff; }
.split-pane > :deep(.ros-monitor-map),
.split-pane > :deep(.monitor-scene) { min-height: 650px; }
.pane-label { position: absolute; z-index: 5; top: 12px; left: 12px; display: flex; flex-direction: column; padding: 7px 10px; border: 1px solid #dfe8f1; border-radius: 5px; color: #193552; background: rgba(255,255,255,.92); box-shadow: 0 4px 12px rgba(31,50,73,.08); pointer-events: none; }
.pane-label.dark { border-color: #28515a; color: #d2e5e8; background: rgba(7,23,29,.86); }
.pane-label strong { font-size: 11px; }
.pane-label span { margin-top: 2px; color: #7e91a8; font-size: 9px; }
.pane-label.dark span { color: #70969d; }

.canvas-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 26px;
  min-height: 45px;
  padding: 10px 15px;
  border-top: 1px solid var(--monitor-line);
  color: #72859d;
  font-size: 10px;
}
.canvas-meta b { color: #3c5673; font-weight: 600; }
.sync-state { display: flex; align-items: center; gap: 5px; margin-left: auto; }
.sync-state i { width: 7px; height: 7px; border-radius: 50%; }
.sync-state i.ok { background: #2fa66f; }
.sync-state i.bad { background: #f04438; }

.right-rail { display: grid; gap: 16px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 54px; padding: 10px 14px; border-bottom: 1px solid var(--monitor-line); }
.telemetry-block { padding: 13px 14px 0; }
.block-title { margin-bottom: 9px; color: #5a708c; font-size: 10px; font-weight: 800; }
.status-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.status-tile { display: flex; align-items: center; gap: 8px; min-height: 51px; padding: 8px 10px; border: 1px solid #e5edf5; border-radius: 6px; background: #f8fafd; }
.status-tile > i { width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; }
.status-tile > i.ok { background: #2fa66f; }
.status-tile > i.warn { background: #f79009; }
.status-tile > i.bad { background: #f04438; }
.status-tile span { display: block; color: #7f91a7; font-size: 10px; }
.status-tile strong { display: block; margin-top: 2px; color: #294865; font-size: 11px; }

.sensor-list { display: grid; gap: 8px; }
.sensor-row { display: grid; grid-template-columns: 56px minmax(55px, 1fr) minmax(74px, auto); align-items: center; gap: 8px; color: #526c88; font-size: 10px; }
.sensor-row > strong { text-align: right; font-size: 9px; font-weight: 600; white-space: nowrap; }
.sensor-row > strong.ok { color: #25885f; }
.sensor-row > strong.warn { color: #c06d04; }
.sensor-row > strong.bad { color: #d92d20; }
.sensor-row > strong.muted { color: #9aa8b8; }
.freshness-track { height: 6px; overflow: hidden; border-radius: 9px; background: #e8eef5; }
.freshness-track i { display: block; height: 100%; border-radius: inherit; }
.freshness-track i.ok { background: #35a872; }
.freshness-track i.warn { background: #f79009; }
.freshness-track i.bad { background: #f04438; }
.freshness-empty { height: 6px; border-radius: 9px; background: repeating-linear-gradient(135deg, #edf1f5 0 4px, #f8fafc 4px 8px); color: transparent; }

.telemetry-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 13px; border-top: 1px solid var(--monitor-line); }
.telemetry-columns .telemetry-block { min-width: 0; padding-bottom: 12px; }
.telemetry-columns .telemetry-block + .telemetry-block { border-left: 1px solid var(--monitor-line); }
.metric-list { display: grid; grid-template-columns: 1fr auto; gap: 7px 6px; margin: 0; font-size: 9px; }
.metric-list dt { color: #8394a9; }
.metric-list dd { margin: 0; color: #34516e; font-weight: 700; text-align: right; }
.metric-list dd.ok { color: #20845a; }
.metric-list dd.bad { color: #d92d20; }
.metric-list dd.muted { color: #9aa8b8; }

.pose-block { margin: 0 14px 14px; padding-top: 12px; border-top: 1px solid var(--monitor-line); }
.pose-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); overflow: hidden; border: 1px solid #dce6ef; border-radius: 6px; background: #f7f9fc; }
.pose-grid span { padding: 8px; text-align: center; }
.pose-grid span + span { border-left: 1px solid #dce6ef; }
.pose-grid small { display: block; margin-bottom: 3px; color: #8193a9; font-size: 9px; }
.pose-grid code { color: #1f446b; font: 700 11px/1.2 "SFMono-Regular", Consolas, monospace; }
.pose-empty { padding: 11px; border-radius: 6px; color: #8d9bad; font-size: 10px; text-align: center; background: #f7f9fc; }

.camera-panel .panel-head { min-height: 48px; }
.camera-panel.collapsed .panel-head { border-bottom: 0; }
.video-shell { position: relative; aspect-ratio: 16 / 8.8; margin: 0 12px 12px; overflow: hidden; border-radius: 6px; background: linear-gradient(rgba(255,255,255,.025) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.025) 1px, transparent 1px), #0b1827; background-size: 24px 24px; }
.video-empty { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px; color: #536b87; }
.video-empty strong { color: #9eb0c3; font-size: 12px; }
.video-empty span { font-size: 10px; }
.video-live,
.video-time,
.video-robot { position: absolute; padding: 4px 7px; border-radius: 3px; color: #fff; font-size: 9px; font-weight: 700; background: rgba(0,0,0,.58); }
.video-live { top: 8px; left: 8px; display: flex; align-items: center; gap: 4px; background: rgba(217,45,32,.86); }
.video-live i { width: 5px; height: 5px; border-radius: 50%; background: #fff; animation: livePulse 1.2s ease-in-out infinite; }
.video-time { top: 8px; right: 8px; }
.video-robot { bottom: 8px; left: 8px; color: #dce8f5; }
@keyframes livePulse { 50% { opacity: .35; } }

@media (max-width: 1450px) {
  .operation-strip { grid-template-columns: minmax(260px, 1.3fr) 1px repeat(6, minmax(92px, .7fr)); }
  .strip-stat { padding-inline: 7px; }
  .tracking-header { grid-template-columns: 1fr auto; }
  .canvas-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .tracking-canvas { min-height: 610px; }
}

@media (max-width: 1180px) {
  .alarm-ribbon { margin-top: 0; }
  .operation-strip { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
  .mission-summary { grid-column: span 2; }
  .strip-divider { display: none; }
  .monitor-workspace { grid-template-columns: 1fr; }
  .right-rail { grid-template-columns: minmax(0, 1.5fr) minmax(280px, 1fr); }
}

@media (max-width: 800px) {
  .alarm-ribbon { grid-template-columns: auto minmax(0, 1fr) auto; }
  .alarm-ribbon strong,
  .alarm-ribbon time { display: none; }
  .operation-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .mission-summary { grid-column: 1 / -1; }
  .tracking-header { grid-template-columns: 1fr; }
  .view-switch { width: 100%; justify-self: stretch; }
  .view-switch :deep(.el-segmented__item) { min-width: 0; }
  .canvas-actions { grid-column: auto; }
  .canvas-actions :deep(.el-button span span) { display: none; }
  .tracking-canvas.mode-dual { grid-template-columns: 1fr; }
  .right-rail { grid-template-columns: 1fr; }
  .tracking-canvas { min-height: 540px; }
  .split-pane > :deep(.ros-monitor-map),
  .split-pane > :deep(.monitor-scene) { min-height: 500px; }
}

@media (max-width: 520px) {
  .operation-strip { grid-template-columns: 1fr; }
  .mission-summary { grid-column: auto; }
  .tracking-header { padding-inline: 12px; }
  .canvas-actions { display: grid; grid-template-columns: repeat(4, 1fr); }
  .canvas-actions :deep(.el-button) { width: 100%; padding-inline: 8px; }
  .telemetry-columns { grid-template-columns: 1fr; }
  .telemetry-columns .telemetry-block + .telemetry-block { border-top: 1px solid var(--monitor-line); border-left: 0; }
}
</style>
