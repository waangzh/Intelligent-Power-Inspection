<template>
  <div ref="container" class="map-container map3d" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as THREE from 'three'
import type { LatLng, Route } from '@/types'

const props = defineProps<{
  route?: Route | null
  robotPosition?: LatLng | null
}>()

const container = ref<HTMLDivElement | null>(null)
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let animationId = 0
let routeLine: THREE.Line | null = null
let checkpointMeshes: THREE.Mesh[] = []
let robotMesh: THREE.Mesh | null = null

function latLngToLocal(latlng: LatLng, origin: LatLng, scale = 8000) {
  return {
    x: (latlng.lng - origin.lng) * scale,
    z: -(latlng.lat - origin.lat) * scale,
  }
}

function buildScene() {
  const activeScene = scene
  if (!activeScene) return

  checkpointMeshes.forEach((m) => activeScene.remove(m))
  checkpointMeshes = []
  if (routeLine) {
    activeScene.remove(routeLine)
    routeLine = null
  }
  if (robotMesh) {
    activeScene.remove(robotMesh)
    robotMesh = null
  }

  const route = props.route
  if (!route || route.path.length === 0) return

  const origin = route.path[0]

  const points = route.path.map((p) => {
    const { x, z } = latLngToLocal(p, origin)
    return new THREE.Vector3(x, 0.3, z)
  })

  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  routeLine = new THREE.Line(
    geometry,
    new THREE.LineBasicMaterial({ color: 0x1a5fb4, linewidth: 3 }),
  )
  activeScene.add(routeLine)

  route.checkpoints.forEach((cp, i) => {
    const { x, z } = latLngToLocal(cp.position, origin)
    const geo = new THREE.CylinderGeometry(0.8, 0.8, 2, 16)
    const mat = new THREE.MeshStandardMaterial({
      color: i % 2 === 0 ? 0xe6a23c : 0xf56c6c,
      metalness: 0.3,
      roughness: 0.6,
    })
    const mesh = new THREE.Mesh(geo, mat)
    mesh.position.set(x, 1, z)
    activeScene.add(mesh)
    checkpointMeshes.push(mesh)
  })

  if (props.robotPosition) {
    const { x, z } = latLngToLocal(props.robotPosition, origin)
    const geo = new THREE.BoxGeometry(1.2, 0.8, 1.8)
    const mat = new THREE.MeshStandardMaterial({ color: 0x409eff })
    robotMesh = new THREE.Mesh(geo, mat)
    robotMesh.position.set(x, 0.6, z)
    activeScene.add(robotMesh)
  }
}

function animate() {
  animationId = requestAnimationFrame(animate)
  if (renderer && scene && camera) {
    camera.position.y = 45
    camera.lookAt(0, 0, 0)
    renderer.render(scene, camera)
  }
}

function onResize() {
  if (!container.value || !camera || !renderer) return
  const w = container.value.clientWidth
  const h = container.value.clientHeight
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

onMounted(() => {
  if (!container.value) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x1a2332)

  camera = new THREE.PerspectiveCamera(
    50,
    container.value.clientWidth / container.value.clientHeight,
    0.1,
    2000,
  )
  camera.position.set(0, 55, 40)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(container.value.clientWidth, container.value.clientHeight)
  container.value.appendChild(renderer.domElement)

  const ambient = new THREE.AmbientLight(0xffffff, 0.6)
  scene.add(ambient)
  const dir = new THREE.DirectionalLight(0xffffff, 0.9)
  dir.position.set(30, 50, 20)
  scene.add(dir)

  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(200, 200),
    new THREE.MeshStandardMaterial({ color: 0x2d3f52, roughness: 0.9 }),
  )
  ground.rotation.x = -Math.PI / 2
  scene.add(ground)

  const grid = new THREE.GridHelper(200, 40, 0x3d5a80, 0x2a3f55)
  scene.add(grid)

  for (let i = 0; i < 6; i++) {
    const tower = new THREE.Mesh(
      new THREE.BoxGeometry(2, 8 + Math.random() * 4, 2),
      new THREE.MeshStandardMaterial({ color: 0x8899aa }),
    )
    tower.position.set(-30 + i * 12, 4, -20 + (i % 3) * 15)
    scene.add(tower)
  }

  buildScene()
  animate()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  cancelAnimationFrame(animationId)
  window.removeEventListener('resize', onResize)
  renderer?.dispose()
  if (container.value && renderer?.domElement.parentNode === container.value) {
    container.value.removeChild(renderer.domElement)
  }
})

watch(() => [props.route, props.robotPosition], buildScene, { deep: true })
</script>

<style scoped>
.map3d {
  background: #1a2332;
}
</style>
