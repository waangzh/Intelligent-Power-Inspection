<template>
  <div>
    <PageHeader
      title="机器人管理"
      description="机器人资产、站点绑定与运行状态管理"
      :breadcrumbs="[{ label: '资产感知' }, { label: '机器人管理' }]"
    />

    <div class="page-actions">
      <el-select v-if="robotStore.robots.length > 1" v-model="selectedRobotId" class="robot-select" aria-label="选择机器人">
        <el-option v-for="item in robotStore.robots" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button :disabled="!robot" @click="openBindingDialog">重新绑定站点</el-button>
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

    <el-empty v-if="!robot" description="未找到机器人设备" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { resourcesApi } from '@/api/resources'
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

const selectedRobotId = ref('')
const bindingDialogVisible = ref(false)
const bindingSaving = ref(false)
const bindingSiteId = ref('')

const robot = computed(() => robotStore.getRobotById(selectedRobotId.value))

watch(
  () => robotStore.robots.map((item) => item.id),
  (ids) => {
    if (!ids.includes(selectedRobotId.value)) selectedRobotId.value = ids[0] ?? ''
  },
  { immediate: true },
)

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

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}
</script>

<style scoped>
.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
  margin: -8px 0 16px;
}

.robot-select {
  width: min(280px, 42vw);
  margin-right: auto;
}

.binding-hint {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
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

@media (max-width: 640px) {
  .page-actions {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .robot-select {
    width: 100%;
    margin-right: 0;
  }
}
</style>
