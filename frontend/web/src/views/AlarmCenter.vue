<template>
  <div>
    <PageHeader title="告警中心" description="实时告警监控、确认与分级处理" :breadcrumbs="[{ label: '监控中心' }, { label: '告警中心' }]">
      <template #actions>
        <el-button v-if="can('alarm:ack')" @click="alarmStore.acknowledgeAll()" :disabled="!alarmStore.unacknowledgedCount">全部确认</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in severityStats" :key="s.label">
        <el-card shadow="never" class="sev-card">
          <div class="sev-val" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="sev-lbl">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="filter-bar">
              <el-input v-model="keyword" placeholder="搜索告警内容" clearable style="width: 200px" size="small" />
              <el-radio-group v-model="filter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="pending">未确认</el-radio-button>
              </el-radio-group>
              <el-select v-model="severityFilter" placeholder="级别" clearable size="small" style="width: 100px">
                <el-option label="紧急" value="CRITICAL" />
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
              </el-select>
            </div>
          </template>
          <el-table :data="filteredAlarms" size="small" @row-click="selectAlarm">
            <el-table-column label="级别" width="80">
              <template #default="{ row }: { row: Alarm }">
                <el-tag :type="severityType(row.severity)" size="small">{{ ALARM_SEVERITY_LABELS[row.severity] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }: { row: Alarm }">{{ DETECTION_LABELS[row.type] }}</template>
            </el-table-column>
            <el-table-column prop="message" label="告警内容" show-overflow-tooltip />
            <el-table-column prop="routeName" label="路线" width="120" show-overflow-tooltip />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.acknowledged ? 'info' : 'danger'" size="small">{{ row.acknowledged ? '已确认' : '待处理' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="can('alarm:ack') || can('workorder:create') || can('task:dispatch')" label="操作" width="240">
              <template #default="{ row }">
                <el-button v-if="can('alarm:ack') && !row.acknowledged" text type="primary" size="small" @click.stop="alarmStore.acknowledge(row.id)">确认</el-button>
                <el-button
                  v-if="can('workorder:create') && !workOrderStore.getByAlarmId(row.id)"
                  text
                  type="warning"
                  size="small"
                  @click.stop="createWorkOrder(row)"
                >转工单</el-button>
                <el-tag v-else-if="workOrderStore.getByAlarmId(row.id)" size="small" type="info">
                  {{ workOrderStore.getByAlarmId(row.id)?.autoConverted ? '已自动转工单' : '已转工单' }}
                </el-tag>
                <el-button
                  v-if="can('task:dispatch')"
                  text
                  type="success"
                  size="small"
                  @click.stop="openAgentForAlarm(row)"
                >Agent 研判</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" v-if="selected">
          <template #header>告警详情</template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="级别">
              <el-tag :type="severityType(selected.severity)">{{ ALARM_SEVERITY_LABELS[selected.severity] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="类型">{{ DETECTION_LABELS[selected.type] }}</el-descriptions-item>
            <el-descriptions-item label="路线">{{ selected.routeName }}</el-descriptions-item>
            <el-descriptions-item label="检查点">{{ selected.checkpointName || '路线行进中' }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ selected.message }}</el-descriptions-item>
          </el-descriptions>
          <img v-if="selected.imageUrl" :src="selected.imageUrl" class="alarm-img" alt="截图" />
          <div v-if="selected" style="margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px">
            <el-button v-if="can('alarm:ack') && !selected.acknowledged" type="primary" size="small" @click="alarmStore.acknowledge(selected.id)">确认告警</el-button>
            <el-button
              v-if="can('workorder:create') && !workOrderStore.getByAlarmId(selected.id)"
              type="warning"
              size="small"
              @click="createWorkOrder(selected)"
            >转为工单</el-button>
            <el-button v-else-if="workOrderStore.getByAlarmId(selected.id)" size="small" @click="router.push('/workorders')">查看工单</el-button>
            <el-button v-if="can('task:dispatch')" type="success" size="small" @click="openAgentForAlarm(selected)">Agent 研判</el-button>
          </div>
        </el-card>
        <el-card v-else shadow="never"><div class="empty-hint">点击告警查看详情</div></el-card>
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>级别分布</template>
          <ChartCard :option="chartOption" :height="180" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChartCard from '@/components/ChartCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useWorkOrderStore } from '@/stores/workOrder'
import type { Alarm, AlarmSeverity } from '@/types'
import { ALARM_SEVERITY_LABELS, DETECTION_LABELS } from '@/types'

const router = useRouter()
const alarmStore = useAlarmStore()
const authStore = useAuthStore()
const workOrderStore = useWorkOrderStore()
const { can } = usePermission()
const filter = ref<'all' | 'pending'>('all')
const severityFilter = ref('')
const keyword = ref('')
const selected = ref<Alarm | null>(alarmStore.alarms[0] ?? null)

const severityStats = computed(() => [
  { label: '紧急', value: alarmStore.alarms.filter((a) => a.severity === 'CRITICAL').length, color: '#f56c6c' },
  { label: '高', value: alarmStore.alarms.filter((a) => a.severity === 'HIGH').length, color: '#e6a23c' },
  { label: '中', value: alarmStore.alarms.filter((a) => a.severity === 'MEDIUM').length, color: '#409eff' },
  { label: '未确认', value: alarmStore.unacknowledgedCount, color: '#909399' },
])

const filteredAlarms = computed(() => {
  let list = alarmStore.alarms
  if (filter.value === 'pending') list = list.filter((a) => !a.acknowledged)
  if (severityFilter.value) list = list.filter((a) => a.severity === severityFilter.value)
  if (keyword.value) list = list.filter((a) => a.message.includes(keyword.value))
  return list
})

const chartOption = computed(() => ({
  series: [{
    type: 'pie',
    radius: '65%',
    data: severityStats.value.slice(0, 3).map((s) => ({ name: s.label, value: s.value || 0 })),
  }],
}))

function selectAlarm(row: Alarm) {
  selected.value = row
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function severityType(s: AlarmSeverity) {
  return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }[s] as 'info' | 'warning' | 'danger'
}

async function createWorkOrder(alarm: Alarm) {
  const user = authStore.user
  if (!user) return
  try {
    const order = await workOrderStore.createFromAlarm(alarm, { id: user.id, name: user.displayName })
    if (!alarm.acknowledged) alarmStore.acknowledge(alarm.id)
    ElMessage.success(`工单 ${order.id} 已创建，请前往工单管理指派调度员`)
    router.push('/workorders')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  }
}

function openAgentForAlarm(alarm: Alarm) {
  router.push({ path: '/agents', query: { alarmId: alarm.id } })
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.sev-card .sev-val {
  font-size: 26px;
  font-weight: 700;
}

.sev-card .sev-lbl {
  font-size: 12px;
  color: #909399;
}

.alarm-img {
  width: 100%;
  margin-top: 12px;
  border-radius: 6px;
}
</style>
