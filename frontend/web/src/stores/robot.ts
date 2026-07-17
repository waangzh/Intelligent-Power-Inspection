import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resourcesApi } from '@/api/resources'
import type { LatLng, Robot } from '@/types'
import { uid } from '@/utils/storage'

export const useRobotStore = defineStore('robot', () => {
  const robots = ref<Robot[]>([])

  async function load(siteId?: string) {
    robots.value = (await resourcesApi.listRobots({ size: 50, siteId })).items
  }

  async function loadOne(id: string) {
    const robot = await resourcesApi.getRobot(id)
    updateLocalRobot(robot)
    return robot
  }

  function updateRobot(id: string, patch: Partial<Robot>) {
    const idx = robots.value.findIndex((r) => r.id === id)
    if (idx >= 0) {
      robots.value[idx] = { ...robots.value[idx], ...patch }
      void resourcesApi.updateRobot(id, patch).then(updateLocalRobot)
    }
  }

  function setPosition(id: string, position: LatLng) {
    updateRobot(id, { position })
  }

  function getRobotById(id: string) {
    return robots.value.find((r) => r.id === id)
  }

  function addRobot(robot: Omit<Robot, 'id'>) {
    const item: Robot = { ...robot, id: uid('robot') }
    robots.value.push(item)
    void resourcesApi.createRobot(item).then(updateLocalRobot)
    return item
  }

  function removeRobot(id: string) {
    robots.value = robots.value.filter((r) => r.id !== id)
    void resourcesApi.removeRobot(id)
  }

  function updateLocalRobot(robot: Robot) {
    const idx = robots.value.findIndex((r) => r.id === robot.id)
    if (idx >= 0) robots.value[idx] = robot
    else robots.value.unshift(robot)
  }

  return { robots, load, loadOne, updateRobot, setPosition, getRobotById, addRobot, removeRobot, applyRemoteRobot: updateLocalRobot }
})
