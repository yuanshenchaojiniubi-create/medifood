<template>
  <div class="private-chat" @click="contextMenu.visible = false">
    <div class="pc-header">
      <el-button text @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <el-avatar :src="peerUser.avatar" :size="36">{{ peerUser.nickname?.[0] || 'U' }}</el-avatar>
      <span class="pc-peer-name">{{ peerUser.nickname }}</span>
      <span :class="['pc-online-dot', { online: peerUser.isOnline }]" :title="peerUser.isOnline ? '在线' : '离线'"></span>
      <span class="pc-online-text">{{ peerUser.isOnline ? '在线' : '离线' }}</span>
      <el-tag v-if="chatStatus.isMutualFollow" type="success" size="small" effect="light">互相关注</el-tag>
      <span v-else class="pc-limit-hint">（互关后可畅聊）</span>
    </div>

    <div class="pc-messages" ref="msgContainer">
      <div v-if="messages.length === 0" class="pc-empty">暂无消息，打个招呼吧</div>
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['pc-msg', { mine: msg.senderId === myUserId }]"
        @mouseenter="hoveredMsgId = msg.id"
        @mouseleave="hoveredMsgId = null"
        @contextmenu.prevent="onContextMenu($event, msg)"
        :id="`msg-${msg.id}`"
      >
        <template v-if="msg.senderId !== myUserId">
          <el-avatar :src="peerUser.avatar" :size="32" class="pc-msg-avatar">{{ peerUser.nickname?.[0] || 'U' }}</el-avatar>
        </template>
        <div class="pc-msg-bubble-wrapper">
          <div v-if="msg.quoteMessageId" class="pc-msg-quote" @click="scrollToMsg(msg.quoteMessageId)">
            <span class="pc-quote-label">回复</span>
            <span class="pc-quote-name">{{ quoteSenderName(msg) }}</span>
            <span class="pc-quote-text">{{ msg.quoteContent || '...' }}</span>
          </div>
          <div :class="['pc-msg-bubble', { recalled: msg.isRecalled === 1 }]">
            <template v-if="msg.isRecalled === 1">
              <span class="pc-recalled-text">消息已撤回</span>
            </template>
            <template v-else>
              <div v-if="msg.messageType === 'IMAGE' && msg.imageUrl" class="pc-msg-image">
                <img :src="msg.imageUrl" alt="图片" />
              </div>
              <div v-else-if="msg.messageType === 'SHARE'" class="pc-msg-share" @click="goShare(msg)">
                <div class="share-type-tag">{{ shareTypeName(msg) }}</div>
                <div class="share-title">{{ shareTitle(msg) }}</div>
              </div>
              <div v-else-if="msg.messageType === 'VOICE'" class="pc-msg-voice">
                <VoiceBubble
                  :audio-url="parseVoice(msg).audioUrl"
                  :duration="parseVoice(msg).duration"
                  :mine="msg.senderId === myUserId"
                />
                <span v-if="parseVoice(msg).transcription" class="pc-voice-text">{{ parseVoice(msg).transcription }}</span>
              </div>
              <div v-else class="pc-msg-text">{{ msg.content }}</div>
            </template>
          </div>
          <div v-if="hoveredMsgId === msg.id && msg.isRecalled === 0" class="pc-msg-actions" :class="{ mine: msg.senderId === myUserId }">
            <span class="pc-action-btn" @click="startReply(msg)">引用</span>
            <span v-if="msg.senderId === myUserId && canRecall(msg)" class="pc-action-btn" @click="doRecall(msg)">撤回</span>
          </div>
          <!-- 已读回执标签（仅对自己发送的消息显示） -->
          <div v-if="msg.senderId === myUserId && msg.isRecalled === 0" class="pc-read-status">
            <span v-if="msg.isRead === 1" class="pc-read-tag">已读</span>
            <span v-else class="pc-unread-tag">未读</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="contextMenu.visible" class="pc-context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
      <div class="pc-cm-item" @click="startReplyFromMenu(); contextMenu.visible = false">引用</div>
      <div v-if="canRecallById(contextMenu.msgId)" class="pc-cm-item" @click="doRecallById(contextMenu.msgId); contextMenu.visible = false">撤回</div>
    </div>

    <div v-if="!canSend" class="pc-blocked">
      <el-icon><WarningFilled /></el-icon>
      <span v-if="requestStatus === 'PENDING'">好友申请已发送，等待对方同意...</span>
      <span v-else-if="peerRequestStatus === 'PENDING'">
        <template v-if="!chatStatus.isMutualFollow">对方想加你为好友</template>
        <el-button type="primary" size="small" @click="acceptReq">同意</el-button>
        <el-button size="small" @click="rejectReq">拒绝</el-button>
      </span>
      <span v-else>互相关注后才能聊天</span>
      <el-button v-if="requestStatus !== 'PENDING' && peerRequestStatus !== 'PENDING'" type="primary" size="small" @click="sendFriendReq">加好友</el-button>
    </div>

    <div class="pc-input" v-else>
          <div v-if="replyTo" class="pc-reply-bar">
            <span class="pc-reply-label">回复 {{ quoteSenderName(replyTo) }}</span>
            <span class="pc-reply-preview">{{ replyPreview(replyTo) }}</span>
            <el-button text size="small" @click="cancelReply">取消</el-button>
          </div>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入消息..."
            resize="none"
            @keydown.enter.exact.prevent="send"
          />
          <el-button
            class="pc-voice-btn"
            :icon="Microphone"
            circle
            @mousedown.prevent="startVoiceRecord"
            @touchstart.prevent="startVoiceRecord"
          />
          <el-button type="primary" :disabled="!inputText.trim()" @click="send">发送</el-button>
        </div>
  </div>
  <VoiceRecorder v-model="showVoiceRecorder" @send="sendVoice" />
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { getMessagesWithUser, getChatStatus, recallMessage, sendFriendRequest, acceptFriendRequest, rejectFriendRequest, getRequestStatus } from '@/api/message'
import { ArrowLeft, WarningFilled, Microphone } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import VoiceRecorder from '@/components/VoiceRecorder.vue'
import VoiceBubble from '@/components/VoiceBubble.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const peerUserId = Number(route.params.userId)
const myUserId = computed(() => userStore.userInfo?.id)

const messages = ref([])
const inputText = ref('')
const msgContainer = ref(null)
const canSend = ref(true)
const showVoiceRecorder = ref(false)
const requestStatus = ref(null)       // 我发给对方的申请状态
const peerRequestStatus = ref(null)   // 对方发给我的申请状态
const pendingRequestId = ref(null)    // 当前待处理的申请ID

const startVoiceRecord = () => {
  showVoiceRecorder.value = true
}

const replyTo = ref(null)
const hoveredMsgId = ref(null)
const contextMenu = ref({ visible: false, x: 0, y: 0, msgId: null })

const peerUser = reactive({
  nickname: `用户${peerUserId}`,
  avatar: '',
  isOnline: false
})

const chatStatus = reactive({
  isMutualFollow: false,
  mySentCount: 0,
  peerSentCount: 0
})

const handleIncomingMessage = (data) => {
  if (data.recallMessageId) {
    const target = messages.value.find(m => m.id === data.recallMessageId)
    if (target) {
      target.isRecalled = 1
    }
    return
  }
  messages.value.push({
    id: data.id,
    senderId: Number(data.senderId),
    content: data.content,
    messageType: data.messageType || 'TEXT',
    imageUrl: data.imageUrl,
    isRecalled: data.isRecalled || 0,
    quoteMessageId: data.quoteMessageId,
    quoteContent: data.quoteContent,
    quoteSenderId: data.quoteSenderId,
    createdAt: data.createdAt
  })
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    const el = msgContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

const loadHistory = async () => {
  try {
    const res = await getMessagesWithUser(peerUserId, 1, 200)
    if (res?.data?.records) {
      messages.value = res.data.records.reverse()
      scrollToBottom()
    }
  } catch { /* ignore */ }
}

const loadStatus = async () => {
  try {
    const res = await getChatStatus(peerUserId)
    if (res?.data) {
      chatStatus.isMutualFollow = res.data.isMutualFollow ?? false
      chatStatus.mySentCount = res.data.mySentCount ?? 0
      chatStatus.peerSentCount = res.data.peerSentCount ?? 0
      canSend.value = res.data.canSend ?? true
      peerUser.isOnline = res.data.isOnline ?? false
      // 使用后端返回的真实昵称和头像
      if (res.data.nickname) {
        peerUser.nickname = res.data.nickname
      }
      if (res.data.avatar) {
        peerUser.avatar = res.data.avatar
      }
    }
  } catch { /* ignore */ }
}

/**
 * 查询好友申请状态
 */
const loadRequestStatus = async () => {
  try {
    const res = await getRequestStatus(peerUserId)
    if (res?.data) {
      requestStatus.value = res.data.myRequestStatus || null
      peerRequestStatus.value = res.data.peerRequestStatus || null
      pendingRequestId.value = res.data.peerRequestId || null
      // 如果已经是好友，可能还没有互关（但申请已通过），需要刷新chatStatus
      if (res.data.isMutualFollow) {
        canSend.value = true
        chatStatus.isMutualFollow = true
      }
    }
  } catch { /* ignore */ }
}

/**
 * 发送好友申请
 */
const sendFriendReq = async () => {
  try {
    await sendFriendRequest(peerUserId, '我想和你成为好友')
    requestStatus.value = 'PENDING'
    ElMessage.success('好友申请已发送')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发送失败')
  }
}

/**
 * 同意好友申请
 */
const acceptReq = async () => {
  try {
    await acceptFriendRequest(pendingRequestId.value)
    peerRequestStatus.value = 'ACCEPTED'
    canSend.value = true
    chatStatus.isMutualFollow = true
    ElMessage.success('已同意好友申请，现在可以聊天了')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

/**
 * 拒绝好友申请
 */
const rejectReq = async () => {
  try {
    await rejectFriendRequest(pendingRequestId.value)
    peerRequestStatus.value = 'REJECTED'
    ElMessage.info('已拒绝好友申请')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

/**
 * 处理已读回执：当对方阅读了我的消息时，更新本地消息的已读状态
 */
const handleReadReceipt = (data) => {
  if (data.type === 'READ_RECEIPT' && data.readerId === peerUserId) {
    const readIds = new Set(data.messageIds || [])
    for (const msg of messages.value) {
      if (msg.senderId === myUserId.value && readIds.has(msg.id)) {
        msg.isRead = 1
      }
    }
  }
}

onMounted(async () => {
  chatStore.setActiveChat(peerUserId)
  chatStore.subscribe(peerUserId, handleIncomingMessage)
  chatStore.onReadReceiptFor(peerUserId, handleReadReceipt)
  await loadHistory()
  await loadStatus()
  await loadRequestStatus()
})

onUnmounted(() => {
  chatStore.clearActiveChat()
  chatStore.unsubscribe(peerUserId)
  chatStore.offReadReceiptFor(peerUserId)
})

const send = async () => {
    const text = inputText.value.trim()
    if (!text) return

    if (!chatStatus.isMutualFollow && chatStatus.mySentCount >= 1) {
      ElMessage.warning('互相关注后才能继续聊天。快去关注对方吧！')
      return
    }

    const bodyObj = {
      receiverId: peerUserId,
      content: text,
      messageType: 'TEXT'
    }
    if (replyTo.value) {
      bodyObj.quoteMessageId = replyTo.value.id
    }

    const ok = chatStore.sendMessage(bodyObj)
    if (!ok) {
      try {
        const { sendMessage: sendRest } = await import('@/api/message')
        await sendRest(bodyObj)
      } catch {
        ElMessage.error('发送失败，请重试')
        return
      }
    }

    const localMsg = {
      id: Date.now(),
      senderId: myUserId.value,
      content: text,
      messageType: 'TEXT',
      createdAt: new Date().toISOString(),
      isRecalled: 0
    }
    if (replyTo.value) {
      localMsg.quoteMessageId = replyTo.value.id
      localMsg.quoteContent = replyPreview(replyTo.value)
      localMsg.quoteSenderId = replyTo.value.senderId
    }
    messages.value.push(localMsg)
    replyTo.value = null
    inputText.value = ''
    scrollToBottom()

    if (!chatStatus.isMutualFollow) {
      chatStatus.mySentCount++
      if (chatStatus.mySentCount >= 1) {
        canSend.value = false
      }
    }
  }

const sendVoice = async (voiceData) => {
    const contentJson = JSON.stringify({
      audioUrl: voiceData.audioUrl,
      duration: voiceData.duration,
      transcription: voiceData.transcription || ''
    })
    const bodyObj = {
      receiverId: peerUserId,
      content: contentJson,
      messageType: 'VOICE'
    }
    const ok = chatStore.sendMessage(bodyObj)
    if (!ok) {
      try {
        const { sendMessage: sendRest } = await import('@/api/message')
        await sendRest(bodyObj)
      } catch { /* ignore */ }
    }
    messages.value.push({
      id: Date.now(),
      senderId: myUserId.value,
      content: contentJson,
      messageType: 'VOICE',
      createdAt: new Date().toISOString()
    })
    scrollToBottom()
  }

const goFollow = () => {
    router.push(`/user/${peerUserId}`)
  }

  const startReply = (msg) => {
    replyTo.value = msg
    inputText.value = ''
  }

  const startReplyFromMenu = () => {
    const msg = messages.value.find(m => m.id === contextMenu.value.msgId)
    if (msg) {
      startReply(msg)
    }
  }

  const cancelReply = () => {
    replyTo.value = null
  }

  const canRecall = (msg) => {
    const diff = (Date.now() - new Date(msg.createdAt).getTime()) / 60000
    return diff < 2
  }

  const canRecallById = (msgId) => {
    const msg = messages.value.find(m => m.id === msgId)
    return msg && msg.senderId === myUserId.value && canRecall(msg)
  }

  const doRecall = async (msg) => {
    try {
      await recallMessage(msg.id)
      msg.isRecalled = 1
      ElMessage.success('消息已撤回')
    } catch {
      ElMessage.error('撤回失败，请稍后重试')
    }
  }

  const doRecallById = async (msgId) => {
    const msg = messages.value.find(m => m.id === msgId)
    if (msg) doRecall(msg)
  }

  const onContextMenu = (e, msg) => {
    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      msgId: msg.id
    }
  }

  const quoteSenderName = (msg) => {
    if (!msg) return ''
    if (msg.quoteSenderId === myUserId.value) return '我'
    return peerUser.nickname || '对方'
  }

  const replyPreview = (msg) => {
    if (!msg) return ''
    if (msg.messageType === 'IMAGE') return '[图片]'
    if (msg.messageType === 'VOICE') return '[语音]'
    return (msg.content || '').substring(0, 30)
  }

  const scrollToMsg = (msgId) => {
    const el = document.getElementById(`msg-${msgId}`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      el.classList.add('pc-msg-highlight')
      setTimeout(() => el.classList.remove('pc-msg-highlight'), 2000)
    }
  }

  const parseShare = (msg) => {
  try {
    return JSON.parse(msg.content)
  } catch {
    return null
  }
}

const shareTypeName = (msg) => {
  const s = parseShare(msg)
  const map = { KNOWLEDGE: '知识', RECIPE: '食谱', POST: '动态' }
  return map[s?.shareType] || '未知'
  }

  const parseVoice = (msg) => {
    try {
      return JSON.parse(msg.content)
    } catch {
      return { audioUrl: msg.voiceUrl || '', duration: msg.duration || 0, transcription: msg.transcription || '' }
    }
  }

  const formatVoiceTime = (s) => {
    const dur = Number(s) || 0
    const m = Math.floor(dur / 60)
    const sec = dur % 60
    return m > 0 ? `${m}'${String(sec).padStart(2, '0')}''` : `${sec}''`
  }

const shareTitle = (msg) => {
  const s = parseShare(msg)
  return s?.shareTitle || '查看详情'
}

const goShare = (msg) => {
  const s = parseShare(msg)
  if (!s) return
  const map = { KNOWLEDGE: '/knowledge', RECIPE: '/recipe', POST: '/community/post' }
  const path = map[s.shareType]
  if (path && s.shareId) {
    router.push(`${path}/${s.shareId}`)
  }
}

</script>

<style scoped>
.private-chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  max-width: 750px;
  margin: 0 auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.pc-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.pc-peer-name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.pc-online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  flex-shrink: 0;
}

.pc-online-dot.online {
  background: #67c23a;
  box-shadow: 0 0 4px rgba(103, 194, 58, 0.5);
}

.pc-online-text {
  font-size: 12px;
  color: #909399;
  margin-right: 4px;
}

.pc-limit-hint {
  font-size: 12px;
  color: #e6a23c;
}

.pc-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pc-empty {
  text-align: center;
  color: #c0c4cc;
  margin-top: 100px;
}

.pc-msg {
  display: flex;
  max-width: 70%;
}

.pc-msg.mine {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.pc-msg-avatar {
  flex-shrink: 0;
  margin-right: 8px;
}

.pc-msg.mine .pc-msg-avatar {
  margin-right: 0;
  margin-left: 8px;
}

.pc-msg-bubble {
  background: #f0f0f0;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  word-break: break-all;
}

.pc-msg.mine .pc-msg-bubble {
  background: #409eff;
  color: #fff;
}

.pc-msg-image img {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
}

.pc-msg-share {
  cursor: pointer;
  min-width: 140px;
}

.pc-msg-share .share-type-tag {
  font-size: 12px;
  color: #409eff;
  margin-bottom: 4px;
}

.pc-msg-share .share-title {
  font-size: 14px;
  font-weight: 500;
}

.pc-msg.mine .pc-msg-share .share-type-tag {
  color: #a0d3ff;
}

.pc-blocked {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fef0f0;
  border-top: 1px solid #fde2e2;
  color: #f56c6c;
  font-size: 13px;
}

.pc-input {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  align-items: flex-end;
}

.pc-input .el-textarea {
  flex: 1;
}

.pc-voice-btn {
  flex-shrink: 0;
}

.pc-msg-voice {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pc-voice-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  padding: 0 4px;
}

.pc-msg-bubble-wrapper {
  position: relative;
}

.pc-msg-bubble.recalled {
  background: #f5f5f5 !important;
  color: #999 !important;
  font-style: italic;
}

.pc-recalled-text {
  font-size: 13px;
  color: #999;
}

.pc-msg-actions {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  gap: 4px;
  opacity: 0.85;
  z-index: 5;
}

.pc-msg-actions:not(.mine) {
  right: -56px;
}

.pc-msg-actions.mine {
  left: -56px;
}

.pc-action-btn {
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(255,255,255,0.9);
  border: 1px solid #e4e7ed;
  user-select: none;
}

.pc-action-btn:hover {
  color: #409eff;
  border-color: #409eff;
}

.pc-context-menu {
  position: fixed;
  z-index: 2000;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  overflow: hidden;
  min-width: 80px;
}

.pc-cm-item {
  padding: 8px 16px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
}

.pc-cm-item:hover {
  background: #ecf5ff;
  color: #409eff;
}

.pc-reply-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f0f2f5;
  border-radius: 6px;
  margin-bottom: 8px;
  font-size: 12px;
}

.pc-reply-label {
  color: #409eff;
  font-weight: 500;
  white-space: nowrap;
}

.pc-reply-preview {
  color: #909399;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pc-msg-quote {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  margin-bottom: 4px;
  background: rgba(0,0,0,0.05);
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  border-left: 3px solid #409eff;
}

.pc-msg.mine .pc-msg-quote {
  background: rgba(255,255,255,0.2);
  border-left-color: #a0d3ff;
}

.pc-quote-label {
  color: #409eff;
  font-weight: 500;
  white-space: nowrap;
}

.pc-msg.mine .pc-quote-label {
  color: #a0d3ff;
}

.pc-quote-name {
  color: #606266;
  font-weight: 500;
  white-space: nowrap;
}

.pc-msg.mine .pc-quote-name {
  color: rgba(255,255,255,0.8);
}

.pc-quote-text {
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pc-msg.mine .pc-quote-text {
  color: rgba(255,255,255,0.65);
}

.pc-msg-highlight {
  animation: msg-highlight 2s ease;
}

@keyframes msg-highlight {
  0%, 50% { background-color: rgba(64, 158, 255, 0.12); }
  100% { background-color: transparent; }
}

.pc-read-status {
  display: flex;
  justify-content: flex-end;
  margin-top: 2px;
  padding-right: 4px;
}

.pc-read-tag {
  font-size: 11px;
  color: #c0c4cc;
}

.pc-unread-tag {
  font-size: 11px;
  color: #909399;
  font-weight: 500;
}
</style>