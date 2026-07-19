import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { DetectionItem, DetectionRun, DetectionTemplate, RobotInspectionImage } from '@/types'
import type { ListQuery } from '@/types/pagination'

export const useDetectionStore = defineStore('detection', () => {
  const templates = ref<DetectionTemplate[]>([])
  const total = ref(0)
  const images = ref<RobotInspectionImage[]>([])
  const imageTotal = ref(0)
  const runs = ref<DetectionRun[]>([])
  const runTotal = ref(0)

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

  async function loadImages(query: ListQuery = { size: 12 }) {
    const result = await resourcesApi.listRobotInspectionImages(query)
    images.value = result.items
    imageTotal.value = result.total
  }

  async function loadRuns(query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listDetectionRuns(query)
    runs.value = result.items
    runTotal.value = result.total
  }

  async function importImage(form: FormData) {
    const saved = await resourcesApi.importRobotInspectionImage(form)
    images.value = [saved, ...images.value.filter((item) => item.id !== saved.id)]
    imageTotal.value += 1
    return saved
  }

  async function detectImage(imageId: string, detections: DetectionItem[]) {
    const run = await resourcesApi.detectRobotInspectionImage(imageId, detections)
    runs.value = [run, ...runs.value.filter((item) => item.runId !== run.runId)]
    runTotal.value += 1
    return run
  }

  async function getRun(runId: string) {
    const run = await resourcesApi.getDetectionRun(runId)
    const index = runs.value.findIndex((item) => item.runId === run.runId)
    if (index >= 0) runs.value[index] = run
    else runs.value.unshift(run)
    return run
  }

  return {
    templates, total, images, imageTotal, runs, runTotal,
    load, addTemplate, updateTemplate, removeTemplate,
    loadImages, loadRuns, importImage, detectImage, getRun,
  }
})
