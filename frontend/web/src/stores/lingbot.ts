import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { LingBotMapJob, LingBotMapOutputProfile, LingBotVideoUploadResponse } from '@/types'
import { uid } from '@/utils/storage'

export interface CreateLingBotJobInput {
  siteId: string
  siteName: string
  name: string
  videoUrl?: string
  fps: number
  stride: number
  keyframeInterval: number
  windowSize: number
  outputProfile: LingBotMapOutputProfile
  maskSky: boolean
}

export const useLingBotStore = defineStore('lingbot', () => {
  const jobs = ref<LingBotMapJob[]>([])

  async function load() {
    jobs.value = await resourcesApi.listLingBotJobs()
  }

  async function uploadVideo(file: File): Promise<LingBotVideoUploadResponse> {
    const form = new FormData()
    form.append('video', file)
    return resourcesApi.uploadLingBotVideo(form)
  }

  async function createJob(input: CreateLingBotJobInput) {
    const job: LingBotMapJob = {
      id: uid('lingbot'),
      siteId: input.siteId,
      siteName: input.siteName,
      name: input.name,
      status: 'PENDING',
      progress: 0,
      pointCount: 0,
      videoCount: 0,
      inputKind: 'video',
      videoUrl: input.videoUrl,
      fps: input.fps,
      stride: input.stride,
      keyframeInterval: input.keyframeInterval,
      windowSize: input.windowSize,
      outputProfile: input.outputProfile,
      maskSky: input.maskSky,
      createdAt: new Date().toISOString(),
    }
    jobs.value.unshift(job)
    const created = await resourcesApi.createLingBotJob(job)
    updateLocalJob(created)
    return created
  }

  async function refreshJob(id: string) {
    const job = jobs.value.find((j) => j.id === id)
    if (!job || job.status === 'COMPLETED' || job.status === 'CANCELLED') return job
    const updated = await resourcesApi.refreshLingBotJob(id)
    updateLocalJob(updated)
    return updated
  }

  function updateLocalJob(job: LingBotMapJob) {
    const idx = jobs.value.findIndex((j) => j.id === job.id)
    if (idx >= 0) jobs.value[idx] = job
    else jobs.value.unshift(job)
  }

  return { jobs, load, uploadVideo, createJob, refreshJob, applyRemoteJob: updateLocalJob }
})
