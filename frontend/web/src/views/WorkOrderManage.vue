<template>
  <div>
    <PageHeader title="工单管理" description="告警转工单、指派处置与闭环复核" :breadcrumbs="[{ label: '运维中心' }, { label: '工单管理' }]">
      <template #actions>
        <el-button @click="router.push('/alarms')">从告警创建</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="12" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statusCards" :key="s.key">
        <el-card shadow="never" class="stat-card" :class="{ active: statusFilter === s.key }" @click="statusFilter = s.key">
          <div class="stat-val">{{ s.value }}</div>
          <div class="stat-lbl">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-table :data="filteredOrders" size="small" @row-click="openDetail">
        <el-table-column prop="id" label="工单号" width="130" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }: { row: WorkOrder }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ WORK_ORDER_PRIORITY_LABELS[row.priority] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }: { row: WorkOrder }">
            <el-tag :type="statusType(row.status)" size="small">{{ WORK_ORDER_STATUS_LABELS[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="处理人" width="100" />
        <el-table-column prop="createdByName" label="创建人" width="100" />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="can('task:dispatch')" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" text type="primary" size="small" @click.stop="advance(row.id, 'PROCESSING')">接单</el-button>
            <el-button v-if="row.status === 'PROCESSING'" text type="success" size="small" @click.stop="openResolve(row)">提交复核</el-button>
            <el-button v-if="row.status === 'REVIEW'" text type="primary" size="small" @click.stop="closeOrder(row.id)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="工单详情" width="560px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="工单号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.description }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ WORK_ORDER_STATUS_LABELS[detail.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.assigneeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理说明">{{ detail.resolution || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" title="提交处理结果" width="480px">
      <el-input v-model="resolveText" type="textarea" :rows="4" placeholder="请填写现场处理情况" />
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResolve">提交复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { useWorkOrderStore } from '@/stores/workOrder'
import { usePermission } from '@/composables/usePermission'
import type { WorkOrder, WorkOrderPriority, WorkOrderStatus } from '@/types/workOrder'
import { WORK_ORDER_PRIORITY_LABELS, WORK_ORDER_STATUS_LABELS } from '@/types/workOrder'

const router = useRouter()
const workOrderStore = useWorkOrderStore()
const { can } = usePermission()
const statusFilter = ref<string>('ALL')
const detailVisible = ref(false)
const detail = ref<WorkOrder | null>(null)
const resolveVisible = ref(false)
const resolveText = ref('')
const resolvingId = ref('')

const statusCards = computed(() => {
  const c = workOrderStore.statusCounts
  return [
    { key: 'ALL', label: '全部', value: workOrderStore.orders.length },
    { key: 'PENDING', label: '待处理', value: c.PENDING },
    { key: 'PROCESSING', label: '处理中', value: c.PROCESSING },
    { key: 'REVIEW', label: '待复核', value: c.REVIEW },
  ]
})

const filteredOrders = computed(() => {
  if (statusFilter.value === 'ALL') return workOrderStore.orders
  return workOrderStore.orders.filter((o) => o.status === statusFilter.value)
})

function openDetail(row: WorkOrder) {
  detail.value = row
  detailVisible.value = true
}

function advance(id: string, status: WorkOrderStatus) {
  workOrderStore.updateStatus(id, status)
  ElMessage.success('状态已更新')
}

function openResolve(row: WorkOrder) {
  resolvingId.value = row.id
  resolveText.value = ''
  resolveVisible.value = true
}

function submitResolve() {
  if (!resolveText.value.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  workOrderStore.updateStatus(resolvingId.value, 'REVIEW', { resolution: resolveText.value })
  resolveVisible.value = false
  ElMessage.success('已提交复核')
}

function closeOrder(id: string) {
  workOrderStore.updateStatus(id, 'CLOSED')
  ElMessage.success('工单已关闭')
}

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function statusType(s: WorkOrderStatus) {
  return { PENDING: 'danger', PROCESSING: 'warning', REVIEW: '', CLOSED: 'success', CANCELLED: 'info' }[s] as '' | 'success' | 'warning' | 'danger' | 'info'
}

function priorityType(p: WorkOrderPriority) {
  return { LOW: 'info', MEDIUM: '', HIGH: 'warning', URGENT: 'danger' }[p] as '' | 'warning' | 'danger' | 'info'
}
</script>

<style scoped>
.stat-card {
  cursor: pointer;
  text-align: center;
  transition: border-color 0.2s;
}

.stat-card.active {
  border-color: #1a5fb4;
}

.stat-val {
  font-size: 24px;
  font-weight: 700;
  color: #1a5fb4;
}

.stat-lbl {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
