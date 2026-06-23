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

const WS_URL = import.meta.env.VITE_WS_URL || defaultWsUrl()

let socket: WebSocket | null = null
let connected = false
let reconnectTimer: number | null = null
let subscriptionSeq = 0

const subscriptions = new Map<string, TopicSubscription>()

function defaultWsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
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
    sendFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '10000,10000',
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
  const headerText = Object.entries(headers)
    .map(([key, value]) => `${key}:${value}`)
    .join('\n')
  socket.send(`${command}\n${headerText}\n\n${body}\0`)
}
