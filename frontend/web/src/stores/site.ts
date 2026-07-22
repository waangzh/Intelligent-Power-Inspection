import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { Area, Site } from '@/types'
import type { ListQuery } from '@/types/pagination'
import { uid } from '@/utils/storage'

export const useSiteStore = defineStore('site', () => {
  const sites = ref<Site[]>([])
  const siteTotal = ref(0)
  const areas = ref<Area[]>([])
  const areaTotal = ref(0)

  async function load(siteId?: string) {
    const [remoteSites, remoteAreas] = await Promise.all([
      resourcesApi.listSites({ size: 20 }),
      resourcesApi.listAreas({ size: 20, siteId }),
    ])
    sites.value = remoteSites.items
    siteTotal.value = remoteSites.total
    areas.value = remoteAreas.items
    areaTotal.value = remoteAreas.total
  }

  async function loadSites(query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listSites(query)
    sites.value = result.items
    siteTotal.value = result.total
  }

  async function loadAreas(siteId: string, query: ListQuery = { size: 20 }) {
    const result = await resourcesApi.listAreas({ ...query, siteId })
    areas.value = result.items
    areaTotal.value = result.total
  }

  async function loadOne(id: string) {
    const site = await resourcesApi.getSite(id)
    updateLocalSite(site)
    return site
  }

  async function addSite(site: Omit<Site, 'id' | 'createdAt'>) {
    const newSite: Site = {
      ...site,
      id: uid('site'),
      createdAt: new Date().toISOString(),
    }
    const saved = await resourcesApi.createSite(newSite)
    updateLocalSite(saved)
    return saved
  }

  async function updateSite(id: string, patch: Partial<Site>) {
    const saved = await resourcesApi.updateSite(id, patch)
    updateLocalSite(saved)
    return saved
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
    siteTotal,
    areas,
    areaTotal,
    load,
    loadSites,
    loadAreas,
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
