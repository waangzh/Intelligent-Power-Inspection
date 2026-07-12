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
          <el-descriptions-item label="具体地点">
            <span class="location-value">{{ detail.locationDescription || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.description }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ WORK_ORDER_STATUS_LABELS[detail.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.assigneeName || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.resolution && !detail.review" label="处理说明">{{ detail.resolution }}</el-descriptions-item>
        </el-descriptions>
        <section v-if="detail.review" class="review-record">
          <div class="review-record-title">
            <span>复核记录</span>
            <el-tag size="small" type="success">{{ WORK_ORDER_REVIEW_CONCLUSION_LABELS[detail.review.conclusion] }}</el-tag>
          </div>
          <div class="review-meta">{{ detail.review.submittedByName }} · {{ fmt(detail.review.submittedAt) }}</div>
          <div class="review-grid">
            <div class="review-field"><span>现场检查情况</span><p>{{ detail.review.onsiteFinding }}</p></div>
            <div class="review-field"><span>处理措施与验证结果</span><p>{{ detail.review.handlingMeasures }}</p></div>
            <div v-if="detail.review.followUpPlan" class="review-field"><span>遗留风险与后续计划</span><p>{{ detail.review.followUpPlan }}</p></div>
          </div>
        </section>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" title="提交复核" width="560px" :close-on-click-modal="false">
      <div class="review-form-intro">请如实记录现场核查和处置验证结果，提交后将进入待复核状态。</div>
      <el-form label-position="top" class="review-form">
        <el-form-item label="复核结论" required>
          <el-select v-model="reviewForm.conclusion" placeholder="请选择复核结论" style="width: 100%">
            <el-option v-for="item in reviewConclusionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="现场检查情况" required>
          <el-input v-model="reviewForm.onsiteFinding" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明现场检查范围、发现情况和关键读数（至少 10 个字符）" />
        </el-form-item>
        <el-form-item label="处理措施与验证结果" required>
          <el-input v-model="reviewForm.handlingMeasures" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明已采取的措施及验证方式、验证结果（至少 10 个字符）" />
        </el-form-item>
        <el-form-item :label="requiresFollowUp ? '遗留风险与后续计划（必填）' : '遗留风险与后续计划'" :required="requiresFollowUp">
          <el-input v-model="reviewForm.followUpPlan" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="未完全消缺时，请说明风险、责任人或计划完成时间" />
        </el-form-item>
      </el-form>
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
import type { WorkOrder, WorkOrderPriority, WorkOrderReviewInput, WorkOrderStatus } from '@/types/workOrder'
import { WORK_ORDER_PRIORITY_LABELS, WORK_ORDER_REVIEW_CONCLUSION_LABELS, WORK_ORDER_STATUS_LABELS } from '@/types/workOrder'

const router = useRouter()
const workOrderStore = useWorkOrderStore()
const { can } = usePermission()
const statusFilter = ref<string>('ALL')
const detailVisible = ref(false)
const detail = ref<WorkOrder | null>(null)
const resolveVisible = ref(false)
const resolvingId = ref('')
const reviewConclusionOptions = Object.entries(WORK_ORDER_REVIEW_CONCLUSION_LABELS).map(([value, label]) => ({ value, label }))
const reviewForm = ref<WorkOrderReviewInput>(emptyReviewForm())

const requiresFollowUp = computed(() => ['PARTIALLY_RESOLVED', 'UNRESOLVED'].includes(reviewForm.value.conclusion))

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

async function advance(id: string, status: WorkOrderStatus) {
  await workOrderStore.updateStatus(id, status)
  ElMessage.success('状态已更新')
}

function openResolve(row: WorkOrder) {
  resolvingId.value = row.id
  reviewForm.value = emptyReviewForm()
  resolveVisible.value = true
}

async function submitResolve() {
  const review: WorkOrderReviewInput = {
    conclusion: reviewForm.value.conclusion,
    onsiteFinding: reviewForm.value.onsiteFinding.trim(),
    handlingMeasures: reviewForm.value.handlingMeasures.trim(),
    followUpPlan: reviewForm.value.followUpPlan?.trim(),
  }
  if (review.onsiteFinding.length < 10 || review.handlingMeasures.length < 10) {
    ElMessage.warning('现场检查情况和处理措施至少填写 10 个字符')
    return
  }
  if (requiresFollowUp.value && !review.followUpPlan) {
    ElMessage.warning('请填写遗留风险与后续计划')
    return
  }
  await workOrderStore.updateStatus(resolvingId.value, 'REVIEW', { review })
  resolveVisible.value = false
  ElMessage.success('已提交复核')
}

async function closeOrder(id: string) {
  await workOrderStore.updateStatus(id, 'CLOSED')
  ElMessage.success('工单已关闭')
}

function emptyReviewForm(): WorkOrderReviewInput {
  return {
    conclusion: 'RESOLVED',
    onsiteFinding: '',
    handlingMeasures: '',
    followUpPlan: '',
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

.location-value {
  display: inline-block;
  padding: 2px 8px;
  color: #174f84;
  background: #edf5fc;
  border-radius: 3px;
}

.review-record {
  margin-top: 16px;
  padding: 16px;
  background: linear-gradient(135deg, #f5f9fc, #fff);
  border: 1px solid #dbe8f3;
  border-left: 3px solid #1a5fb4;
  border-radius: 4px;
}

.review-record-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #1d2939;
}

.review-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #7a8795;
}

.review-grid {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.review-field > span {
  font-size: 12px;
  color: #64748b;
}

.review-field p {
  margin: 4px 0 0;
  line-height: 1.7;
  color: #344054;
  white-space: pre-wrap;
}

.review-form-intro {
  margin-bottom: 16px;
  padding: 10px 12px;
  color: #476072;
  font-size: 13px;
  line-height: 1.6;
  background: #f4f8fb;
  border-left: 3px solid #1a5fb4;
}

.review-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>
