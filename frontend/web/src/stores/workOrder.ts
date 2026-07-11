import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Alarm } from '@/types'
import type { WorkOrder, WorkOrderStatus } from '@/types/workOrder'

export const useWorkOrderStore = defineStore('workOrder', () => {
  const orders = ref<WorkOrder[]>([])

  const statusCounts = computed(() => ({
    PENDING: orders.value.filter((o) => o.status === 'PENDING').length,
    PROCESSING: orders.value.filter((o) => o.status === 'PROCESSING').length,
    REVIEW: orders.value.filter((o) => o.status === 'REVIEW').length,
    CLOSED: orders.value.filter((o) => o.status === 'CLOSED').length,
  }))

  async function load() {
    orders.value = await resourcesApi.listWorkOrders()
  }

  function getById(id: string) {
    return orders.value.find((o) => o.id === id)
  }

  function getByAlarmId(alarmId: string) {
    return orders.value.find((o) => o.alarmId === alarmId)
  }

  async function createFromAlarm(
    alarm: Alarm,
    assigneeName?: string,
  ) {
    if (getByAlarmId(alarm.id)) {
      throw new Error('该告警已有关联工单')
    }
    const order = await resourcesApi.createWorkOrderFromAlarm(alarm.id, assigneeName)
    updateLocalOrder(order)
    return order
  }

  function updateStatus(id: string, status: WorkOrderStatus, extra?: { resolution?: string }) {
    const order = orders.value.find((o) => o.id === id)
    if (!order) return
    order.status = status
    order.updatedAt = new Date().toISOString()
    if (extra?.resolution) order.resolution = extra.resolution
    if (status === 'CLOSED') order.closedAt = order.updatedAt
    void resourcesApi.updateWorkOrderStatus(id, status, extra).then(updateLocalOrder)
  }

  function assign(id: string, assigneeName: string) {
    const order = orders.value.find((o) => o.id === id)
    if (!order) return
    order.assigneeName = assigneeName
    order.updatedAt = new Date().toISOString()
    void resourcesApi.assignWorkOrder(id, assigneeName).then(updateLocalOrder)
  }

  function updateLocalOrder(order: WorkOrder) {
    const idx = orders.value.findIndex((o) => o.id === order.id)
    if (idx >= 0) orders.value[idx] = order
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
    assign,
  }
})
