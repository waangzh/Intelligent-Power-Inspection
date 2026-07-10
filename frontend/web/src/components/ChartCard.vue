<template>
  <div ref="chartEl" class="chart-card" :style="{ height: height + 'px' }" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
const props = defineProps<{
  option: Record<string, unknown>
  height?: number
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function render() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption(props.option, true)
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  render()
  if (chartEl.value) {
    resizeObserver = new ResizeObserver(onResize)
    resizeObserver.observe(chartEl.value)
  }
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})

watch(() => props.option, render, { deep: true })
</script>

<style scoped>
.chart-card {
  width: 100%;
}
</style>
