<template>
  <div class="monitor-page">
    <PageHeader
      title="实时监控"
      description="基于 ROS 建图的机器人位姿追踪（对接实机 mobile bridge）"
      :breadcrumbs="[{ label: '监控中心' }, { label: '实时监控' }]"
    />

    <div v-if="selectedRobot" class="live-strip">
      <div class="live-item">
        <el-icon><VideoCamera /></el-icon>
        <span class="live-label">当前任务</span>
        <template v-if="robotTask">
          <el-link type="primary" @click="router.push(`/tasks/${robotTask.id}`)">{{ robotTask.name }}</el-link>
          <el-progress
            :percentage="taskStore.executionFor(robotTask.id)?.progress ?? robotTask.progress"
            :stroke-width="6"
            style="width: 120px"
          />
        </template>
        <span v-else class="live-muted">空闲 · 无执行中任务</span>
      </div>
      <div class="live-divider" />
      <div class="live-item">
        <el-icon><Bell /></el-icon>
        <span class="live-label">最新告警</span>
        <template v-if="latestAlarm">
          <el-link type="warning" @click="router.push('/alarms')">{{ latestAlarm.message }}</el-link>
          <span class="live-muted">{{ formatRelativeTime(latestAlarm.createdAt) }}</span>
        </template>
        <span v-else class="live-muted">暂无告警</span>
      </div>
    </div>

    <el-row :gutter="16" class="monitor-main">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="map-card">
          <template #header>
            <div class="card-head">
              <div class="map-title">
                <span class="table-title">地图实时追踪</span>
                <span class="robot-name">{{ selectedRobot?.name ?? '未连接' }}</span>
              </div>
              <div class="map-status">
                <span class="status-pill" :class="bridgeOnline ? 'ok' : 'bad'">
                  <i /> Bridge {{ bridgeOnline ? '在线' : '离线' }}
                </span>
                <span class="status-pill" :class="patrolPillClass">
                  <i /> {{ selectedRobot ? patrolStateLabel(selectedRobot.telemetry?.patrolState) : '无设备' }}
                </span>
              </div>
            </div>
            <div class="map-toolbar">
              <el-button size="small" plain @click="recenterMap">
                <el-icon><Aim /></el-icon>
                居中
              </el-button>
              <el-button size="small" :type="followRobot ? 'primary' : 'default'" plain @click="followRobot = !followRobot">
                <el-icon><Location /></el-icon>
                {{ followRobot ? '跟随中' : '跟随机器人' }}
              </el-button>
              <el-button size="small" plain @click="toggleFullscreen">
                <el-icon><FullScreen /></el-icon>
                全屏
              </el-button>
            </div>
          </template>
          <div ref="mapShellRef" class="map-shell">
            <RosSlamMonitorMap ref="mapRef" :route="displayRoute" :robot-position="robotPos" />
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8" class="side-col">
        <template v-if="selectedRobot">
          <el-card shadow="never" class="telemetry-card">
            <template #header>
              <div class="panel-head">
                <span class="table-title">实机遥测</span>
                <el-tag size="small" effect="light">{{ selectedRobot.model }}</el-tag>
              </div>
            </template>

            <div class="telemetry-section">
              <div class="section-label">连接状态</div>
              <div class="status-grid">
                <div class="status-tile">
                  <span class="dot" :class="bridgeOnline ? 'ok' : 'bad'" />
                  <div>
                    <div class="tile-title">Bridge</div>
                    <div class="tile-value">{{ bridgeOnline ? '在线' : '离线' }}</div>
                  </div>
                </div>
                <div class="status-tile">
                  <span class="dot" :class="nav2Class(selectedRobot.telemetry?.nav2Status)" />
                  <div>
                    <div class="tile-title">Nav2</div>
                    <div class="tile-value">{{ nav2StatusLabel(selectedRobot.telemetry?.nav2Status) }}</div>
                  </div>
                </div>
                <div class="status-tile">
                  <span class="dot" :class="mappingClass(selectedRobot.telemetry?.mappingStatus)" />
                  <div>
                    <div class="tile-title">建图</div>
                    <div class="tile-value">{{ mappingStatusLabel(selectedRobot.telemetry?.mappingStatus) }}</div>
                  </div>
                </div>
                <div class="status-tile">
                  <span class="dot" :class="platformClass(selectedRobot.status)" />
                  <div>
                    <div class="tile-title">平台</div>
                    <div class="tile-value">{{ platformStatusLabel(selectedRobot.status) }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="telemetry-section">
              <div class="section-label">传感器</div>
              <div class="sensor-row">
                <span>里程计</span>
                <el-progress
                  :percentage="sensorPercent(selectedRobot.telemetry?.lastOdomAgeSec)"
                  :color="sensorColor(selectedRobot.telemetry?.lastOdomAgeSec)"
                  :stroke-width="8"
                  :show-text="false"
                />
                <span class="sensor-text">{{ sensorFreshness(selectedRobot.telemetry?.lastOdomAgeSec) }}</span>
              </div>
              <div class="sensor-row">
                <span>雷达</span>
                <el-progress
                  :percentage="sensorPercent(selectedRobot.telemetry?.lastScanAgeSec)"
                  :color="sensorColor(selectedRobot.telemetry?.lastScanAgeSec)"
                  :stroke-width="8"
                  :show-text="false"
                />
                <span class="sensor-text">{{ sensorFreshness(selectedRobot.telemetry?.lastScanAgeSec) }}</span>
              </div>
            </div>

            <div class="telemetry-section">
              <div class="section-label">位姿 · map 坐标</div>
              <div v-if="selectedRobot.telemetry?.pose" class="pose-panel">
                <div><span>x</span><code>{{ selectedRobot.telemetry.pose.x.toFixed(3) }}</code></div>
                <div><span>y</span><code>{{ selectedRobot.telemetry.pose.y.toFixed(3) }}</code></div>
                <div><span>yaw</span><code>{{ selectedRobot.telemetry.pose.yaw.toFixed(3) }}</code></div>
              </div>
              <div v-else class="pose-empty">暂无位姿数据</div>
              <div class="meta-line">系统模式：{{ selectedRobot.telemetry?.systemMode || '-' }} · SN {{ selectedRobot.serialNo }}</div>
            </div>
          </el-card>

          <el-card shadow="never" class="video-card">
            <template #header>
              <div class="panel-head">
                <span class="table-title">前视相机</span>
                <span class="video-meta">1920×1080</span>
              </div>
            </template>
            <div class="video-shell">
              <div class="video-no-signal">
                <el-icon :size="36"><VideoCameraFilled /></el-icon>
                <p>等待视频流</p>
                <small>相机接入后将在此显示实时画面</small>
              </div>
              <div class="video-badges">
                <span class="badge live"><i /> LIVE</span>
                <span class="badge">{{ videoClock }}</span>
              </div>
            </div>
          </el-card>
        </template>
        <el-card v-else shadow="never"><div class="empty-hint">暂无机器人数据</div></el-card>
      </el-col>
    </el-row>

    <div v-if="selectedRobot" class="summary-bar">
      <span class="summary-item"><strong>{{ selectedRobot.name }}</strong></span>
      <span class="summary-sep">·</span>
      <span class="summary-item">Bridge <em :class="bridgeOnline ? 'ok' : 'bad'">{{ bridgeOnline ? '在线' : '离线' }}</em></span>
      <span class="summary-sep">·</span>
      <span class="summary-item">{{ patrolStateLabel(selectedRobot.telemetry?.patrolState) }}</span>
      <span class="summary-sep">·</span>
      <span class="summary-item">Nav2 {{ nav2StatusLabel(selectedRobot.telemetry?.nav2Status) }}</span>
      <span class="summary-sep">·</span>
      <span class="summary-item">任务 {{ robotTask?.name ?? '空闲' }}</span>
      <span v-if="robotTask" class="summary-progress">
        <el-progress
          :percentage="taskStore.executionFor(robotTask.id)?.progress ?? robotTask.progress"
          :stroke-width="6"
          style="width: 100px"
        />
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Aim,
  Bell,
  FullScreen,
  Location,
  VideoCamera,
  VideoCameraFilled,
} from '@element-plus/icons-vue'
import RosSlamMonitorMap from '@/components/RosSlamMonitorMap.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAlarmStore } from '@/stores/alarm'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useTaskStore } from '@/stores/task'
import type { Robot } from '@/types'
import {
  bridgeReachable,
  mappingStatusLabel,
  nav2StatusLabel,
  patrolStateLabel,
  PLATFORM_STATUS_LABELS,
  sensorFreshness,
} from '@/utils/robotStatus'

const router = useRouter()
const robotStore = useRobotStore()
const taskStore = useTaskStore()
const routeStore = useRouteStore()
const alarmStore = useAlarmStore()

const mapRef = ref<{ recenter: () => void } | null>(null)
const mapShellRef = ref<HTMLElement | null>(null)
const followRobot = ref(false)
const videoClock = ref('')

let clockTimer: ReturnType<typeof setInterval> | undefined

const selectedRobot = computed(() => robotStore.robots[0])
const bridgeOnline = computed(() => selectedRobot.value ? bridgeReachable(selectedRobot.value) : false)

const robotTask = computed(() => {
  const robot = selectedRobot.value
  if (!robot?.currentTaskId) return null
  return taskStore.getTaskById(robot.currentTaskId) ?? null
})

const displayRoute = computed(() => {
  const routeId = robotTask.value?.routeId
  if (routeId) return routeStore.getRouteById(routeId) ?? null
  const siteId = selectedRobot.value?.siteId
  if (siteId) {
    const siteRoutes = routeStore.getRoutesBySite(siteId)
    return siteRoutes.find((r) => r.mapId || r.executorJson?.map_snapshot) ?? siteRoutes[0] ?? null
  }
  return null
})

const robotPos = computed(() => selectedRobot.value?.position ?? null)

const latestAlarm = computed(() => alarmStore.alarms[0] ?? null)

const patrolPillClass = computed(() => {
  const state = selectedRobot.value?.telemetry?.patrolState
  if (state === 'running') return 'ok'
  if (state === 'failed') return 'bad'
  return 'idle'
})

watch(robotPos, () => {
  if (followRobot.value) mapRef.value?.recenter()
})

function recenterMap() {
  mapRef.value?.recenter()
}

async function toggleFullscreen() {
  const el = mapShellRef.value
  if (!el) return
  if (document.fullscreenElement) {
    await document.exitFullscreen().catch(() => {})
  } else {
    await el.requestFullscreen().catch(() => {})
  }
}

function platformStatusLabel(status: keyof typeof PLATFORM_STATUS_LABELS) {
  return PLATFORM_STATUS_LABELS[status]
}

function formatRelativeTime(iso: string) {
  const diffSec = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (diffSec < 60) return `${diffSec} 秒前`
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  return `${Math.floor(diffSec / 3600)} 小时前`
}

function sensorPercent(age?: number | null) {
  if (age == null) return 0
  if (age <= 3) return 100
  if (age <= 10) return 55
  return 20
}

function sensorColor(age?: number | null) {
  if (age == null) return '#c0c4cc'
  if (age <= 3) return '#12b76a'
  if (age <= 10) return '#ff8a00'
  return '#f04438'
}

function nav2Class(status?: string) {
  return status === 'running' ? 'ok' : 'warn'
}

function mappingClass(status?: string) {
  return status === 'running' ? 'ok' : 'idle'
}

function platformClass(status: Robot['status']) {
  if (status === 'ONLINE' || status === 'BUSY') return 'ok'
  return 'bad'
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
.live-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 20px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--pi-border);
  border-radius: var(--pi-card-radius);
  box-shadow: var(--pi-card-shadow);
}

.live-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-width: 0;
}

.live-label {
  color: var(--pi-muted);
  font-weight: 600;
  white-space: nowrap;
}

.live-muted {
  color: #909399;
  font-size: 12px;
}

.live-divider {
  width: 1px;
  height: 20px;
  background: var(--pi-border-soft);
}

.map-card :deep(.el-card__header) {
  padding-bottom: 10px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.map-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.robot-name {
  font-size: 13px;
  color: var(--pi-muted);
  font-weight: 600;
}

.map-status {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #f4f6f8;
  color: #526986;
}

.status-pill i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #909399;
}

.status-pill.ok {
  background: #ecfdf3;
  color: #027a48;
}

.status-pill.ok i {
  background: #12b76a;
}

.status-pill.bad {
  background: #fef3f2;
  color: #b42318;
}

.status-pill.bad i {
  background: #f04438;
}

.status-pill.idle i {
  background: #1768f2;
}

.map-toolbar {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.map-shell {
  height: 560px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #1a3a5c;
  background: #0d2137;
}

.map-shell:fullscreen {
  height: 100vh;
  border-radius: 0;
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.telemetry-card,
.video-card {
  margin-bottom: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.telemetry-section + .telemetry-section {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--pi-border-soft);
}

.section-label {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--pi-muted);
  letter-spacing: 0.3px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.status-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7faff;
  border: 1px solid var(--pi-border-soft);
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #909399;
}

.dot.ok { background: #12b76a; }
.dot.warn { background: #ff8a00; }
.dot.bad { background: #f04438; }
.dot.idle { background: #1768f2; }

.tile-title {
  font-size: 11px;
  color: var(--pi-muted);
}

.tile-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--pi-text);
}

.sensor-row {
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #526986;
}

.sensor-row:last-child {
  margin-bottom: 0;
}

.sensor-text {
  font-size: 11px;
  color: var(--pi-muted);
  white-space: nowrap;
}

.pose-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.pose-panel div {
  padding: 10px;
  border-radius: 8px;
  background: #0d2137;
  color: #fff;
  text-align: center;
}

.pose-panel span {
  display: block;
  font-size: 11px;
  color: #8a9bb0;
  margin-bottom: 4px;
}

.pose-panel code {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 14px;
  font-weight: 700;
}

.pose-empty {
  padding: 14px;
  text-align: center;
  color: #909399;
  font-size: 13px;
  background: #f7faff;
  border-radius: 8px;
}

.meta-line {
  margin-top: 8px;
  font-size: 11px;
  color: var(--pi-muted);
}

.video-meta {
  font-size: 11px;
  color: var(--pi-muted);
}

.video-shell {
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  overflow: hidden;
  background:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    #0a1628;
  background-size: 24px 24px;
}

.video-no-signal {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #6f8099;
}

.video-no-signal p {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #b8c5d6;
}

.video-no-signal small {
  font-size: 12px;
}

.video-badges {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.badge {
  position: absolute;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
}

.badge.live {
  top: 8px;
  left: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(180, 35, 24, 0.85);
}

.badge.live i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: pulse 1.2s ease-in-out infinite;
}

.badge:last-child {
  bottom: 8px;
  right: 8px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.summary-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin-top: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--pi-border);
  border-radius: var(--pi-card-radius);
  box-shadow: var(--pi-card-shadow);
  font-size: 13px;
  color: #526986;
}

.summary-item strong {
  color: var(--pi-text);
}

.summary-item em {
  font-style: normal;
  font-weight: 700;
}

.summary-item em.ok { color: #12b76a; }
.summary-item em.bad { color: #f04438; }

.summary-sep {
  color: #c0c4cc;
}

.summary-progress {
  margin-left: auto;
}

@media (max-width: 1200px) {
  .side-col {
    margin-top: 16px;
  }

  .map-shell {
    height: 480px;
  }

  .summary-progress {
    margin-left: 0;
    width: 100%;
  }
}
</style>
