import { Client } from '@stomp/stompjs'

/**
 * STOMP 单例（@stomp/stompjs）。替代文档 Socket.IO 实时层。
 * 连接后端 /ws（SockJS 未启用，直接用原生 WS）。握手以 ?token= 传 JWT。
 * 所有场景前端仍保留轮询兜底：连接失败/未登录时 subscribe 仅登记，不报错。
 */
const listeners = new Map() // destination -> Set(cb)
let client = null
let connected = false

function wsUrl() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const token = localStorage.getItem('token') || ''
  return `${proto}://${window.location.host}/ws?token=${encodeURIComponent(token)}`
}

export function connectSocket() {
  if (client && (connected || client.active)) return
  if (!localStorage.getItem('token')) return
  client = new Client({
    brokerURL: wsUrl(),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      connected = true
      // 重连后重新订阅已登记的主题
      listeners.forEach((_cbs, dest) => {
        client.subscribe(dest, frame => dispatch(dest, frame))
      })
    },
    onDisconnect: () => { connected = false },
    onWebSocketClose: () => { connected = false },
    // 连接失败静默降级（前端轮询兜底）
    onStompError: () => { connected = false }
  })
  try {
    client.activate()
  } catch (e) {
    connected = false
  }
}

function dispatch(dest, frame) {
  const cbs = listeners.get(dest)
  if (!cbs) return
  let payload = frame.body
  try { payload = JSON.parse(frame.body) } catch (e) { /* 保留原始字符串 */ }
  cbs.forEach(cb => { try { cb(payload) } catch (e) { /* noop */ } })
}

/**
 * 订阅主题，返回取消订阅函数。
 */
export function subscribe(destination, cb) {
  if (!listeners.has(destination)) {
    listeners.set(destination, new Set())
    if (connected && client && client.active) {
      client.subscribe(destination, frame => dispatch(destination, frame))
    }
  }
  listeners.get(destination).add(cb)
  return () => {
    const set = listeners.get(destination)
    if (set) {
      set.delete(cb)
      if (set.size === 0) listeners.delete(destination)
    }
  }
}

export function disconnectSocket() {
  listeners.clear()
  connected = false
  if (client) {
    try { client.deactivate() } catch (e) { /* noop */ }
    client = null
  }
}

export function isConnected() {
  return connected
}
