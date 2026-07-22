type MessageHandler<T> = (payload: T) => void

interface StompFrame {
  command: string
  headers: Record<string, string>
  body: string
}

interface TopicSubscription {
  id: string
  topic: string
  handler: MessageHandler<unknown>
}

const WS_URL = import.meta.env.VITE_WS_URL || 'wss://waang.top/ws'
const SESSION_KEY = 'pi_session'

let socket: WebSocket | null = null
let connected = false
let reconnectTimer: number | null = null
let subscriptionSeq = 0

const subscriptions = new Map<string, TopicSubscription>()

function accessToken() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY) || 'null') as { token?: string } | null
    return session?.token || null
  } catch {
    return null
  }
}

export function connectRealtime() {
  if (
    socket
    && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)
  ) {
    return
  }

  socket = new WebSocket(WS_URL)
  socket.onopen = () => {
    const token = accessToken()
    if (!token) {
      socket?.close()
      return
    }
    sendFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '10000,10000',
      Authorization: `Bearer ${token}`,
    })
  }
  socket.onmessage = (event) => {
    String(event.data)
      .split('\0')
      .map((raw) => raw.trim())
      .filter(Boolean)
      .forEach(handleFrame)
  }
  socket.onclose = () => {
    connected = false
    scheduleReconnect()
  }
  socket.onerror = () => {
    socket?.close()
  }
}

export function disconnectRealtime() {
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (socket?.readyState === WebSocket.OPEN) {
    sendFrame('DISCONNECT', {})
  }
  socket?.close()
  socket = null
  connected = false
}

export function subscribeTopic<T>(topic: string, handler: MessageHandler<T>) {
  const existing = subscriptions.get(topic)
  const item: TopicSubscription = existing ?? {
    id: `sub-${++subscriptionSeq}`,
    topic,
    handler: handler as MessageHandler<unknown>,
  }
  item.handler = handler as MessageHandler<unknown>
  subscriptions.set(topic, item)

  if (connected) {
    sendSubscribe(item)
  } else {
    connectRealtime()
  }

  return () => {
    const current = subscriptions.get(topic)
    if (!current) return
    if (connected) {
      sendFrame('UNSUBSCRIBE', { id: current.id })
    }
    subscriptions.delete(topic)
  }
}

function scheduleReconnect() {
  if (subscriptions.size === 0 || reconnectTimer) return
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectRealtime()
  }, 3000)
}

function handleFrame(raw: string) {
  const frame = parseFrame(raw)
  if (frame.command === 'CONNECTED') {
    connected = true
    subscriptions.forEach(sendSubscribe)
    return
  }
  if (frame.command !== 'MESSAGE') return

  const topic = frame.headers.destination
  const subscription = topic ? subscriptions.get(topic) : undefined
  if (!subscription) return

  try {
    subscription.handler(JSON.parse(frame.body))
  } catch {
    subscription.handler(frame.body)
  }
}

function parseFrame(raw: string): StompFrame {
  const [head, ...bodyParts] = raw.split('\n\n')
  const [command, ...headerLines] = head.split('\n')
  const headers: Record<string, string> = {}
  headerLines.forEach((line) => {
    const index = line.indexOf(':')
    if (index > 0) {
      headers[line.slice(0, index)] = line.slice(index + 1)
    }
  })
  return { command, headers, body: bodyParts.join('\n\n') }
}

function sendSubscribe(subscription: TopicSubscription) {
  sendFrame('SUBSCRIBE', {
    id: subscription.id,
    destination: subscription.topic,
  })
}

function sendFrame(command: string, headers: Record<string, string>, body = '') {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  const headerLines = Object.entries(headers)
    .map(([key, value]) => `${key}:${value}`)
  socket.send([command, ...headerLines, '', body].join('\n') + '\0')
}
