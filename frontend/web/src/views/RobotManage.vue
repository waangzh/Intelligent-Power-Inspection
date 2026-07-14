<template>
  <div>
    <PageHeader
      title="机器人管理"
      description="单台实机状态监控（对接 mobile bridge :8000）"
      :breadcrumbs="[{ label: '资产感知' }, { label: '机器人管理' }]"
    />

    <div class="page-actions">
      <el-button type="primary" @click="router.push('/robots/status')">查看在线状态</el-button>
    </div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statusStats" :key="s.label">
        <el-card shadow="never" class="mini-stat">
          <div class="val">{{ s.value }}</div>
          <div class="lbl">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="robot" shadow="never">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="名称">{{ robot.name }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ robot.model }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ robot.serialNo }}</el-descriptions-item>
        <el-descriptions-item label="绑定站点">{{ siteName(robot.siteId) }}</el-descriptions-item>
        <el-descriptions-item label="平台状态">
          <el-tag :type="statusType(robot.status)" size="small">{{ platformStatusLabel(robot.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Bridge 连接">
          <el-tag :type="bridgeReachable(robot) ? 'success' : 'danger'" size="small">
            {{ bridgeReachable(robot) ? '已连接' : '未连接' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="巡逻状态">{{ patrolStateLabel(robot.telemetry?.patrolState) }}</el-descriptions-item>
        <el-descriptions-item label="系统模式">{{ robot.telemetry?.systemMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="建图状态">{{ mappingStatusLabel(robot.telemetry?.mappingStatus) }}</el-descriptions-item>
        <el-descriptions-item label="Nav2 状态">{{ nav2StatusLabel(robot.telemetry?.nav2Status) }}</el-descriptions-item>
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
        <el-descriptions-item label="Bridge 地址" :span="2">{{ robot.telemetry?.bridgeBaseUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近同步" :span="2">{{ robot.telemetry?.bridgeSyncedAt ? fmt(robot.telemetry.bridgeSyncedAt) : '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-empty v-else description="未找到机器人设备" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { useRobotStore } from '@/stores/robot'
import { useSiteStore } from '@/stores/site'
import type { Robot } from '@/types'
import {
  bridgeReachable,
  mappingStatusLabel,
  nav2StatusLabel,
  patrolStateLabel,
  PLATFORM_STATUS_LABELS,
  sensorFreshness,
} from '@/utils/robotStatus'

const robotStore = useRobotStore()
const siteStore = useSiteStore()
const router = useRouter()

const robot = computed(() => robotStore.robots[0])

const statusStats = computed(() => {
  const item = robot.value
  const t = item?.telemetry
  return [
    { label: '设备数', value: robotStore.robots.length },
    { label: 'Bridge', value: item && bridgeReachable(item) ? '在线' : '离线' },
    { label: '巡逻', value: patrolStateLabel(t?.patrolState) },
    { label: 'Nav2', value: nav2StatusLabel(t?.nav2Status) },
  ]
})

function siteName(id?: string) {
  return id ? siteStore.getSiteById(id)?.name ?? '-' : '未绑定'
}

function statusType(s: Robot['status']) {
  return { ONLINE: 'success', BUSY: 'warning', OFFLINE: 'danger' }[s] as 'success' | 'warning' | 'danger'
}

function platformStatusLabel(s: Robot['status']) {
  return PLATFORM_STATUS_LABELS[s]
}

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}
</script>

<style scoped>
.page-actions {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 16px;
}

.mini-stat .val {
  font-size: 24px;
  font-weight: 700;
  color: #1a5fb4;
}

.mini-stat .lbl {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
