import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { DetectionTemplate } from '@/types'
import type { ListQuery } from '@/types/pagination'

export const useDetectionStore = defineStore('detection', () => {
  const templates = ref<DetectionTemplate[]>([])
  const total = ref(0)

  async function load(query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listDetectionTemplates(query)
    templates.value = result.items
    total.value = result.total
  }

  async function addTemplate(tpl: Omit<DetectionTemplate, 'id' | 'createdAt'>) {
    const saved = await resourcesApi.createDetectionTemplate(tpl)
    templates.value.push(saved)
    return saved
  }

  async function updateTemplate(id: string, patch: Partial<DetectionTemplate>) {
    const saved = await resourcesApi.updateDetectionTemplate(id, patch)
    const idx = templates.value.findIndex((template) => template.id === id)
    if (idx >= 0) templates.value[idx] = saved
    return saved
  }

  async function removeTemplate(id: string) {
    await resourcesApi.removeDetectionTemplate(id)
    templates.value = templates.value.filter((t) => t.id !== id)
  }

  return { templates, total, load, addTemplate, updateTemplate, removeTemplate }
})
