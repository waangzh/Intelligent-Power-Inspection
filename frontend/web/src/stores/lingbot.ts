import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { LingBotMapJob } from '@/types'
import { uid } from '@/utils/storage'

export const useLingBotStore = defineStore('lingbot', () => {
  const jobs = ref<LingBotMapJob[]>([])

  async function load() {
    jobs.value = await resourcesApi.listLingBotJobs()
  }

  function createJob(siteId: string, siteName: string, name: string) {
    const job: LingBotMapJob = {
      id: uid('lingbot'),
      siteId,
      siteName,
      name,
      status: 'PENDING',
      progress: 0,
      pointCount: 0,
      videoCount: 0,
      createdAt: new Date().toISOString(),
    }
    jobs.value.unshift(job)
    void resourcesApi.createLingBotJob(job).then(updateLocalJob)
    return job
  }

  function simulateProgress(id: string) {
    const job = jobs.value.find((j) => j.id === id)
    if (!job || job.status === 'COMPLETED') return
    job.status = 'PROCESSING'
    job.progress = Math.min(100, job.progress + 15)
    job.pointCount += 120000
    job.videoCount += 2
    if (job.progress >= 100) {
      job.status = 'COMPLETED'
      job.progress = 100
      job.completedAt = new Date().toISOString()
    }
    void resourcesApi.simulateLingBotJob(id).then(updateLocalJob)
  }

  function updateLocalJob(job: LingBotMapJob) {
    const idx = jobs.value.findIndex((j) => j.id === job.id)
    if (idx >= 0) jobs.value[idx] = job
    else jobs.value.unshift(job)
  }

  return { jobs, load, createJob, simulateProgress, applyRemoteJob: updateLocalJob }
})
