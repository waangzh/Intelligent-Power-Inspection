<template>
  <div>
    <PageHeader title="巡检记录" description="历史巡检报告与异常汇总" :breadcrumbs="[{ label: '数据中心' }, { label: '巡检记录' }]">
      <template #actions>
        <el-button v-if="can('record:export')" :loading="exporting" @click="exportRecords">
          <el-icon><Download /></el-icon>
          导出 CSV
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form :inline="true" size="small">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="任务/路线名称" clearable style="width: 180px" @change="searchRecords" />
        </el-form-item>
        <el-form-item label="告警">
          <el-select v-model="alarmFilter" clearable style="width: 120px">
            <el-option label="有告警" value="has" />
            <el-option label="无告警" value="none" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="filteredRecords" size="small">
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <el-table-column prop="routeName" label="巡检路线" min-width="120" />
        <el-table-column prop="robotName" label="机器人" width="130" />
        <el-table-column prop="checkpointCount" label="检查点" width="80" align="center" />
        <el-table-column label="告警" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alarmCount > 0 ? 'warning' : 'success'" size="small">{{ row.alarmCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="90" />
        <el-table-column prop="summary" label="摘要" show-overflow-tooltip min-width="180" />
        <el-table-column label="完成时间" width="160">
          <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="showDetail(row)">报告</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPagination :total="taskStore.recordTotal" :page="recordPage" @change="loadRecordPage" />
    </el-card>

    <el-dialog v-model="detailVisible" title="巡检报告" width="640px">
      <template v-if="detailRecord">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务">{{ detailRecord.taskName }}</el-descriptions-item>
          <el-descriptions-item label="路线">{{ detailRecord.routeName }}</el-descriptions-item>
          <el-descriptions-item label="机器人">{{ detailRecord.robotName }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detailRecord.duration }}</el-descriptions-item>
          <el-descriptions-item label="检查点" :span="2">{{ detailRecord.checkpointCount }} 个</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detailRecord.summary }}</el-descriptions-item>
        </el-descriptions>
        <el-divider>检查点明细（演示）</el-divider>
        <el-table :data="checkpointRows" size="small">
          <el-table-column prop="name" label="检查点" />
          <el-table-column prop="result" label="检测结果" />
          <el-table-column prop="alarm" label="告警" width="80" />
        </el-table>
        <div style="margin-top: 12px; text-align: right">
          <el-button v-if="can('record:export')" type="primary" :loading="exporting" @click="exportRecords">导出 CSV</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
