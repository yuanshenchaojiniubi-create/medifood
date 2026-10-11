<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <div class="message-center">
        <div class="mc-header">
          <h2>消息中心</h2>
        </div>
        <div class="mc-body">
      <div class="mc-tabs">
        <div :class="['mc-tab', { active: activeTab === 'notify' }]" @click="activeTab = 'notify'">
          <el-badge :value="notifyUnread" :hidden="notifyUnread === 0" :max="99">
            <span>通知</span>
          </el-badge>
        </div>
        <div :class="['mc-tab', { active: activeTab === 'chat' }]" @click="activeTab = 'chat'">
          <el-badge :value="chatUnread" :hidden="chatUnread === 0" :max="99">
            <span>私信</span>
          </el-badge>
        </div>
      </div>

      <div v-if="activeTab === 'notify'" class="mc-list">
        <div v-if="notifications.length === 0" class="mc-empty">暂无通知</div>
        <div v-for="n in notifications" :key="notifyKey(n)" class="mc-item" :class="{ unread: n.isRead === 0 }" @click="goNotify(n)">
          <div class="mc-item-content">
            <div class="mc-item-title">
              <el-badge is-dot :hidden="n.isRead === 1" class="unread-dot" />
              {{ notifyTitle(n) }}
            </div>
            <div class="mc-item-time">{{ formatTime(n.createTime) }}</div>
          </div>
        </div>
        <div class="mc-pagination" v-if="notifyTotal > 10">
          <el-pagination small background layout="prev, next" :total="notifyTotal" :page-size="10" v-model:current-page="notifyPage" @current-change="fetchNotifications" />
        </div>
      </div>

      <div v-if="activeTab === 'chat'" class="mc-list">
        <div v-if="conversations.length === 0" class="mc-empty">暂无私信</div>
        <div v-for="c in conversations" :key="c.userId" class="mc-item" @click="$router.push(`/messages/chat/${c.userId}`)">
          <div class="mc-avatar-wrap">
            <el-avatar :src="c.avatar" :size="45">{{ c.nickname?.[0] || 'U' }}</el-avatar>
            <span :class="['mc-online-dot', { online: c.isOnline }]"></span>
          </div>
          <div class="mc-conv-content">
            <div class="mc-conv-top">
              <span class="mc-conv-name">{{ c.nickname }}</span>
              <span class="mc-conv-time">{{ c.lastTime }}</span>
            </div>
            <div class="mc-conv-bottom">
              <span class="mc-conv-preview">
                <span v-if="c.lastMessageRead === false" class="mc-unread-flag">[未读]</span>
                {{ convPreview(c) }}
              </span>
              <el-badge v-if="c.unread > 0" :value="c.unread" />
            </div>
          </div>
        </div>
      </div>
      </div>
      </div>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import { listNotifications, markRead, markAggregateRead } from '@/api/notify'
import { getConversations, getChatUnreadCount } from '@/api/message'
import { getUnreadCount } from '@/api/notify'

const router = useRouter()
const activeTab = ref('notify')
const notifications = ref([])
const notifyTotal = ref(0)
const notifyPage = ref(1)
const notifyUnread = ref(0)
const conversations = ref([])
const chatUnread = ref(0)

const notifyKey = (n) => {
  if (n?.isAggregate === 1 && n?.aggregateKey) return `agg-${n.aggregateKey}`
  return `msg-${n?.id}`
}

const entName = (t) => ({ 1: '知识', 2: '食谱', 3: '动态', 4: '问答' })[t] || '内容'

const notifyTitle = (n) => {
  const actor = n.actorName || `用户${n.actorId}`
  const ent = entName(n.entityType)
  const title = n.entityTitle || `${ent}#${n.entityId}`
  const agg = Number(n.aggregateCount || n.unreadCount || 0)
  if (n.isAggregate === 1 && agg > 1) {
    if (n.type === 1) return `${actor} 等 ${agg} 人赞了你的${ent}《${title}》`
    if (n.type === 5) return `${actor} 等 ${agg} 人收藏了你的${ent}《${title}》`
  }
  if (n.type === 1) return `${actor} 赞了你的${ent}《${title}》`
  if (n.type === 2) return `${actor} 评论了你的${ent}《${title}》`
  if (n.type === 3) return `${actor} 回复了你的${ent}《${title}》`
  if (n.type === 4) return `${actor} 关注了你`
  if (n.type === 5) return `${actor} 收藏了你的${ent}《${title}》`
  return `${actor} 的一条新消息`
}

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

const goNotify = async (n) => {
  if (n.isRead === 0) {
    try {
      if (n.isAggregate === 1 && n.aggregateKey) {
        await markAggregateRead(n.aggregateKey)
      } else {
        await markRead(n.id)
      }
      n.isRead = 1
    } catch { /* ignore */ }
  }
  const map = { 1: '/knowledge', 2: '/recipe', 3: '/community/post', 4: '/qa/question' }
  const base = map[n.entityType]
  if (base && n.entityId) {
    router.push(`${base}/${n.entityId}`)
  }
}

const fetchNotifications = async () => {
  try {
    const res = await listNotifications(notifyPage.value, 10)
    if (res?.data) {
      notifications.value = res.data.records || []
      notifyTotal.value = res.data.total || 0
    }
  } catch { /* ignore */ }
}

const fetchChatList = async () => {
  try {
    const res = await getConversations()
    if (res?.data) {
      conversations.value = res.data || []
    }
  } catch { /* ignore */ }
}

const fetchUnreads = async () => {
  try {
    const nRes = await getUnreadCount()
    notifyUnread.value = nRes?.data ?? 0
  } catch { /* ignore */ }
  try {
    const cRes = await getChatUnreadCount()
    chatUnread.value = cRes?.data?.chatUnread ?? 0
  } catch { /* ignore */ }
}

const parseSharePreview = (msg) => {
  try {
    const s = JSON.parse(msg)
    if (s.shareType) {
      const map = { KNOWLEDGE: '知识', RECIPE: '食谱', POST: '动态' }
      return `[分享${map[s.shareType] || ''}] ${s.shareTitle || ''}`
    }
  } catch { /* ignore */ }
  return null
}

const convPreview = (c) => {
  const sharePreview = parseSharePreview(c.lastMessage)
  if (sharePreview) return sharePreview
  return c.lastMessage || '暂无消息'
}

onMounted(() => {
  fetchNotifications()
  fetchChatList()
  fetchUnreads()
})
</script>

<style scoped>
.message-center {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  overflow: hidden;
}

.mc-header h2 {
  margin: 0;
  padding: 16px 20px;
  font-size: 18px;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
}

.mc-body {
  overflow: hidden;
}

.mc-tabs {
  display: flex;
  border-bottom: 1px solid #ebeef5;
}

.mc-tab {
  flex: 1;
  text-align: center;
  padding: 14px 0;
  cursor: pointer;
  font-size: 15px;
  color: #606266;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.mc-tab:hover {
  color: #409eff;
}

.mc-tab.active {
  color: #409eff;
  border-bottom-color: #409eff;
  font-weight: 600;
}

.mc-list {
  min-height: 300px;
}

.mc-empty {
  text-align: center;
  padding: 60px 20px;
  color: #c0c4cc;
  font-size: 14px;
}

.mc-item {
  display: flex;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.15s;
}

.mc-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.mc-online-dot {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #c0c4cc;
  border: 2px solid #fff;
}

.mc-online-dot.online {
  background: #67c23a;
  box-shadow: 0 0 4px rgba(103, 194, 58, 0.5);
}

.mc-item:hover {
  background: #f5f7fa;
}

.mc-item.unread {
  background: #ecf5ff;
}

.mc-item-content {
  flex: 1;
}

.mc-item-title {
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.mc-item-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.mc-conv-content {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}

.mc-conv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mc-conv-name {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}

.mc-conv-time {
  font-size: 12px;
  color: #c0c4cc;
}

.mc-conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.mc-conv-preview {
  font-size: 13px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 500px;
}

.mc-unread-flag {
  color: #f56c6c;
  font-size: 11px;
  margin-right: 2px;
}

.mc-pagination {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

.unread-dot {
  flex-shrink: 0;
}
</style>