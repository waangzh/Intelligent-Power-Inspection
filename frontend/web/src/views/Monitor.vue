<template>
  <div>
    <PageHeader
      title="实时监控"
      description="基于 ROS 建图的机器人位姿追踪（对接实机 mobile bridge）"
      :breadcrumbs="[{ label: '监控中心' }, { label: '实时监控' }]"
    />

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>地图实时追踪 · {{ selectedRobot?.name ?? '未连接' }}</span>
              <el-tag size="small" :type="selectedRobot && bridgeReachable(selectedRobot) ? 'success' : 'info'">
                {{ selectedRobot ? patrolStateLabel(selectedRobot.telemetry?.patrolState) : '无设备' }}
              </el-tag>
            </div>
          </template>
          <div class="map-panel">
            <RosSlamMonitorMap :route="displayRoute" :robot-position="robotPos" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>实机遥测</template>
          <el-descriptions v-if="selectedRobot" :column="1" border size="small">
            <el-descriptions-item label="名称">{{ selectedRobot.name }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ selectedRobot.model }}</el-descriptions-item>
            <el-descriptions-item label="平台状态">
              <el-tag size="small">{{ platformStatusLabel(selectedRobot.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="巡逻状态">{{ patrolStateLabel(selectedRobot.telemetry?.patrolState) }}</el-descriptions-item>
            <el-descriptions-item label="系统模式">{{ selectedRobot.telemetry?.systemMode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="建图">{{ mappingStatusLabel(selectedRobot.telemetry?.mappingStatus) }}</el-descriptions-item>
            <el-descriptions-item label="Nav2">{{ nav2StatusLabel(selectedRobot.telemetry?.nav2Status) }}</el-descriptions-item>
            <el-descriptions-item label="里程计">{{ sensorFreshness(selectedRobot.telemetry?.lastOdomAgeSec) }}</el-descriptions-item>
            <el-descriptions-item label="雷达">{{ sensorFreshness(selectedRobot.telemetry?.lastScanAgeSec) }}</el-descriptions-item>
            <el-descriptions-item label="位姿">
              <span v-if="selectedRobot.telemetry?.pose">
                x={{ selectedRobot.telemetry.pose.x.toFixed(2) }},
                y={{ selectedRobot.telemetry.pose.y.toFixed(2) }}
              </span>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="序列号">{{ selectedRobot.serialNo }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-card shadow="never">
          <template #header>模拟视频画面</template>
          <div class="video-placeholder">
            <img :src="videoUrl" alt="监控画面" />
            <div class="video-overlay">LIVE · 前视相机</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>设备运行状态</template>
      <el-table :data="robotStore.robots" size="small">
        <el-table-column prop="name" label="机器人" />
        <el-table-column prop="model" label="型号" />
        <el-table-column label="Bridge" width="90">
          <template #default="{ row }">
            <el-tag :type="bridgeReachable(row) ? 'success' : 'danger'" size="small">
              {{ bridgeReachable(row) ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="巡逻" width="100">
          <template #default="{ row }">{{ patrolStateLabel(row.telemetry?.patrolState) }}</template>
        </el-table-column>
        <el-table-column label="Nav2" width="100">
          <template #default="{ row }">{{ nav2StatusLabel(row.telemetry?.nav2Status) }}</template>
        </el-table-column>
        <el-table-column label="当前任务" min-width="120">
          <template #default="{ row }">{{ taskName(row.currentTaskId) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import RosSlamMonitorMap from '@/components/RosSlamMonitorMap.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useTaskStore } from '@/stores/task'
import {
  bridgeReachable,
  mappingStatusLabel,
  nav2StatusLabel,
  patrolStateLabel,
  PLATFORM_STATUS_LABELS,
  sensorFreshness,
} from '@/utils/robotStatus'

const robotStore = useRobotStore()
const taskStore = useTaskStore()
const routeStore = useRouteStore()

const selectedRobot = computed(() => robotStore.robots[0])
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
const videoUrl = computed(() => `https://picsum.photos/seed/monitor_${selectedRobot.value?.id ?? 'robot'}/640/360`)

function platformStatusLabel(status: keyof typeof PLATFORM_STATUS_LABELS) {
  return PLATFORM_STATUS_LABELS[status]
}

function taskName(taskId?: string) {
  if (!taskId) return '-'
  return taskStore.getTaskById(taskId)?.name ?? '-'
}
</script>

<style scoped>
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.map-panel {
  height: 400px;
}

.video-placeholder {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
}

.video-placeholder img {
  width: 100%;
  display: block;
}

.video-overlay {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;
}
</style>
