import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useUserStore } from './user'
import { getChatUnreadCount } from '@/api/message'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

export const useChatStore = defineStore('chat', () => {
  const userStore = useUserStore()

  let stompClient = null
  let reconnectTimer = null
  let heartbeatTimer = null

  const connected = ref(false)
  const activeChatPeerId = ref(null)
  const toasts = ref([])
  const subscribers = new Map()
  /** 在线状态映射: userId -> boolean */
  const onlineStatus = ref({})
  /** 已读回执回调映射: peerId -> callback */
  const readReceiptCallbacks = new Map()

  let toastIdCounter = 0

  const addToast = (msg) => {
    const t = {
      id: ++toastIdCounter,
      messageId: msg.id,
      senderId: msg.senderId,
      senderName: msg.senderName || `用户${msg.senderId}`,
      avatar: msg.avatar || '',
      content: msg.content || '',
      messageType: msg.messageType || 'TEXT',
      createdAt: msg.createdAt || new Date().toISOString()
    }
    toasts.value.push(t)
    setTimeout(() => {
      removeToast(t.id)
    }, 6000)
  }

  const removeToast = (id) => {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  const getMessagePreview = (msg) => {
    if (msg.messageType === 'IMAGE') return '[图片]'
    if (msg.messageType === 'VOICE') return '[语音]'
    if (msg.messageType === 'SHARE') {
      try {
        const s = JSON.parse(msg.content)
        const map = { KNOWLEDGE: '知识', RECIPE: '食谱', POST: '动态' }
        return `[分享${map[s.shareType] || ''}] ${s.shareTitle || ''}`
      } catch { return '[分享]' }
    }
    return (msg.content || '').substring(0, 40)
  }

  const onMessage = (msg) => {
    try {
      const data = JSON.parse(msg.body)
      data.senderId = Number(data.senderId)

      if (activeChatPeerId.value === data.senderId) {
        const cb = subscribers.get(data.senderId)
        if (cb) cb(data)
      } else {
        addToast(data)
      }
    } catch { /* ignore */ }
  }

  const onRecall = (msg) => {
    try {
      const data = JSON.parse(msg.body)
      if (data.type === 'RECALL') {
        const cb = subscribers.get(Number(data.senderId))
        if (cb) {
          cb({ recallMessageId: data.messageId, senderId: Number(data.senderId) })
        }
      }
    } catch { /* ignore */ }
  }

  /**
   * 处理已读回执
   */
  const onReadReceipt = (msg) => {
    try {
      const data = JSON.parse(msg.body)
      if (data.type === 'READ_RECEIPT') {
        const readerId = Number(data.readerId)
        const cb = readReceiptCallbacks.get(readerId)
        if (cb) {
          cb({
            type: 'READ_RECEIPT',
            readerId: readerId,
            messageIds: data.messageIds || [],
            count: data.count || 0
          })
        }
      }
    } catch { /* ignore */ }
  }

  /**
   * 处理在线状态变更广播
   */
  const onOnlineStatusChange = (msg) => {
    try {
      const data = JSON.parse(msg.body)
      if (data.type === 'ONLINE_STATUS') {
        onlineStatus.value = {
          ...onlineStatus.value,
          [data.userId]: data.online
        }
      }
    } catch { /* ignore */ }
  }

  const connect = () => {
    if (!userStore.token) return
    if (stompClient?.active) return

    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws/message'),
      connectHeaders: {
        Authorization: `Bearer ${userStore.token}`
      },
      debug: () => {},
      reconnectDelay: 5000,
      onConnect: () => {
        connected.value = true

        stompClient.subscribe('/user/queue/private.message', onMessage)
        stompClient.subscribe('/user/queue/private.recall', onRecall)
        stompClient.subscribe('/user/queue/private.read-receipt', onReadReceipt)
        stompClient.subscribe('/topic/online.status', onOnlineStatusChange)

        // 启动心跳（每10秒发送一次）
        startHeartbeat()

        checkUnreadOnLogin()
      },
      onStompError: () => {
        connected.value = false
      },
      onDisconnect: () => {
        connected.value = false
        stopHeartbeat()
      }
    })

    stompClient.activate()
  }

  const disconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat()
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
    subscribers.clear()
    readReceiptCallbacks.clear()
    toasts.value = []
    activeChatPeerId.value = null
    onlineStatus.value = {}
  }

  /**
   * 启动心跳定时器
   */
  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (stompClient?.active && stompClient?.connected) {
        try {
          stompClient.publish({
            destination: '/app/chat.heartbeat',
            body: '{}'
          })
        } catch { /* ignore */ }
      }
    }, 10000)
  }

  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const sendMessage = (bodyObj) => {
    if (!stompClient?.active || !stompClient?.connected) {
      return false
    }
    try {
      stompClient.publish({
        destination: '/app/chat.private.send',
        body: JSON.stringify(bodyObj)
      })
      return true
    } catch (e) {
      console.warn('STOMP publish failed, fallback to REST:', e)
      return false
    }
  }

  const setActiveChat = (peerId) => {
    activeChatPeerId.value = peerId
  }

  const clearActiveChat = () => {
    activeChatPeerId.value = null
  }

  const subscribe = (peerId, callback) => {
    subscribers.set(peerId, callback)
  }

  const unsubscribe = (peerId) => {
    subscribers.delete(peerId)
  }

  /**
   * 注册已读回执回调
   */
  const onReadReceiptFor = (peerId, callback) => {
    readReceiptCallbacks.set(peerId, callback)
  }

  /**
   * 取消已读回执回调
   */
  const offReadReceiptFor = (peerId) => {
    readReceiptCallbacks.delete(peerId)
  }

  const dismissToast = (id) => {
    removeToast(id)
  }

  let hasCheckedUnread = false

  const checkUnreadOnLogin = async () => {
    if (hasCheckedUnread) return
    hasCheckedUnread = true
    try {
      const res = await getChatUnreadCount()
      const count = res?.data?.chatUnread ?? 0
      if (count > 0) {
        toasts.value.push({
          id: --toastIdCounter,
          messageId: null,
          senderId: -1,
          senderName: '消息通知',
          avatar: '',
          content: `您有 ${count} 条未读私信消息`,
          messageType: 'TEXT',
          createdAt: new Date().toISOString(),
          isUnreadTip: true
        })
        setTimeout(() => {
          removeToast(-toastIdCounter)
        }, 8000)
      }
    } catch { /* ignore */ }
  }

  return {
    connected,
    activeChatPeerId,
    toasts,
    onlineStatus,
    getMessagePreview,
    connect,
    disconnect,
    sendMessage,
    setActiveChat,
    clearActiveChat,
    subscribe,
    unsubscribe,
    onReadReceiptFor,
    offReadReceiptFor,
    dismissToast,
    removeToast
  }
})