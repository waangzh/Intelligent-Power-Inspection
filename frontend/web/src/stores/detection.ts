import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { DetectionTemplate } from '@/types'
import { uid } from '@/utils/storage'
import type { ListQuery } from '@/types/pagination'

export const useDetectionStore = defineStore('detection', () => {
  const templates = ref<DetectionTemplate[]>([])
  const total = ref(0)

  async function load(query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listDetectionTemplates(query)
    templates.value = result.items
    total.value = result.total
  }

  function addTemplate(tpl: Omit<DetectionTemplate, 'id' | 'createdAt'>) {
    const item: DetectionTemplate = {
      ...tpl,
      id: uid('tpl'),
      createdAt: new Date().toISOString(),
    }
    templates.value.push(item)
    void resourcesApi.createDetectionTemplate(item).then((saved) => {
      const idx = templates.value.findIndex((t) => t.id === saved.id)
      if (idx >= 0) templates.value[idx] = saved
    })
    return item
  }

  function removeTemplate(id: string) {
    templates.value = templates.value.filter((t) => t.id !== id)
    void resourcesApi.removeDetectionTemplate(id)
  }

  return { templates, total, load, addTemplate, removeTemplate }
})
