import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./DetectionStrategy.vue', import.meta.url), 'utf8')

describe('DetectionStrategy running progress', () => {
  it('uses a moving activity block without a fake percentage', () => {
    expect(source).not.toContain(':percentage="100"')
    expect(source).not.toContain('<el-progress')
    expect(source).toContain('class="running-progress"')
    expect(source).toContain('class="running-progress__track"')
    expect(source).toContain('class="running-progress__indicator"')
    expect(source).toContain('@keyframes running-progress-slide')
  })

  it('shows elapsed time while the model is running', () => {
    expect(source).toContain('模型检测中')
    expect(source).toContain('manualElapsedLabel')
  })

  it('provides an overlay zoom action for the completed result image', () => {
    expect(source).toContain('preview-src-list')
    expect(source).toContain('class="result-image__zoom"')
    expect(source).toContain('aria-label="放大查看检测结果"')
    expect(source).toContain('@click="openResultImagePreview"')
    expect(source).toContain('resultImageRef.value?.showPreview?.()')
  })

  it('shows the alarm count and links a completed run to its alarms', () => {
    expect(source).toContain('alarmCount')
    expect(source).toContain('查看告警')
    expect(source).toContain('detectionRunId')
    expect(source).toContain("path: '/alarms'")
  })

  it('opens a run supplied by the existing detection page query', () => {
    expect(source).toContain('route.query.runId')
    expect(source).toContain('getManualLocateDetection')
    expect(source).toContain('detectionStore.getRun')
  })

  it('can persist robot-image risk rules back to the source checkpoint', () => {
    expect(source).toContain('保存到检查点')
    expect(source).toContain('saveCheckpointDetectionConfig')
    expect(source).toContain('routeStore.updateCheckpoint')
  })
})
