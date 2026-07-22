import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./AlarmCenter.vue', import.meta.url), 'utf8')

describe('AlarmCenter detection provenance', () => {
  it('renders the detection item and source fields in the alarm detail', () => {
    expect(source).toContain('sourceType')
    expect(source).toContain('detectionRunId')
    expect(source).toContain('imageId')
    expect(source).toContain('checkpointId')
    expect(source).toContain('itemName')
    expect(source).toContain('displayLabel')
    expect(source).not.toContain('return alarm.itemId')
    expect(source).toContain('finding')
  })

  it('supports the detection-run query and links back to the existing detection route', () => {
    expect(source).toContain('detectionRunId')
    expect(source).toContain("path: '/detection'")
    expect(source).toContain('query: { runId')
  })
})
