import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Area, Site } from '@/types'
import { uid } from '@/utils/storage'

export const useSiteStore = defineStore('site', () => {
  const sites = ref<Site[]>([])
  const areas = ref<Area[]>([])

  async function load(siteId?: string) {
    const [remoteSites, remoteAreas] = await Promise.all([
      resourcesApi.listSites({ size: 50 }),
      resourcesApi.listAreas({ size: 100, siteId }),
    ])
    sites.value = remoteSites.items
    areas.value = remoteAreas.items
  }

  async function loadOne(id: string) {
    const site = await resourcesApi.getSite(id)
    updateLocalSite(site)
    return site
  }

  function addSite(site: Omit<Site, 'id' | 'createdAt'>) {
    const newSite: Site = {
      ...site,
      id: uid('site'),
      createdAt: new Date().toISOString(),
    }
    sites.value.push(newSite)
    void resourcesApi.createSite(newSite).then((saved) => updateLocalSite(saved))
    return newSite
  }

  function updateSite(id: string, patch: Partial<Site>) {
    const idx = sites.value.findIndex((s) => s.id === id)
    if (idx >= 0) {
      sites.value[idx] = { ...sites.value[idx], ...patch }
      void resourcesApi.updateSite(id, patch).then((saved) => updateLocalSite(saved))
    }
  }

  function removeSite(id: string) {
    sites.value = sites.value.filter((s) => s.id !== id)
    areas.value = areas.value.filter((a) => a.siteId !== id)
    void resourcesApi.removeSite(id)
  }

  function addArea(area: Omit<Area, 'id'>) {
    const newArea: Area = { ...area, id: uid('area') }
    areas.value.push(newArea)
    void resourcesApi.createArea(newArea).then((saved) => updateLocalArea(saved))
    return newArea
  }

  function removeArea(id: string) {
    areas.value = areas.value.filter((a) => a.id !== id)
    void resourcesApi.removeArea(id)
  }

  function getSiteById(id: string) {
    return sites.value.find((s) => s.id === id)
  }

  function getAreasBySite(siteId: string) {
    return areas.value.filter((a) => a.siteId === siteId)
  }

  function updateLocalSite(site: Site) {
    const idx = sites.value.findIndex((s) => s.id === site.id)
    if (idx >= 0) sites.value[idx] = site
    else sites.value.unshift(site)
  }

  function updateLocalArea(area: Area) {
    const idx = areas.value.findIndex((a) => a.id === area.id)
    if (idx >= 0) areas.value[idx] = area
  }

  return {
    sites,
    areas,
    load,
    loadOne,
    addSite,
    updateSite,
    removeSite,
    addArea,
    removeArea,
    getSiteById,
    getAreasBySite,
  }
})
