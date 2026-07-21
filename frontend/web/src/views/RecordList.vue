<template>
  <div class="record-page">
    <PageHeader title="巡检记录" description="历史巡检报告与异常汇总" :breadcrumbs="[{ label: '数据中心' }, { label: '巡检记录' }]">
      <template #actions>
        <el-button v-if="can('record:export')" plain class="export-btn" :loading="exporting" @click="exportRecords">
          <el-icon><Download /></el-icon>
          导出 CSV
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索任务 / 路线名称"
          clearable
          class="filter-search"
          @change="searchRecords"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="filter-item">
          <span class="filter-label">告警</span>
          <el-select v-model="alarmFilter" clearable placeholder="全部" style="width: 120px" @change="searchRecords">
            <el-option label="有告警" value="has" />
            <el-option label="无告警" value="none" />
          </el-select>
        </div>
        <el-button plain @click="resetFilters">
          <el-icon><RefreshRight /></el-icon>
          重置
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-head">
          <span class="table-title">巡检记录</span>
          <span class="record-count">共 {{ filteredRecords.length }} 条</span>
        </div>
      </template>
      <el-table :data="filteredRecords" size="small">
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <el-table-column prop="routeName" label="巡检路线" min-width="120" />
        <el-table-column prop="robotName" label="机器人" width="130" />
        <el-table-column prop="checkpointCount" label="检查点" width="80" align="center" />
        <el-table-column label="告警" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alarmCount > 0 ? 'warning' : 'success'" size="small" effect="light">{{ row.alarmCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="90" />
        <el-table-column prop="summary" label="摘要" show-overflow-tooltip min-width="180" />
        <el-table-column label="完成时间" width="160">
          <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" class-name="actions-col" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button plain size="small" class="action-btn action-detail" @click="showDetail(row)">报告</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <ListPagination :total="taskStore.recordTotal" :page="recordPage" @change="loadRecordPage" />
    </el-card>

    <el-dialog v-model="detailVisible" title="巡检报告" width="680px" class="report-dialog">
      <template v-if="detailRecord">
        <div class="report-summary">
          <div class="report-title">
            <h3>{{ detailRecord.taskName }}</h3>
            <el-tag :type="detailRecord.alarmCount > 0 ? 'warning' : 'success'" size="small" effect="light">
              {{ detailRecord.alarmCount > 0 ? `${detailRecord.alarmCount} 条告警` : '无告警' }}
            </el-tag>
          </div>
          <p>{{ detailRecord.summary }}</p>
        </div>
        <el-descriptions :column="2" border size="small" class="report-meta">
          <el-descriptions-item label="路线">{{ detailRecord.routeName }}</el-descriptions-item>
          <el-descriptions-item label="机器人">{{ detailRecord.robotName }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detailRecord.duration }}</el-descriptions-item>
          <el-descriptions-item label="检查点">{{ detailRecord.checkpointCount }} 个</el-descriptions-item>
        </el-descriptions>
        <div class="report-section-head">
          <span class="table-title">检查点明细</span>
        </div>
        <el-table :data="checkpointRows" size="small" class="report-table">
          <el-table-column prop="name" label="检查点" />
          <el-table-column prop="result" label="检测结果" />
          <el-table-column prop="alarm" label="告警" width="80" align="center" />
        </el-table>
        <div class="report-footer">
          <el-button v-if="can('record:export')" type="primary" :loading="exporting" @click="exportRecords">导出 CSV</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, RefreshRight, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import { usePermission } from '@/composables/usePermission'
import { resourcesApi } from '@/api/resources'
import { useTaskStore } from '@/stores/task'
import type { InspectionRecord } from '@/types'

const taskStore = useTaskStore()
const { can } = usePermission()
const keyword = ref('')
const alarmFilter = ref('')
const detailVisible = ref(false)
const detailRecord = ref<InspectionRecord | null>(null)
const exporting = ref(false)
const recordPage = ref(0)

function loadRecordPage(page: number) {
  recordPage.value = page
  void taskStore.loadRecords({ page, size: 20, q: keyword.value })
}

function searchRecords() {
  loadRecordPage(0)
}

function resetFilters() {
  keyword.value = ''
  alarmFilter.value = ''
  searchRecords()
}

const filteredRecords = computed(() => {
  let list = taskStore.records
  if (keyword.value) {
    list = list.filter(
      (r) => r.taskName.includes(keyword.value) || r.routeName.includes(keyword.value),
    )
  }
  if (alarmFilter.value === 'has') list = list.filter((r) => r.alarmCount > 0)
  if (alarmFilter.value === 'none') list = list.filter((r) => r.alarmCount === 0)
  return list
})

const checkpointRows = computed(() => {
  const n = detailRecord.value?.checkpointCount ?? 3
  return Array.from({ length: n }, (_, i) => ({
    name: `检查点 ${i + 1}`,
    result: i === 1 && (detailRecord.value?.alarmCount ?? 0) > 0 ? '刀闸异常' : '正常',
    alarm: i === 1 && (detailRecord.value?.alarmCount ?? 0) > 0 ? '1' : '0',
  }))
})

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function showDetail(record: InspectionRecord) {
  detailRecord.value = record
  detailVisible.value = true
}

async function exportRecords() {
  exporting.value = true
  try {
    const blob = await resourcesApi.exportRecords()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `inspection-records-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${filteredRecords.value.length} 条记录`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.filter-search {
  width: min(280px, 100%);
}

.filter-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: var(--pi-muted);
  white-space: nowrap;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.export-btn {
  border-radius: 8px;
}

.report-summary {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #f6f8fb;
  border: 1px solid var(--pi-border-soft);
}

.report-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.report-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--pi-text);
}

.report-summary p {
  margin: 0;
  font-size: 13px;
  color: var(--pi-muted);
  line-height: 1.55;
}

.report-meta {
  margin-bottom: 16px;
}

.report-section-head {
  margin-bottom: 10px;
}

.report-footer {
  margin-top: 14px;
  text-align: right;
}
</style>
