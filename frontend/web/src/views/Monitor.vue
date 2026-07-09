<template>
  <div>
    <PageHeader
      title="实时监控"
      description="机器人位姿、任务进度与视频画面（演示）"
      :breadcrumbs="[{ label: '监控中心' }, { label: '实时监控' }]"
    />

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>地图实时追踪</span>
              <el-select v-model="selectedRobotId" size="small" style="width: 180px">
                <el-option v-for="r in robotStore.robots" :key="r.id" :label="r.name" :value="r.id" />
              </el-select>
            </div>
          </template>
          <div style="height: 400px">
            <Map2D
              :center="mapCenter"
              :fallback-center="fallbackCenter"
              :route="activeRoute"
              :robot-position="robotPos"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>遥测数据</template>
          <el-descriptions v-if="selectedRobot" :column="1" border size="small">
            <el-descriptions-item label="名称">{{ selectedRobot.name }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ selectedRobot.model }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small">{{ selectedRobot.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="电量">
              <el-progress :percentage="selectedRobot.battery" :stroke-width="10" />
            </el-descriptions-item>
            <el-descriptions-item label="固件">{{ selectedRobot.firmware }}</el-descriptions-item>
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
      <template #header>全部机器人在线状态</template>
      <el-table :data="robotStore.robots" size="small">
        <el-table-column prop="name" label="机器人" />
        <el-table-column prop="model" label="型号" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : row.status === 'BUSY' ? 'warning' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="电量" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.battery" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="当前任务" min-width="120">
          <template #default="{ row }">{{ taskName(row.currentTaskId) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRobotStore } from '@/stores/robot'
import { useRouteStore } from '@/stores/route'
import { useSiteStore } from '@/stores/site'
import { useTaskStore } from '@/stores/task'

const robotStore = useRobotStore()
const taskStore = useTaskStore()
const routeStore = useRouteStore()
const siteStore = useSiteStore()

const selectedRobotId = ref(robotStore.robots[0]?.id ?? '')
watch(
  () => robotStore.robots.map((robot) => robot.id),
  (ids) => {
    if (ids.length > 0 && !ids.includes(selectedRobotId.value)) {
      selectedRobotId.value = ids[0]
    }
  },
  { immediate: true },
)

const selectedRobot = computed(() => robotStore.getRobotById(selectedRobotId.value))
const activeTask = computed(() => taskStore.getActiveTask())
const activeRoute = computed(() =>
  activeTask.value ? routeStore.getRouteById(activeTask.value.routeId) ?? null : null,
)
const mapCenter = computed(() => {
  const r = selectedRobot.value
  if (r?.position) return r.position
  if (r?.siteId) return siteStore.getSiteById(r.siteId)?.center ?? { lat: 30.27, lng: 120.15 }
  return { lat: 30.27, lng: 120.15 }
})
const fallbackCenter = computed(() => {
  const siteId = selectedRobot.value?.siteId
  return siteId ? siteStore.getSiteById(siteId)?.center ?? mapCenter.value : mapCenter.value
})
const robotPos = computed(() => selectedRobot.value?.position ?? null)
const videoUrl = computed(() => `https://picsum.photos/seed/monitor_${selectedRobotId.value}/640/360`)

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
