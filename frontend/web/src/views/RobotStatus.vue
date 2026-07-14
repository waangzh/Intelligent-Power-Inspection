<template>
  <div class="robot-status-page">
    <PageHeader
      title="机器人在线状态"
      description="由公网 Robot Bridge 心跳同步；状态由平台服务端计算，每 15 秒刷新一次。"
      :breadcrumbs="[{ label: '资产感知' }, { label: '机器人在线状态' }]"
    />

    <section class="status-strip" aria-label="机器人状态汇总">
      <div class="strip-intro">
        <span class="eyebrow">ROBOT HEARTBEAT</span>
        <strong>当前筛选页统计</strong>
      </div>
      <div class="metric online"><b>{{ summary.online }}</b><span>在线（当前页）</span></div>
      <div class="metric offline"><b>{{ summary.offline }}</b><span>离线（当前页）</span></div>
      <div class="metric unknown"><b>{{ summary.unknown }}</b><span>未知 / 配置异常（当前页）</span></div>
      <div class="refresh-note">轮询刷新 · {{ refreshedAt ? formatTime(refreshedAt) : '等待首次刷新' }}</div>
    </section>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-header">
          <div>
            <span class="table-title">心跳台账</span>
            <span class="table-subtitle">不显示设备令牌、签名或原始健康载荷</span>
          </div>
          <div class="table-actions">
            <el-select v-model="statusFilter" size="small" class="status-filter" aria-label="按连接状态筛选">
              <el-option label="全部状态" value="ALL" />
              <el-option label="已连接" value="CONNECTED" />
              <el-option label="离线" value="OFFLINE" />
              <el-option label="未知" value="UNKNOWN" />
              <el-option label="Bridge 不可达" value="BRIDGE_UNREACHABLE" />
              <el-option label="Bridge 未配置" value="BRIDGE_UNCONFIGURED" />
            </el-select>
            <el-button :loading="loading" size="small" @click="refresh">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="errorMessage" class="load-alert" type="error" :closable="false" show-icon>
        <template #title>无法取得机器人状态</template>
        <template #default>
          <span>{{ errorMessage }}。当前页面不会推断或伪造在线状态。</span>
          <el-button link type="primary" @click="refresh">重试</el-button>
        </template>
      </el-alert>

      <el-empty v-else-if="!loading && listState === 'empty'" description="暂无已登记的机器人身份或心跳状态" />

      <el-table v-else :data="items" v-loading="loading" class="status-table" empty-text="暂无机器人状态">
        <el-table-column label="机器人" min-width="190">
          <template #default="{ row }">
            <div class="robot-cell">
              <span class="robot-dot" :class="{ active: row.online }" />
              <div>
                <strong>{{ row.displayName || row.robotId }}</strong>
                <small>{{ row.robotId }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" width="118">
          <template #default="{ row }">
            <el-tag size="small" :type="heartbeatVisual(row)">{{ row.online ? '在线' : connectionLabel(row.connectionStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近心跳" min-width="176">
          <template #default="{ row }">{{ formatTime(row.lastHeartbeatAt) }}</template>
        </el-table-column>
        <el-table-column label="离线 / 状态原因" min-width="190">
          <template #default="{ row }">{{ row.online ? '心跳正常' : offlineReasonLabel(row.offlineReason) }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="125">
          <template #default="{ row }">{{ row.source?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="76" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row.robotId)">详情</el-button></template>
        </el-table-column>
      </el-table>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="refresh" />
      </div>
    </el-card>

    <el-drawer v-model="drawerOpen" title="机器人心跳详情" size="480px">
      <el-skeleton v-if="detailLoading" :rows="7" animated />
      <template v-else-if="selected">
        <div class="detail-identity">
          <span class="robot-dot large" :class="{ active: selected.online }" />
          <div><strong>{{ selected.displayName || selected.robotId }}</strong><span>{{ selected.robotId }}</span></div>
          <el-tag :type="heartbeatVisual(selected)">{{ selected.online ? '在线' : connectionLabel(selected.connectionStatus) }}</el-tag>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="序列号">{{ selected.serialNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="连接状态">{{ connectionLabel(selected.connectionStatus) }}</el-descriptions-item>
          <el-descriptions-item label="最近心跳">{{ formatTime(selected.lastHeartbeatAt) }}</el-descriptions-item>
          <el-descriptions-item label="最后在线">{{ formatTime(selected.lastOnlineAt) }}</el-descriptions-item>
          <el-descriptions-item label="状态更新时间">{{ formatTime(selected.statusUpdatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="状态原因">{{ selected.online ? '心跳正常' : offlineReasonLabel(selected.offlineReason) }}</el-descriptions-item>
          <el-descriptions-item label="Bridge 来源">{{ selected.source?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="协议 / 软件">{{ selected.protocolVersion || '-' }} / {{ selected.softwareVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运行状态">{{ selected.robotState || '-' }}</el-descriptions-item>
          <el-descriptions-item label="诊断摘要">{{ selected.diagnosticSummary || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="未找到状态详情" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { resourcesApi } from '@/api/resources'
import type { RobotConnectionStatus, RobotHeartbeatStatus } from '@/types/robotHeartbeat'
import { connectionLabel, heartbeatListState, heartbeatVisual, offlineReasonLabel } from '@/utils/robotHeartbeatStatus'

const pageSize = 20
const items = ref<RobotHeartbeatStatus[]>([])
const total = ref(0)
const page = ref(1)
const statusFilter = ref<'ALL' | RobotConnectionStatus>('ALL')
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const refreshedAt = ref<string>()
const drawerOpen = ref(false)
const selected = ref<RobotHeartbeatStatus>()

const listState = computed(() => heartbeatListState(items.value, Boolean(errorMessage.value)))
const summary = computed(() => ({
  online: items.value.filter(item => item.online).length,
  offline: items.value.filter(item => !item.online && ['OFFLINE', 'BRIDGE_UNREACHABLE'].includes(item.connectionStatus)).length,
  unknown: items.value.filter(item => !item.online && !['OFFLINE', 'BRIDGE_UNREACHABLE'].includes(item.connectionStatus)).length,
}))

async function refresh() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await resourcesApi.listRobotHeartbeatStatus({
      connectionStatus: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      sort: 'lastHeartbeatAt',
      direction: 'desc',
      page: page.value - 1,
      size: pageSize,
    })
    items.value = result.items
    total.value = result.total
    refreshedAt.value = new Date().toISOString()
  } catch (error) {
    items.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '状态接口暂不可用'
  } finally {
    loading.value = false
  }
}

async function openDetail(robotId: string) {
  drawerOpen.value = true
  detailLoading.value = true
  selected.value = undefined
  try {
    selected.value = await resourcesApi.getRobotHeartbeatStatus(robotId)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载详情'
  } finally {
    detailLoading.value = false
  }
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}

watch(statusFilter, () => {
  page.value = 1
  void refresh()
})

let pollTimer: number | undefined
onMounted(() => {
  void refresh()
  pollTimer = window.setInterval(() => void refresh(), 15_000)
})
onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<style scoped>
.robot-status-page { --ink: #183246; --line: #dce7ec; --cyan: #0b9cb2; --amber: #d68a22; }
.status-strip { display: grid; grid-template-columns: minmax(180px, 1.5fr) repeat(3, minmax(110px, .7fr)) minmax(175px, 1fr); align-items: stretch; margin-bottom: 18px; color: #eaf7fa; background: linear-gradient(105deg, #173748, #19596a 54%, #123a50); border: 1px solid #2b7181; box-shadow: 0 9px 22px rgb(17 59 76 / 14%); }
.strip-intro, .metric, .refresh-note { min-height: 82px; padding: 17px 20px; border-right: 1px solid rgb(228 250 255 / 18%); display: flex; flex-direction: column; justify-content: center; }
.strip-intro strong { font-size: 20px; letter-spacing: .04em; }
.eyebrow { color: #83dce9; font-size: 10px; font-weight: 700; letter-spacing: .14em; margin-bottom: 5px; }
.metric b { font-size: 26px; line-height: 1; font-variant-numeric: tabular-nums; }.metric span, .refresh-note { font-size: 12px; color: #b8d5dc; margin-top: 6px; }
.metric.online b { color: #7ce0b4; }.metric.offline b { color: #ffd182; }.metric.unknown b { color: #b9d8ed; }.refresh-note { border: 0; align-items: flex-end; text-align: right; }
.table-card { border-color: var(--line); }.table-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.table-title { color: var(--ink); font-weight: 700; letter-spacing: .04em; }.table-subtitle { display: block; margin-top: 4px; color: #81929b; font-size: 12px; }.table-actions { display: flex; gap: 8px; }.status-filter { width: 155px; }.load-alert { margin-bottom: 14px; }.status-table :deep(th.el-table__cell) { background: #f4f8f9; color: #57707e; font-size: 12px; letter-spacing: .04em; }.robot-cell, .detail-identity { display: flex; gap: 10px; align-items: center; }.robot-cell strong { color: #213b4d; display: block; }.robot-cell small { display: block; margin-top: 2px; color: #82939d; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }.robot-dot { width: 9px; height: 9px; border-radius: 50%; background: #a8b6bd; box-shadow: 0 0 0 3px #eef3f5; flex: none; }.robot-dot.active { background: #16ad79; box-shadow: 0 0 0 3px #dff6eb; }.robot-dot.large { width: 12px; height: 12px; }.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 16px; }.detail-identity { padding: 2px 0 18px; }.detail-identity div { flex: 1; }.detail-identity strong, .detail-identity span { display: block; }.detail-identity span { color: #7d8d96; font-size: 12px; margin-top: 4px; }
@media (max-width: 900px) { .status-strip { grid-template-columns: repeat(3, 1fr); }.strip-intro { grid-column: 1 / -1; min-height: 64px; }.refresh-note { display: none; } }
@media (max-width: 580px) { .status-strip { grid-template-columns: repeat(3, 1fr); }.strip-intro, .metric { padding: 13px; }.table-header { align-items: flex-start; flex-direction: column; }.table-actions { width: 100%; }.status-filter { flex: 1; } }
</style>
