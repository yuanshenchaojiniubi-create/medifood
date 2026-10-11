<template>
  <div class="notify-bell">
    <el-dropdown trigger="click" :hide-on-click="false" @visible-change="handleDropdownVisible">
      <span class="dropdown-trigger">
        <el-badge :value="unreadCount" :hidden="unreadCount===0">
          <el-button circle :icon="Bell" />
        </el-badge>
      </span>
      <template #dropdown>
        <el-dropdown-menu :class="['notify-menu', { 'expanded': isExpanded }]">
          <div class="notify-header">
            <span class="notify-title-text">消息通知</span>
            <el-button text size="small" @click.stop="toggleExpand">
              <el-icon><Expand v-if="!isExpanded" /><Fold v-else /></el-icon>
              {{ isExpanded ? '收起' : '放大' }}
            </el-button>
          </div>
          <div class="notify-list" :class="{ 'scrollable': isExpanded }">
            <el-dropdown-item v-for="n in items" :key="itemKey(n)" @click="go(n)" @click.stop>
              <div class="notify-item" :class="{ 'unread': n.isRead === 0 }">
                <div class="notify-content">
                  <div class="notify-main">
                    <div class="notify-title">
                      <el-badge is-dot :hidden="n.isRead === 1" class="unread-dot" />
                      {{ titleOf(n) }}
                    </div>
                    <div class="notify-time">{{ format(n.createTime) }}</div>
                  </div>
                  <el-button
                    class="notify-delete-btn"
                    text
                    size="small"
                    @click.stop="handleDelete(n)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </el-dropdown-item>
            <div v-if="items.length===0" class="notify-empty">暂无消息</div>
          </div>
          <el-dropdown-item divided @click="markAll">全部标记已读</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- 聚合通知参与者弹窗 -->
    <el-dialog v-model="aggregateDialog.visible" :title="aggregateDialog.title" width="360px" :close-on-click-modal="true">
      <div class="aggregate-actors-list">
        <div v-if="aggregateDialog.actors.length === 0" class="aggregate-loading">加载中...</div>
        <div v-for="actor in aggregateDialog.actors" :key="actor.userId" class="aggregate-actor-item" @click="router.push(`/user/${actor.userId}`); aggregateDialog.visible = false">
          <el-avatar :src="actor.avatar" :size="36">{{ actor.nickname?.[0] || 'U' }}</el-avatar>
          <span class="aggregate-actor-name">{{ actor.nickname }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Bell, Delete, Expand, Fold } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import request from '@/api/request'
import { markAllRead, listNotifications, markRead, markAggregateRead, getUnreadCount, deleteNotification, deleteAggregateNotification, enrichNotifications, getAggregateActors } from '@/api/notify'
import { getUnifiedUnreadCount } from '@/api/message'

const router = useRouter()
const userStore = useUserStore()
const unreadCount = ref(0)
const items = ref([])
const actorCache = new Map()
const titleCache = new Map()
const isExpanded = ref(false)
const isDropdownOpen = ref(false)
const aggregateDialog = ref({ visible: false, actors: [], title: '' })

/** 当前通知 SSE 的 AbortController，卸载或重连前必须 abort，避免占用浏览器并发连接 */
let streamAbortController = null

const disconnectStream = () => {
  if (streamAbortController) {
    streamAbortController.abort()
    streamAbortController = null
  }
}

const itemKey = (n) => {
  if (n?.isAggregate === 1 && n?.aggregateKey) return `agg-${n.aggregateKey}`
  return `msg-${n?.id}`
}

const entName = (t) => ({ 1: '知识', 2: '食谱', 3: '动态', 4: '问答' })[t] || '内容'

/**
 * 批量获取通知富文本数据（替代N+1查询）
 * 将所有通知的 actorIds 和 entityInfos 一次性发给后端，大幅减少HTTP请求
 */
const batchEnrich = async (notifyList) => {
  if (!notifyList || notifyList.length === 0) return

  // 收集所有需要查询的 actorId 和 entityInfo
  const actorIds = []
  const entityInfos = []
  const seenActors = new Set()
  const seenEntities = new Set()

  for (const n of notifyList) {
    if (n.actorId && !seenActors.has(n.actorId) && !actorCache.has(n.actorId)) {
      actorIds.push(n.actorId)
      seenActors.add(n.actorId)
    }
    if (n.entityType && n.entityId && !n.entityTitle) {
      const key = `${n.entityType}:${n.entityId}`
      if (!seenEntities.has(key) && !titleCache.has(key)) {
        entityInfos.push({ type: n.entityType, id: n.entityId })
        seenEntities.add(key)
      }
    }
  }

  if (actorIds.length === 0 && entityInfos.length === 0) return

  try {
    const res = await enrichNotifications(actorIds, entityInfos)
    const data = res?.data
    if (data?.actorNames) {
      for (const [id, name] of Object.entries(data.actorNames)) {
        actorCache.set(Number(id), name)
      }
    }
    if (data?.entityTitles) {
      for (const [key, title] of Object.entries(data.entityTitles)) {
        titleCache.set(key, title)
      }
    }
    // 将缓存的数据回填到通知列表
    for (const n of notifyList) {
      if (!n.actorName && n.actorId) {
        n.actorName = actorCache.get(n.actorId) || ''
      }
      if (!n.entityTitle && n.entityType && n.entityId) {
        n.entityTitle = titleCache.get(`${n.entityType}:${n.entityId}`) || ''
      }
    }
  } catch { /* ignore */ }
}

const connect = async () => {
  if (!userStore.isLoggedIn) return
  const uid = userStore.userInfo?.id
  if (uid == null || uid === '') return

  disconnectStream()
  const controller = new AbortController()
  streamAbortController = controller

  try {
    const baseURL = request.defaults.baseURL || ''
    const res = await fetch(`${baseURL}/community/notify/stream?uid=${uid}`, {
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${userStore.token}`,
        Accept: 'text/event-stream'
      }
    })
    if (!res.ok || !res.body) return

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const lines = buf.split('\n')
      buf = lines.pop() || ''
      for (const line of lines) {
        const s = line.trim()
        if (!s.startsWith('data:')) continue
        const json = s.substring(5).trim()
        if (!json || json === '[DONE]') continue
        try {
          const n = JSON.parse(json)
          const deltaUnread = Number(n.unreadCount || 1)
          // 防止同一聚合卡片在短时间内重复插入，直接替换已有项
          if (n.isAggregate === 1 && n.aggregateKey) {
            const idx = items.value.findIndex(x => x.isAggregate === 1 && x.aggregateKey === n.aggregateKey)
            if (idx >= 0) {
              const old = items.value[idx] || {}
              items.value[idx] = {
                ...old,
                ...n,
                isRead: 0,
                unreadCount: Number(old.unreadCount || 0) + deltaUnread,
                aggregateCount: Math.max(Number(old.aggregateCount || 0), Number(n.aggregateCount || 0))
              }
            } else {
              items.value.unshift(n)
            }
          } else {
            items.value.unshift(n)
          }
          // 批量获取新通知的富文本数据
          await batchEnrich([n])
          if (n.isRead === 0) unreadCount.value += deltaUnread
        } catch { /* 单条解析失败忽略 */ }
      }
    }
  } catch (e) {
    if (e?.name === 'AbortError') return
  }
}

const titleOf = (n) => {
  const actor = n.actorName || (n.actorId ? `用户${n.actorId}` : '有人')
  const ent = entName(n.entityType)
  const title = n.entityTitle || `${ent}#${n.entityId}`
  const aggCount = Number(n.aggregateCount || n.unreadCount || 0)
  if (n.isAggregate === 1 && aggCount > 1) {
    if (n.type === 1) return `${actor} 等 ${aggCount} 人赞了你的${ent}《${title}》`
    if (n.type === 5) return `${actor} 等 ${aggCount} 人收藏了你的${ent}《${title}》`
  }
  if (n.type === 1) return `${actor} 赞了你的${ent}《${title}》`
  if (n.type === 2) return `${actor} 评论了你的${ent}《${title}》`
  if (n.type === 3) return `${actor} 回复了你的${ent}《${title}》`
  if (n.type === 4) return `${actor} 关注了你`
  if (n.type === 5) return `${actor} 收藏了你的${ent}《${title}》`
  return `${actor} 的一条新消息`
}

const format = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleString('zh-CN')
}

const markAll = async () => {
  try {
    await markAllRead()
    unreadCount.value = 0
    items.value.forEach(n => { n.isRead = 1 })
  } catch { /* ignore */ }
}

const handleDropdownVisible = (visible) => {
  isDropdownOpen.value = visible
  if (!visible) {
    isExpanded.value = false
  }
}

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value
}

const handleDelete = async (n) => {
  try {
    if (n.isAggregate === 1 && n.aggregateKey) {
      await deleteAggregateNotification(n.aggregateKey)
    } else {
      await deleteNotification(n.id)
    }
    const idx = items.value.findIndex(item => itemKey(item) === itemKey(n))
    if (idx !== -1) {
      const deleted = items.value.splice(idx, 1)[0]
      if (deleted.isRead === 0) {
        const dec = Number(deleted.unreadCount || 1)
        unreadCount.value = Math.max(0, unreadCount.value - dec)
      }
    }
  } catch (e) {
    console.error('删除消息失败', e)
  }
}

const go = async (n) => {
  if (n.isRead === 0) {
    try {
      if (n.isAggregate === 1 && n.aggregateKey) {
        await markAggregateRead(n.aggregateKey)
      } else {
        await markRead(n.id)
      }
      n.isRead = 1
      const dec = Number(n.unreadCount || 1)
      unreadCount.value = Math.max(0, unreadCount.value - dec)
    } catch { /* ignore */ }
  }
  // 聚合通知：显示参与者列表
  if (n.isAggregate === 1 && n.aggregateKey) {
    showAggregateActors(n)
    return
  }
  if (n.type === 4) {
    router.push(`/user/${n.actorId}`)
    return
  }
  const routeMap = { 1: (id) => `/knowledge/${id}`, 2: (id) => `/recipe/${id}`, 3: (id) => `/community/post/${id}`, 4: (id) => `/qa/question/${id}` }
  const fn = routeMap[n.entityType]
  if (fn) router.push(fn(n.entityId))
}

/**
 * 显示聚合通知的参与者列表
 */
const showAggregateActors = async (n) => {
  aggregateDialog.value.title = titleOf(n)
  aggregateDialog.value.actors = []
  aggregateDialog.value.visible = true
  try {
    const res = await getAggregateActors(n.aggregateKey)
    if (res?.data) {
      aggregateDialog.value.actors = res.data || []
    }
  } catch { /* ignore */ }
}

const loadInitial = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const size = isExpanded.value ? 50 : 20
    const r = await listNotifications(1, size)
    const list = r?.data?.records || []
    // 批量获取富文本数据（替代逐个N+1查询）
    await batchEnrich(list)
    items.value = list

    // 使用统一未读计数（通知 + 私聊）
    try {
      const unified = await getUnifiedUnreadCount()
      unreadCount.value = Number(unified?.data?.totalUnread || 0)
    } catch {
      const counterResp = await getUnreadCount()
      unreadCount.value = Number(counterResp?.data || 0)
    }
  } catch { /* ignore */ }
}

const startNotify = () => {
  loadInitial()
  connect()
}

onMounted(() => {
  if (userStore.isLoggedIn) startNotify()
})

onUnmounted(() => {
  disconnectStream()
})

watch(
  () => userStore.isLoggedIn,
  (loggedIn, wasLoggedIn) => {
    if (loggedIn) {
      startNotify()
    } else if (wasLoggedIn) {
      disconnectStream()
      items.value = []
      unreadCount.value = 0
    }
  }
)

watch(
  () => userStore.userInfo,
  (info) => {
    if (info && userStore.isLoggedIn) {
      startNotify()
    }
  }
)
</script>

<style scoped>
.notify-bell { display: flex; align-items: center; }
.notify-menu { min-width: 320px; }
.notify-menu.expanded {
  min-width: 480px;
  max-width: 600px;
}
.notify-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}
.notify-title-text {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.notify-list {
  max-height: 360px;
  overflow-y: auto;
}
.notify-list.scrollable {
  max-height: 500px;
}
.notify-item { display: flex; flex-direction: column; width: 100%; padding: 4px 0; }
.notify-content {
  display: flex;
  align-items: center;
  width: 100%;
}
.notify-main {
  flex: 1;
  min-width: 0;
}
.notify-item.unread .notify-title { font-weight: bold; color: #303133; }
.notify-title { font-size: 14px; color: #606266; margin-bottom: 4px; display: flex; align-items: center; }
.notify-time { font-size: 12px; color: #909399; }
.notify-delete-btn {
  flex-shrink: 0;
  margin-left: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}
.notify-item:hover .notify-delete-btn {
  opacity: 1;
}
.notify-empty {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}
.header-notify { margin-right: 10px; }
.unread-dot { margin-right: 6px; }

.aggregate-actors-list {
  max-height: 360px;
  overflow-y: auto;
}

.aggregate-loading {
  text-align: center;
  padding: 20px;
  color: #909399;
}

.aggregate-actor-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.15s;
}

.aggregate-actor-item:hover {
  background: #f5f7fa;
}

.aggregate-actor-name {
  font-size: 14px;
  color: #303133;
}
</style>
