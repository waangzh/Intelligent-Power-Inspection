<template>
  <div class="workorder-page">
    <PageHeader
      title="工单管理"
      description="管理员：转工单与复核，调度员在接单大厅抢单并现场处理"
      :breadcrumbs="[{ label: '运维中心' }, { label: '工单管理' }]"
    />

    <div class="workorder-toolbar">
      <div class="scope-tabs">
        <button
          v-for="tab in scopeTabs"
          :key="tab.key"
          type="button"
          class="scope-tab"
          :class="{ active: scopeFilter === tab.key }"
          @click="scopeFilter = tab.key"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          {{ tab.label }}
        </button>
      </div>
      <div class="toolbar-actions">
        <el-button type="primary" :loading="exporting" @click="exportWorkOrders">
          <el-icon><Download /></el-icon>
          导出工单
        </el-button>
      </div>
    </div>

    <div class="stats-grid">
      <el-card
        v-for="(stat, index) in overviewStats"
        :key="stat.key"
        shadow="never"
        :class="['stat-card', `stat-card-${index}`, { active: activeStatCard === stat.key }]"
        @click="selectStatCard(stat.key)"
      >
        <div class="overview-stat">
          <div class="stat-icon" :class="`tone-${index}`">
            <el-icon><component :is="statIcons[index]" /></el-icon>
          </div>
          <div>
            <div class="label">{{ stat.label }}</div>
            <div class="value">{{ stat.value }}</div>
            <div class="trend">{{ stat.footer }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索工单号 / 标题 / 地点"
          clearable
          class="filter-search"
          @change="orderPage = 0"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="filter-item">
          <span class="filter-label">优先级</span>
          <el-select v-model="priorityFilter" placeholder="全部" style="width: 100px" @change="orderPage = 0">
            <el-option label="全部" value="ALL" />
            <el-option label="紧急" value="URGENT" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">状态</span>
          <el-select v-model="statusFilter" placeholder="全部" style="width: 110px" @change="onStatusFilterChange">
            <el-option label="全部" value="ALL" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="待复核" value="REVIEW" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">处理人</span>
          <el-select v-model="assigneeFilter" placeholder="全部" style="width: 120px" @change="orderPage = 0">
            <el-option label="全部" value="ALL" />
            <el-option label="待接单" value="UNASSIGNED" />
            <el-option v-for="name in assigneeOptions" :key="name" :label="name" :value="name" />
          </el-select>
        </div>
        <el-button @click="resetFilters">
          <el-icon><RefreshRight /></el-icon>
          重置
        </el-button>
        <el-button v-if="can('workorder:create')" type="primary" @click="router.push('/alarms')">
          <el-icon><Plus /></el-icon>
          新建工单
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-head">
          <span class="table-title">工单列表</span>
          <span class="record-count">共 {{ filteredOrders.length }} 条记录</span>
        </div>
      </template>
      <el-table :data="paginatedOrders" size="small" @row-click="openDetail">
        <el-table-column prop="id" label="工单号" width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="地点" min-width="160" show-overflow-tooltip>
          <template #default="{ row }: { row: WorkOrder }">
            {{ locationLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }: { row: WorkOrder }">
            <el-tag :type="priorityType(row.priority)" size="small" effect="light">
              {{ WORK_ORDER_PRIORITY_LABELS[row.priority] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }: { row: WorkOrder }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ WORK_ORDER_STATUS_LABELS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="100">
          <template #default="{ row }: { row: WorkOrder }">
            {{ workOrderAssigneeLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" class-name="actions-col">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <el-button
                v-if="canClaimOrder(row)"
                plain
                size="small"
                class="action-btn action-claim"
                :loading="claimingId === row.id"
                @click="submitClaim(row)"
              >接单</el-button>
              <el-button
                v-if="canProcessOrder(row) && row.status === 'PROCESSING'"
                plain
                size="small"
                class="action-btn action-submit"
                @click="openResolve(row)"
              >提交复核</el-button>
              <el-button
                v-if="can('workorder:review') && row.status === 'REVIEW'"
                plain
                size="small"
                class="action-btn action-review"
                @click="openReview(row)"
              >确认复核</el-button>
              <el-button plain size="small" class="action-btn action-detail" @click="openDetail(row)">详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <ListPagination :total="filteredOrders.length" :page="orderPage" @change="loadOrderPage" />
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
            <el-descriptions-item label="处理结论">
              {{ detail.resolutionForm.conclusion ? WORK_ORDER_REVIEW_CONCLUSION_LABELS[detail.resolutionForm.conclusion] : '-' }}
            </el-descriptions-item>
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
        <el-form-item label="处理结论" required>
          <el-select v-model="resolveForm.conclusion" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="o in WORK_ORDER_REVIEW_CONCLUSION_OPTIONS"
              :key="o"
              :label="WORK_ORDER_REVIEW_CONCLUSION_LABELS[o]"
              :value="o"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="补充说明" :required="needsFollowUpPlan">
          <el-input
            v-model="resolveForm.remarks"
            type="textarea"
            :rows="2"
            :placeholder="needsFollowUpPlan ? '部分消缺/未消缺时必须填写遗留风险与后续计划' : '其他现场情况'"
          />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  CircleCheck,
  Clock,
  Document,
  Download,
  EditPen,
  Plus,
  Refresh,
  RefreshRight,
  Search,
  Top,
  User,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import ListPagination from '@/components/ListPagination.vue'
import { useAuthStore } from '@/stores/auth'
import { useWorkOrderStore } from '@/stores/workOrder'
import { usePermission } from '@/composables/usePermission'
import type { WorkOrder, WorkOrderPriority, WorkOrderReviewConclusion, WorkOrderStatus } from '@/types/workOrder'
import {
  CONCLUSIONS_REQUIRING_FOLLOW_UP,
  FAULT_TYPE_OPTIONS,
  HANDLING_METHOD_OPTIONS,
  WORK_ORDER_PRIORITY_LABELS,
  WORK_ORDER_REVIEW_CONCLUSION_LABELS,
  WORK_ORDER_REVIEW_CONCLUSION_OPTIONS,
  WORK_ORDER_STATUS_LABELS,
} from '@/types/workOrder'
import { isWorkOrderUnassigned, workOrderAssigneeLabel } from '@/utils/workOrder'

const PAGE_SIZE = 20
const LOAD_SIZE = 500

const router = useRouter()
const workOrderStore = useWorkOrderStore()
const authStore = useAuthStore()
const { can, role } = usePermission()

const keyword = ref('')
const statusFilter = ref<string>('ALL')
const priorityFilter = ref<string>('ALL')
const assigneeFilter = ref<string>('ALL')
const highPriorityOnly = ref(false)
const activeStatCard = ref<string>('ALL')
const orderPage = ref(0)
const scopeFilter = ref<'ALL' | 'POOL' | 'MINE'>('ALL')
const detailVisible = ref(false)
const detail = ref<WorkOrder | null>(null)
const resolveVisible = ref(false)
const reviewVisible = ref(false)
const resolvingId = ref('')
const reviewingId = ref('')
const claimingId = ref('')
const submitting = ref(false)
const exporting = ref(false)

const statIcons = [Document, Clock, Refresh, CircleCheck, Top]

const scopeTabs = [
  { key: 'ALL' as const, label: '全部可见', icon: Document },
  { key: 'POOL' as const, label: '接单大厅', icon: User },
  { key: 'MINE' as const, label: '我的工单', icon: EditPen },
]

async function loadOrders() {
  await workOrderStore.load({ page: 0, size: LOAD_SIZE })
}

function loadOrderPage(page: number) {
  orderPage.value = page
}

onMounted(() => {
  void loadOrders()
})

watch(scopeFilter, () => {
  orderPage.value = 0
})

watch(priorityFilter, () => {
  if (priorityFilter.value !== 'ALL') {
    highPriorityOnly.value = false
    activeStatCard.value = ''
  }
  orderPage.value = 0
})

const resolveForm = reactive({
  faultType: '',
  handlingMethod: '',
  replacedParts: '',
  testResult: '',
  conclusion: '' as WorkOrderReviewConclusion | '',
  remarks: '',
})

const needsFollowUpPlan = computed(() =>
  CONCLUSIONS_REQUIRING_FOLLOW_UP.includes(resolveForm.conclusion as WorkOrderReviewConclusion),
)

const reviewForm = reactive({
  result: 'PASS' as 'PASS' | 'REJECT',
  comment: '',
})

function isMyOrder(order: WorkOrder) {
  const me = authStore.user
  if (!me) return false
  return order.assigneeId === me.id || order.assigneeName === me.displayName
}

const baseOrders = computed(() => {
  if (role.value === 'DISPATCHER') {
    return workOrderStore.orders.filter((o) => isWorkOrderUnassigned(o) || isMyOrder(o))
  }
  return workOrderStore.orders
})

const scopedOrders = computed(() => {
  let list = baseOrders.value
  if (scopeFilter.value === 'POOL') {
    list = list.filter((o) => isWorkOrderUnassigned(o))
  } else if (scopeFilter.value === 'MINE') {
    list = list.filter((o) => isMyOrder(o))
  }
  return list
})

const overviewStats = computed(() => {
  const list = scopedOrders.value
  const count = (status: WorkOrderStatus) => list.filter((o) => o.status === status).length
  const highPriority = list.filter((o) => o.priority === 'HIGH' || o.priority === 'URGENT').length
  return [
    { key: 'ALL', label: '全部工单', value: list.length, footer: '当前统计' },
    { key: 'PENDING', label: '待处理', value: count('PENDING'), footer: '当前统计' },
    { key: 'PROCESSING', label: '处理中', value: count('PROCESSING'), footer: '当前统计' },
    { key: 'REVIEW', label: '待复核', value: count('REVIEW'), footer: '当前统计' },
    { key: 'HIGH', label: '高优先级', value: highPriority, footer: '需优先关注' },
  ]
})

const assigneeOptions = computed(() => {
  const names = new Set<string>()
  scopedOrders.value.forEach((o) => {
    const label = workOrderAssigneeLabel(o)
    if (label !== '待接单') names.add(label)
  })
  return [...names].sort()
})

const filteredOrders = computed(() => {
  let list = scopedOrders.value
  if (statusFilter.value !== 'ALL') {
    list = list.filter((o) => o.status === statusFilter.value)
  }
  if (highPriorityOnly.value) {
    list = list.filter((o) => o.priority === 'HIGH' || o.priority === 'URGENT')
  } else if (priorityFilter.value !== 'ALL') {
    list = list.filter((o) => o.priority === priorityFilter.value)
  }
  if (assigneeFilter.value === 'UNASSIGNED') {
    list = list.filter((o) => isWorkOrderUnassigned(o))
  } else if (assigneeFilter.value !== 'ALL') {
    list = list.filter((o) => workOrderAssigneeLabel(o) === assigneeFilter.value)
  }
  const q = keyword.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (o) =>
        o.id.toLowerCase().includes(q) ||
        o.title.toLowerCase().includes(q) ||
        locationLabel(o).toLowerCase().includes(q),
    )
  }
  return list
})

const paginatedOrders = computed(() => {
  const start = orderPage.value * PAGE_SIZE
  return filteredOrders.value.slice(start, start + PAGE_SIZE)
})

function selectStatCard(key: string) {
  activeStatCard.value = key
  orderPage.value = 0
  if (key === 'HIGH') {
    highPriorityOnly.value = true
    statusFilter.value = 'ALL'
    priorityFilter.value = 'ALL'
    return
  }
  highPriorityOnly.value = false
  statusFilter.value = key === 'ALL' ? 'ALL' : key
}

function onStatusFilterChange() {
  highPriorityOnly.value = false
  activeStatCard.value = statusFilter.value
  orderPage.value = 0
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = 'ALL'
  priorityFilter.value = 'ALL'
  assigneeFilter.value = 'ALL'
  highPriorityOnly.value = false
  activeStatCard.value = 'ALL'
  scopeFilter.value = 'ALL'
  orderPage.value = 0
}

function canClaimOrder(row: WorkOrder) {
  return can('workorder:process') && row.status === 'PENDING' && isWorkOrderUnassigned(row)
}

function canProcessOrder(row: WorkOrder) {
  if (role.value !== 'DISPATCHER' || !can('workorder:process')) return false
  if (row.status !== 'PROCESSING') return false
  return isMyOrder(row)
}

function locationLabel(row: WorkOrder) {
  return (
    row.location?.siteName ||
    row.location?.address ||
    row.location?.areaName ||
    row.location?.routeName ||
    '-'
  )
}

function openDetail(row: WorkOrder) {
  detail.value = row
  detailVisible.value = true
}

async function submitClaim(row: WorkOrder) {
  claimingId.value = row.id
  try {
    await workOrderStore.claim(row.id)
    ElMessage.success('接单成功，工单已进入处理中')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '接单失败，可能已被他人抢走')
  } finally {
    claimingId.value = ''
  }
}

function openResolve(row: WorkOrder) {
  resolvingId.value = row.id
  resolveForm.faultType = ''
  resolveForm.handlingMethod = ''
  resolveForm.replacedParts = ''
  resolveForm.testResult = ''
  resolveForm.conclusion = ''
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
  if (!resolveForm.conclusion) {
    ElMessage.warning('请选择处理结论')
    return
  }
  if (needsFollowUpPlan.value && !resolveForm.remarks.trim()) {
    ElMessage.warning('部分消缺或未消缺时，请在补充说明中填写遗留风险与后续计划')
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
        conclusion: resolveForm.conclusion,
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

function exportWorkOrders() {
  if (!filteredOrders.value.length) {
    ElMessage.warning('当前没有可导出的工单')
    return
  }
  exporting.value = true
  try {
    const headers = ['工单号', '标题', '地点', '优先级', '状态', '处理人', '创建时间']
    const rows = filteredOrders.value.map((o) => [
      o.id,
      o.title,
      locationLabel(o),
      WORK_ORDER_PRIORITY_LABELS[o.priority],
      WORK_ORDER_STATUS_LABELS[o.status],
      workOrderAssigneeLabel(o),
      fmt(o.createdAt),
    ])
    const csv = [headers, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
      .join('\n')
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `work-orders-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${filteredOrders.value.length} 条工单`)
  } finally {
    exporting.value = false
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
</script>

<style scoped>
.workorder-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.scope-tabs {
  display: inline-flex;
  padding: 4px;
  background: #f4f6f8;
  border-radius: 10px;
  gap: 4px;
}

.scope-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #526986;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.scope-tab.active {
  background: #1768f2;
  color: #fff;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.overview-stat {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 16px;
  color: #fff;
  font-size: 24px;
}

.tone-0 { background: linear-gradient(135deg, #2878ff, #1455d9); }
.tone-1 { background: linear-gradient(135deg, #ff7d2d, #f25216); }
.tone-2 { background: linear-gradient(135deg, #2878ff, #1455d9); }
.tone-3 { background: linear-gradient(135deg, #8b58ff, #6431e6); }
.tone-4 { background: linear-gradient(135deg, #ff4d4f, #cf1322); }

.stat-card {
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.stat-card.active {
  border-color: #1768f2;
  box-shadow: 0 0 0 1px #1768f2 inset;
}

.stat-card .label {
  margin: 0;
  color: #526986;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-card .value {
  margin-top: 2px;
  font-size: 30px;
  line-height: 1.05;
  font-weight: 700;
}

.stat-card .trend {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  color: #909399;
}

.stat-card-0 .value { color: #1768f2; }
.stat-card-1 .value { color: #f25216; }
.stat-card-2 .value { color: #1768f2; }
.stat-card-3 .value { color: #6431e6; }
.stat-card-4 .value,
.stat-card-4 .trend { color: #cf1322; }

.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-search {
  width: 280px;
  flex: 1 1 220px;
  max-width: 360px;
}

.filter-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #526986;
  white-space: nowrap;
}

.table-card :deep(.el-card__header) {
  padding-block: 12px 10px;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.table-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2d3d;
}

.record-count {
  font-size: 13px;
  color: #909399;
}

.table-card :deep(.el-table__body td.el-table__cell) {
  vertical-align: middle;
}

.table-card :deep(.actions-col .cell) {
  overflow: visible;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  min-height: 28px;
  white-space: nowrap;
}

.row-actions :deep(.action-btn) {
  margin: 0;
  padding: 5px 12px;
  height: 28px;
  border-radius: 6px;
  font-weight: 500;
}

.row-actions :deep(.action-claim) {
  background: #fff7e6;
  border-color: #ffbb96;
  color: #fa8c16;
}

.row-actions :deep(.action-claim:hover),
.row-actions :deep(.action-claim:focus) {
  background: #ffe7ba;
  border-color: #ffa940;
  color: #d46b08;
}

.row-actions :deep(.action-detail),
.row-actions :deep(.action-review) {
  background: #e6f4ff;
  border-color: #91caff;
  color: #1677ff;
}

.row-actions :deep(.action-detail:hover),
.row-actions :deep(.action-detail:focus),
.row-actions :deep(.action-review:hover),
.row-actions :deep(.action-review:focus) {
  background: #bae0ff;
  border-color: #69b1ff;
  color: #0958d9;
}

.row-actions :deep(.action-submit) {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #52c41a;
}

.row-actions :deep(.action-submit:hover),
.row-actions :deep(.action-submit:focus) {
  background: #d9f7be;
  border-color: #95de64;
  color: #389e0d;
}

.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

@media (max-width: 768px) {
  .workorder-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }

  .scope-tabs {
    width: 100%;
    overflow-x: auto;
  }
}
</style>
