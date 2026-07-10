<template>
  <div>
    <PageHeader
      title="工单管理"
      description="管理员指派/改派与复核，调度员现场处置并提交复核"
      :breadcrumbs="[{ label: '运维中心' }, { label: '工单管理' }]"
    >
      <template #actions>
        <el-button v-if="can('workorder:create')" @click="router.push('/alarms')">从告警创建</el-button>
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
      <el-table :data="visibleOrders" size="small" @row-click="openDetail">
        <el-table-column prop="id" label="工单号" width="130" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="地点" min-width="140" show-overflow-tooltip>
          <template #default="{ row }: { row: WorkOrder }">
            {{ locationLabel(row) }}
          </template>
        </el-table-column>
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
        <el-table-column label="处理人" width="100">
          <template #default="{ row }: { row: WorkOrder }">
            {{ workOrderAssigneeLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="can('workorder:assign') || can('workorder:process') || can('workorder:review')" label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="can('workorder:assign') && canAssignOrder(row)"
              text
              type="primary"
              size="small"
              @click.stop="openAssign(row)"
            >{{ assignActionLabel(row) }}</el-button>
            <el-button
              v-if="canProcessOrder(row) && row.status === 'PROCESSING'"
              text
              type="success"
              size="small"
              @click.stop="openResolve(row)"
            >提交复核</el-button>
            <el-button
              v-if="can('workorder:review') && row.status === 'REVIEW'"
              text
              type="primary"
              size="small"
              @click.stop="openReview(row)"
            >确认复核</el-button>
            <el-button text type="info" size="small" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="工单详情" width="640px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="工单号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.description }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ WORK_ORDER_STATUS_LABELS[detail.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag :type="priorityType(detail.priority)" size="small">{{ WORK_ORDER_PRIORITY_LABELS[detail.priority] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{ workOrderAssigneeLabel(detail) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdByName }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.autoConverted" label="来源">告警自动转工单</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">具体地点</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="站点">{{ detail.location?.siteName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="区域/路线">{{ detail.location?.areaName || detail.location?.routeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检查点">{{ detail.location?.checkpointName || '路线行进中' }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ detail.location?.address || '-' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.resolutionForm">
          <div class="section-title">现场处理记录</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="故障类型">{{ detail.resolutionForm.faultType }}</el-descriptions-item>
            <el-descriptions-item label="处理方式">{{ detail.resolutionForm.handlingMethod }}</el-descriptions-item>
            <el-descriptions-item label="更换部件">{{ detail.resolutionForm.replacedParts || '-' }}</el-descriptions-item>
            <el-descriptions-item label="试验结果">{{ detail.resolutionForm.testResult }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ detail.resolutionForm.remarks || '-' }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ detail.resolutionForm.submittedBy }} · {{ fmt(detail.resolutionForm.submittedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <template v-if="detail.reviewForm">
          <div class="section-title">管理员复核</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="复核结果">
              <el-tag :type="detail.reviewForm.result === 'PASS' ? 'success' : 'danger'" size="small">
                {{ detail.reviewForm.result === 'PASS' ? '通过' : '退回' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="复核意见">{{ detail.reviewForm.comment }}</el-descriptions-item>
            <el-descriptions-item label="复核人">{{ detail.reviewForm.reviewedBy }} · {{ fmt(detail.reviewForm.reviewedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" :title="assignDialogTitle" width="420px">
      <el-form label-width="88px" size="small">
        <el-form-item label="调度员" required>
          <el-select v-model="assignForm.userId" placeholder="选择处理人" style="width: 100%" filterable>
            <el-option v-for="u in dispatchers" :key="u.id" :label="`${u.displayName}（${u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">确认指派</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" title="提交现场处理结果" width="520px">
      <el-form :model="resolveForm" label-width="96px" size="small">
        <el-form-item label="故障类型" required>
          <el-select v-model="resolveForm.faultType" placeholder="请选择" style="width: 100%">
            <el-option v-for="o in FAULT_TYPE_OPTIONS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理方式" required>
          <el-select v-model="resolveForm.handlingMethod" placeholder="请选择" style="width: 100%">
            <el-option v-for="o in HANDLING_METHOD_OPTIONS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="更换部件">
          <el-input v-model="resolveForm.replacedParts" placeholder="如有更换请填写部件名称/型号" />
        </el-form-item>
        <el-form-item label="试验结果" required>
          <el-input v-model="resolveForm.testResult" type="textarea" :rows="2" placeholder="如：复测正常、绝缘合格等" />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="resolveForm.remarks" type="textarea" :rows="2" placeholder="其他现场情况" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResolve">提交复核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="管理员复核确认" width="480px">
      <el-form :model="reviewForm" label-width="96px" size="small">
        <el-form-item label="复核结果" required>
          <el-radio-group v-model="reviewForm.result">
            <el-radio value="PASS">通过并关闭</el-radio>
            <el-radio value="REJECT">退回重做</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核意见" required>
          <el-input v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="请填写复核结论与依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">确认复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { useWorkOrderStore } from '@/stores/workOrder'
import { usePermission } from '@/composables/usePermission'
import type { WorkOrder, WorkOrderPriority, WorkOrderStatus } from '@/types/workOrder'
import {
  FAULT_TYPE_OPTIONS,
  HANDLING_METHOD_OPTIONS,
  WORK_ORDER_PRIORITY_LABELS,
  WORK_ORDER_STATUS_LABELS,
} from '@/types/workOrder'
import { isWorkOrderUnassigned, workOrderAssigneeLabel } from '@/utils/workOrder'

const router = useRouter()
const workOrderStore = useWorkOrderStore()
const userStore = useUserStore()
const authStore = useAuthStore()
const { can, role } = usePermission()
const statusFilter = ref<string>('ALL')
const detailVisible = ref(false)
const detail = ref<WorkOrder | null>(null)
const resolveVisible = ref(false)
const reviewVisible = ref(false)
const assignVisible = ref(false)
const resolvingId = ref('')
const reviewingId = ref('')
const assigningOrder = ref<WorkOrder | null>(null)
const submitting = ref(false)

const assignForm = reactive({ userId: '' })

const resolveForm = reactive({
  faultType: '',
  handlingMethod: '',
  replacedParts: '',
  testResult: '',
  remarks: '',
})

const reviewForm = reactive({
  result: 'PASS' as 'PASS' | 'REJECT',
  comment: '',
})

const dispatchers = computed(() => userStore.users.filter((u) => u.role === 'DISPATCHER' && u.enabled !== false))

const statusCards = computed(() => {
  const orders = visibleOrders.value
  const count = (status: WorkOrderStatus) => orders.filter((o) => o.status === status).length
  return [
    { key: 'ALL', label: '全部', value: orders.length },
    { key: 'PENDING', label: '待处理', value: count('PENDING') },
    { key: 'PROCESSING', label: '处理中', value: count('PROCESSING') },
    { key: 'REVIEW', label: '待复核', value: count('REVIEW') },
  ]
})

const visibleOrders = computed(() => {
  let list = workOrderStore.orders
  if (role.value === 'DISPATCHER') {
    const me = authStore.user
    if (!me) return []
    list = list.filter((o) => o.assigneeId === me.id || o.assigneeName === me.displayName)
  }
  if (statusFilter.value === 'ALL') return list
  return list.filter((o) => o.status === statusFilter.value)
})

const assignDialogTitle = computed(() =>
  assigningOrder.value && !isWorkOrderUnassigned(assigningOrder.value) ? '改派调度员' : '指派调度员',
)

function canAssignOrder(row: WorkOrder) {
  return row.status === 'PENDING' || row.status === 'PROCESSING'
}

function assignActionLabel(row: WorkOrder) {
  return row.status === 'PENDING' && isWorkOrderUnassigned(row) ? '指派' : '改派'
}

function canProcessOrder(row: WorkOrder) {
  if (role.value !== 'DISPATCHER' || !can('workorder:process')) return false
  if (row.status !== 'PROCESSING') return false
  const me = authStore.user
  if (!me) return false
  return row.assigneeId === me.id || row.assigneeName === me.displayName
}

function locationLabel(row: WorkOrder) {
  return row.location?.checkpointName
    ? `${row.location.routeName || ''} · ${row.location.checkpointName}`
    : row.location?.areaName || row.location?.routeName || '-'
}

function openDetail(row: WorkOrder) {
  detail.value = row
  detailVisible.value = true
}

function openAssign(row: WorkOrder) {
  assigningOrder.value = row
  assignForm.userId = row.assigneeId || dispatchers.value.find((u) => u.displayName === row.assigneeName)?.id || ''
  assignVisible.value = true
}

async function submitAssign() {
  const dispatcher = dispatchers.value.find((u) => u.id === assignForm.userId)
  if (!dispatcher) {
    ElMessage.warning('请选择调度员')
    return
  }
  const order = assigningOrder.value
  if (!order) return
  const isReassign = assignActionLabel(order) === '改派'
  submitting.value = true
  try {
    await workOrderStore.assign(order.id, { id: dispatcher.id, name: dispatcher.displayName })
    assignVisible.value = false
    ElMessage.success(
      isReassign
        ? `已改派给 ${dispatcher.displayName}，工单保持处理中`
        : `已指派给 ${dispatcher.displayName}，工单进入处理中`,
    )
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '指派失败')
  } finally {
    submitting.value = false
  }
}

function openResolve(row: WorkOrder) {
  resolvingId.value = row.id
  resolveForm.faultType = ''
  resolveForm.handlingMethod = ''
  resolveForm.replacedParts = ''
  resolveForm.testResult = ''
  resolveForm.remarks = ''
  resolveVisible.value = true
}

function openReview(row: WorkOrder) {
  reviewingId.value = row.id
  reviewForm.result = 'PASS'
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function submitResolve() {
  if (!resolveForm.faultType || !resolveForm.handlingMethod || !resolveForm.testResult.trim()) {
    ElMessage.warning('请填写必填项：故障类型、处理方式、试验结果')
    return
  }
  const user = authStore.user
  if (!user) return
  submitting.value = true
  try {
    await workOrderStore.submitResolution(
      resolvingId.value,
      {
        faultType: resolveForm.faultType,
        handlingMethod: resolveForm.handlingMethod,
        replacedParts: resolveForm.replacedParts || undefined,
        testResult: resolveForm.testResult.trim(),
        remarks: resolveForm.remarks || undefined,
        submittedAt: '',
        submittedBy: user.displayName,
      },
      user.displayName,
    )
    resolveVisible.value = false
    ElMessage.success('已提交复核，等待管理员确认')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

async function submitReview() {
  if (!reviewForm.comment.trim()) {
    ElMessage.warning('请填写复核意见')
    return
  }
  const user = authStore.user
  if (!user) return
  submitting.value = true
  try {
    await workOrderStore.submitReview(reviewingId.value, reviewForm, user.displayName)
    reviewVisible.value = false
    ElMessage.success(reviewForm.result === 'PASS' ? '复核通过，工单已关闭' : '已退回，等待重新处理')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '复核失败')
  } finally {
    submitting.value = false
  }
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

onMounted(() => {
  if (can('workorder:assign')) {
    void userStore.loadUsers()
  }
})
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

.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
