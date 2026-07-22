import { afterEach, describe, expect, it, vi } from 'vitest'

import { connectRealtime, disconnectRealtime } from './realtime'

class MockWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static instances: MockWebSocket[] = []

  readonly sent: string[] = []
  readyState = MockWebSocket.OPEN
  onopen: (() => void) | null = null

  constructor(readonly url: string) {
    MockWebSocket.instances.push(this)
  }

  send(payload: string) {
    this.sent.push(payload)
  }

  close() {
    this.readyState = 3
  }
}

describe('实时通信', () => {
  afterEach(() => {
    disconnectRealtime()
    MockWebSocket.instances = []
    vi.unstubAllGlobals()
  })

  it('使用生产 WSS 地址并发送无 payload 的 DISCONNECT 帧', () => {
    vi.stubGlobal('WebSocket', MockWebSocket)
    vi.stubGlobal('localStorage', {
      getItem: () => JSON.stringify({ token: 'test-token' }),
    })

    connectRealtime()
    const socket = MockWebSocket.instances[0]
    expect(socket?.url).toBe('wss://waang.top/ws')

    socket?.onopen?.()
    disconnectRealtime()

    expect(socket?.sent.at(-1)).toBe('DISCONNECT\n\n\0')
  })
})
