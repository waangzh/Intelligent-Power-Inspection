import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Alarm } from '@/types'
import type {
  WorkOrder,
  WorkOrderLocation,
  WorkOrderPriority,
  WorkOrderResolutionForm,
  WorkOrderReviewForm,
  WorkOrderStatus,
} from '@/types/workOrder'
import { useNotificationStore } from '@/stores/notification'
import { useSiteStore } from '@/stores/site'
import { uid } from '@/utils/storage'
import { normalizeWorkOrder } from '@/utils/workOrder'

function buildLocationFromAlarm(alarm: Alarm): WorkOrderLocation {
  const siteStore = useSiteStore()
  const site = siteStore.sites[0]
  return {
    siteName: site?.name,
    routeName: alarm.routeName,
    checkpointName: alarm.checkpointName,
    areaName: alarm.checkpointName ? `${alarm.routeName} · ${alarm.checkpointName}` : alarm.routeName,
    address: site?.address,
  }
}

function resolutionSummary(form: WorkOrderResolutionForm): string {
  return [
    `故障类型：${form.faultType}`,
    `处理方式：${form.handlingMethod}`,
    form.replacedParts ? `更换部件：${form.replacedParts}` : '',
    `试验结果：${form.testResult}`,
    form.remarks ? `备注：${form.remarks}` : '',
  ]
    .filter(Boolean)
    .join('；')
}

export const useWorkOrderStore = defineStore('workOrder', () => {
  const orders = ref<WorkOrder[]>([])

  const statusCounts = computed(() => ({
    PENDING: orders.value.filter((o) => o.status === 'PENDING').length,
    PROCESSING: orders.value.filter((o) => o.status === 'PROCESSING').length,
    REVIEW: orders.value.filter((o) => o.status === 'REVIEW').length,
    CLOSED: orders.value.filter((o) => o.status === 'CLOSED').length,
  }))

  async function load() {
    const raw = await resourcesApi.listWorkOrders()
    const normalized = raw.map(normalizeWorkOrder)
    orders.value = normalized

    await Promise.all(
      normalized.map(async (order) => {
        const original = raw.find((o) => o.id === order.id)
        if (original && original.status !== order.status) {
          try {
            await resourcesApi.updateWorkOrderStatus(order.id, order.status)
          } catch {
            // 修复历史脏数据失败时忽略，界面已按归一化结果展示
          }
        }
      }),
    )
  }

  function getById(id: string) {
    return orders.value.find((o) => o.id === id)
  }

  function getByAlarmId(alarmId: string) {
    return orders.value.find((o) => o.alarmId === alarmId)
  }

  async function createFromAlarm(
    alarm: Alarm,
    creator: { id: string; name: string },
    options?: { assigneeName?: string; assigneeId?: string; autoConverted?: boolean },
  ) {
    if (getByAlarmId(alarm.id)) {
      throw new Error('该告警已有关联工单')
    }
    const priority: WorkOrderPriority =
      alarm.severity === 'CRITICAL' ? 'URGENT' : alarm.severity === 'HIGH' ? 'HIGH' : 'MEDIUM'

    const now = new Date().toISOString()
    const location = buildLocationFromAlarm(alarm)
    const order: WorkOrder = {
      id: uid('wo'),
      title: `告警处置：${alarm.message.slice(0, 24)}`,
      description: alarm.message,
      alarmId: alarm.id,
      status: 'PENDING',
      priority,
      assigneeName: options?.assigneeName,
      assigneeId: options?.assigneeId,
      createdById: creator.id,
      createdByName: creator.name,
      location,
      autoConverted: options?.autoConverted,
      createdAt: now,
      updatedAt: now,
    }
    orders.value.unshift(order)

    const saved = await resourcesApi.createWorkOrderFromAlarm(alarm.id, options?.assigneeName)
    const merged: WorkOrder = normalizeWorkOrder({
      ...saved,
      assigneeName: options?.assigneeName,
      assigneeId: options?.assigneeId,
      location,
      autoConverted: options?.autoConverted,
    })
    updateLocalOrder(merged)

    const ntf = useNotificationStore()
    ntf.push(creator.id, 'WORKORDER', '工单已创建', merged.title, '/workorders')
    return merged
  }

  async function updateStatus(
    id: string,
    status: WorkOrderStatus,
    extra?: { resolution?: string; resolutionForm?: WorkOrderResolutionForm; reviewForm?: WorkOrderReviewForm },
  ) {
    const order = orders.value.find((o) => o.id === id)
    if (!order) return
    order.status = status
    order.updatedAt = new Date().toISOString()
    if (extra?.resolution) order.resolution = extra.resolution
    if (extra?.resolutionForm) order.resolutionForm = extra.resolutionForm
    if (extra?.reviewForm) order.reviewForm = extra.reviewForm
    if (status === 'CLOSED') order.closedAt = order.updatedAt

    const saved = await resourcesApi.updateWorkOrderStatus(id, status, {
      resolution: extra?.resolution ?? order.resolution,
    })
    const merged = { ...saved, resolutionForm: order.resolutionForm, reviewForm: order.reviewForm, location: order.location }
    updateLocalOrder(merged)
    return merged
  }

  async function submitResolution(id: string, form: WorkOrderResolutionForm, submitter: string) {
    const fullForm = { ...form, submittedAt: new Date().toISOString(), submittedBy: submitter }
    return updateStatus(id, 'REVIEW', {
      resolution: resolutionSummary(fullForm),
      resolutionForm: fullForm,
    })
  }

  async function submitReview(id: string, form: Omit<WorkOrderReviewForm, 'reviewedAt' | 'reviewedBy'>, reviewer: string) {
    const fullForm: WorkOrderReviewForm = {
      ...form,
      reviewedAt: new Date().toISOString(),
      reviewedBy: reviewer,
    }
    const status: WorkOrderStatus = form.result === 'PASS' ? 'CLOSED' : 'PROCESSING'
    return updateStatus(id, status, {
      resolution: form.comment,
      reviewForm: fullForm,
    })
  }

  async function assign(id: string, assignee: { id: string; name: string }) {
    const order = orders.value.find((o) => o.id === id)
    if (!order) return
    order.assigneeName = assignee.name
    order.assigneeId = assignee.id
    if (order.status === 'PENDING' || order.status === 'PROCESSING') {
      order.status = 'PROCESSING'
    }
    order.updatedAt = new Date().toISOString()
    const saved = await resourcesApi.assignWorkOrder(id, { name: assignee.name, id: assignee.id })
    const merged = {
      ...saved,
      assigneeId: assignee.id,
      assigneeName: assignee.name,
      status: (saved.status as WorkOrderStatus) || order.status,
      location: order.location,
      resolutionForm: order.resolutionForm,
      reviewForm: order.reviewForm,
    }
    updateLocalOrder(merged)
    return merged
  }

  function updateLocalOrder(order: WorkOrder) {
    const idx = orders.value.findIndex((o) => o.id === order.id)
    if (idx >= 0) orders.value[idx] = { ...orders.value[idx], ...order }
    else orders.value.unshift(order)
  }

  return {
    orders,
    statusCounts,
    load,
    getById,
    getByAlarmId,
    createFromAlarm,
    updateStatus,
    submitResolution,
    submitReview,
    assign,
  }
})
