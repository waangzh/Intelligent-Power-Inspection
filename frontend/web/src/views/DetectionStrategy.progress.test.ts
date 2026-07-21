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
})
