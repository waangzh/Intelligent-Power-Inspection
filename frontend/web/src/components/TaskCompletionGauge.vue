<template>
  <section
    class="completion-gauge"
    :aria-label="`任务完成率 ${safePercentage}%，已完成 ${safeCompleted} / ${safeTotal} 项`"
  >
    <div class="gauge-stage">
      <svg class="gauge-svg" viewBox="0 0 360 230" role="img" aria-hidden="true">
        <path class="gauge-track" :d="trackPath" />
        <path v-if="safePercentage > 0" class="gauge-progress" :d="progressPath" />
        <g class="gauge-ticks">
          <text
            v-for="tick in ticks"
            :key="tick.value"
            :x="tick.x"
            :y="tick.y"
            :text-anchor="tick.anchor"
            :class="{ endpoint: tick.endpoint }"
          >
            {{ tick.value }}%
          </text>
        </g>
      </svg>

      <div class="gauge-center-content">
        <strong>{{ safePercentage }}%</strong>
        <span>已完成 {{ safeCompleted }} / {{ safeTotal }} 项</span>
        <span v-if="statusLabel" class="gauge-status" :class="statusTone">{{ statusLabel }}</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    percentage: number
    completed: number
    total: number
    statusLabel?: string
    statusTone?: string
  }>(),
  {
    statusLabel: '',
    statusTone: 'neutral',
  },
)

const VIEWBOX_WIDTH = 360
const CENTER_X = VIEWBOX_WIDTH / 2
const CENTER_Y = 184
const ARC_RADIUS = 145
const TICK_RADIUS = ARC_RADIUS + 24
const START_ANGLE = 180
const ARC_ANGLE = 180
const TICK_VALUES = [0, 25, 50, 75, 100]

const safePercentage = computed(() => Math.min(100, Math.max(0, Math.round(Number(props.percentage) || 0))))
const safeCompleted = computed(() => Math.max(0, Math.round(Number(props.completed) || 0)))
const safeTotal = computed(() => Math.max(0, Math.round(Number(props.total) || 0)))

function pointOnArc(angle: number, radius: number) {
  const radians = (angle * Math.PI) / 180
  return {
    x: CENTER_X + radius * Math.cos(radians),
    y: CENTER_Y + radius * Math.sin(radians),
  }
}

function formatPoint(point: { x: number; y: number }) {
  return `${point.x.toFixed(2)} ${point.y.toFixed(2)}`
}

function arcPath(endAngle: number) {
  const start = pointOnArc(START_ANGLE, ARC_RADIUS)
  const end = pointOnArc(endAngle, ARC_RADIUS)
  const arcSpan = Math.abs(endAngle - START_ANGLE)
  const largeArcFlag = arcSpan > 180 ? 1 : 0
  return `M ${formatPoint(start)} A ${ARC_RADIUS} ${ARC_RADIUS} 0 ${largeArcFlag} 1 ${formatPoint(end)}`
}

const trackPath = arcPath(START_ANGLE + ARC_ANGLE)
const progressPath = computed(() => arcPath(START_ANGLE + (ARC_ANGLE * safePercentage.value) / 100))

const ticks = computed(() => TICK_VALUES.map((value) => {
  const angle = START_ANGLE + (ARC_ANGLE * value) / 100
  const endpoint = value === 0 || value === 100
  const point = pointOnArc(angle, TICK_RADIUS)

  if (value === 0) {
    return { value, x: point.x - 3, y: point.y + 25, anchor: 'end', endpoint }
  }
  if (value === 100) {
    return { value, x: point.x + 3, y: point.y + 25, anchor: 'start', endpoint }
  }
  return { value, x: point.x, y: point.y + 4, anchor: 'middle', endpoint }
}))
</script>

<style scoped>
.completion-gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.gauge-stage {
  position: relative;
  width: min(100%, 360px);
  aspect-ratio: 360 / 230;
}

.gauge-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.gauge-track,
.gauge-progress {
  fill: none;
  stroke-width: 18;
  stroke-linecap: round;
}

.gauge-track {
  stroke: #e7ecf5;
}

.gauge-progress {
  stroke: #2474ea;
}

.gauge-ticks text {
  fill: #8391a6;
  font-size: 14px;
  font-weight: 600;
}

.gauge-ticks text.endpoint {
  font-size: 14px;
}

.gauge-center-content {
  position: absolute;
  top: 52%;
  left: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: min(78%, 260px);
  transform: translateX(-50%);
  color: #102a56;
  text-align: center;
  white-space: nowrap;
  pointer-events: none;
}

.gauge-center-content strong {
  font-size: clamp(34px, 4vw, 38px);
  font-weight: 700;
  line-height: 1;
  letter-spacing: -1px;
}

.gauge-center-content > span:not(.gauge-status) {
  color: #64748b;
  font-size: clamp(15px, 2vw, 16px);
  font-weight: 500;
  line-height: 1.2;
}

.gauge-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: 24px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.gauge-status.good {
  border-color: #d3efd1;
  color: #59b96b;
  background: #eff9ee;
}

.gauge-status.watch {
  border-color: #ffe5bc;
  color: #dd8b19;
  background: #fff8eb;
}

.gauge-status.alert {
  border-color: #ffd8d6;
  color: #d64b46;
  background: #fff2f1;
}

.gauge-status.neutral {
  border-color: #e0e7f0;
  color: #718096;
  background: #f5f7fa;
}

@media (max-width: 480px) {
  .gauge-stage {
    width: min(100%, 320px);
  }

  .gauge-ticks text {
    font-size: 12px;
  }

  .gauge-ticks text.endpoint {
    font-size: 12px;
  }

  .gauge-center-content {
    top: 50%;
  }

  .gauge-center-content strong {
    font-size: 36px;
    letter-spacing: -1px;
  }

  .gauge-center-content > span:not(.gauge-status) {
    font-size: 14px;
  }

  .gauge-status {
    font-size: 12px;
  }
}
</style>
