<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <div class="smart-qa-container">
        <div class="chat-layout">
      <div class="sidebar">
        <div class="sidebar-header">
          <div class="title">历史对话</div>
          <el-button type="primary" size="small" @click="newConversation">新建对话</el-button>
        </div>
        <div class="conversation-list">
          <div 
            v-for="conv in conversations" 
            :key="conv.id" 
            :class="['conversation-item', conv.id === activeConversationId ? 'active' : '']"
            @click="selectConversation(conv.id)"
          >
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-time">{{ formatTime(conv.updatedAt || conv.lastMessageAt) }}</div>
          </div>
        </div>
        <div class="sidebar-actions" v-if="activeConversationId">
          <el-button size="small" @click="renameSelected">重命名</el-button>
          <el-button size="small" type="danger" @click="deleteSelected">删除</el-button>
        </div>
      </div>
      <div class="chat-panel">
        <div class="chat-messages" ref="messageBox">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-icon">🍵</div>
          <h3>我是您的药食同源助手</h3>
          <p>您可以问我关于脾胃调理、药膳食谱或养生常识的问题</p>
          <div class="suggested-queries">
            <el-tag 
              v-for="q in suggestedQueries" 
              :key="q" 
              @click="handleSuggestedQuery(q)"
              class="query-tag"
            >{{ q }}</el-tag>
          </div>
        </div>
        
        <div 
          v-for="(msg, index) in messages" 
          :key="index" 
          :class="['message-item', msg.role === 'user' ? 'user-msg' : 'ai-msg']"
        >
          <el-avatar :size="40" :src="msg.role === 'user' ? userAvatar : aiAvatar" class="avatar" />
          <div class="message-content">
            <div class="sender-name">{{ msg.role === 'user' ? '您' : '药食助手' }}</div>
            <div class="bubble">
              <img v-if="msg.image" :src="msg.image" class="uploaded-image" alt="上传图片" />
              <div v-if="msg.analyzing" class="analyzing-state">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>AI 正在分析中...</span>
              </div>
              <div v-else-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
              <div v-else>{{ msg.content }}</div>
            </div>
            <!-- 反馈按钮：仅 AI 消息显示 -->
            <div v-if="msg.role === 'assistant' && !msg.analyzing" class="feedback-buttons">
              <el-button 
                :type="msg.feedbackType === 'positive' ? 'success' : 'default'"
                size="small" 
                circle 
                plain
                @click="submitFeedback(msg, 'positive')"
                :disabled="msg.feedbackSubmitted"
                title="有帮助"
              >👍</el-button>
              <el-button 
                :type="msg.feedbackType === 'negative' ? 'danger' : 'default'"
                size="small" 
                circle 
                plain
                @click="submitFeedback(msg, 'negative')"
                :disabled="msg.feedbackSubmitted"
                title="没帮助"
              >👎</el-button>
            </div>
          </div>
        </div>
        
        <div v-if="isStreaming && currentAiMessage === ''" class="message-item ai-msg">
          <el-avatar :size="40" :src="aiAvatar" class="avatar" />
          <div class="message-content">
            <div class="sender-name">药食助手</div>
            <div class="bubble typing">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
        </div>
        <div class="input-area">
          <div class="input-container">
            <el-input
              v-model="query"
              type="textarea"
              :rows="2"
              placeholder="请输入您的问题..."
              resize="none"
              @keydown.enter.prevent="handleEnter"
              :disabled="isStreaming"
            />
            <div class="input-footer">
              <div class="toolbar-left">
                <!-- 图片粘贴/上传 -->
                <el-tooltip content="上传图片提问（支持粘贴）" placement="top">
                  <el-button circle size="small" @click="pickChatImage" :disabled="isStreaming || analyzing">
                    <el-icon><Picture /></el-icon>
                  </el-button>
                </el-tooltip>
                <!-- 语音输入 -->
                <el-tooltip v-if="speechSupported" :content="isRecording ? '点击停止录音' : '语音输入'" placement="top">
                  <el-button 
                    circle 
                    size="small" 
                    @click="toggleVoiceInput" 
                    :type="isRecording ? 'danger' : 'default'"
                    :disabled="isStreaming || analyzing"
                    :class="{ 'is-recording': isRecording }"
                  >
                    <el-icon><Microphone /></el-icon>
                  </el-button>
                </el-tooltip>
                <!-- 录音状态提示 -->
                <span v-if="isRecording" class="recording-indicator">
                  <span class="recording-dot"></span>
                  正在录音...
                </span>
                <el-tooltip content="上传体检报告（PDF/Word/图片）" placement="top">
                  <el-button circle size="small" @click="pickReportFile" :disabled="analyzing || foodAnalysisLoading">
                    <el-icon><Upload /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-dropdown trigger="click" @command="pickFoodImage" :disabled="analyzing || foodAnalysisLoading">
                  <el-button circle size="small">
                    <el-icon><Camera /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="ingredient">
                        <el-icon><Camera /></el-icon> 拍照识食材
                      </el-dropdown-item>
                      <el-dropdown-item command="dish">
                        <el-icon><Camera /></el-icon> 拍照识菜品
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <div class="actions">
                <el-tooltip :content="agentMode ? '智能模式：AI 会自动调用工具查找信息' : '普通模式：直接问答'" placement="top">
                  <el-button 
                    :type="agentMode ? 'warning' : 'default'" 
                    plain
                    size="small"
                    @click="agentMode = !agentMode"
                    :disabled="isStreaming"
                  >
                    {{ agentMode ? '智能' : '普通' }}
                  </el-button>
                </el-tooltip>
                <el-button 
                  v-if="isStreaming" 
                  type="danger" 
                  plain 
                  @click="stopStream"
                >停止生成</el-button>
                <el-button 
                  type="primary" 
                  :disabled="!query.trim() || isStreaming || analyzing || foodAnalysisLoading" 
                  @click="sendMessage"
                >
                  发送
                  <el-icon class="el-icon--right"><Promotion /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
          <input
            type="file"
            ref="reportFileInput"
            style="display:none"
            accept=".pdf,.docx,.txt,.png,.jpg,.jpeg,.webp"
            @change="handleReportUpload"
          />
          <input
            type="file"
            ref="foodImageInput"
            style="display:none"
            accept="image/*"
            capture="environment"
            @change="handleFoodImageUpload"
          />
          <input
            type="file"
            ref="chatImageInput"
            style="display:none"
            accept="image/*"
            @change="handleChatImageUpload"
          />
        </div>
      </div>
    </div>
      </div>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import PageLayout from '@/components/PageLayout.vue'
import { Promotion, Camera, Upload, Loading, Picture, Microphone } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { useUserStore } from '@/store/user'
import request from '@/api/request'
import { listConversations, createConversation, renameConversation, deleteConversation, getMessages, appendMessage, analyzeFoodImage, analyzeReport } from '@/api/ai'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// Markdown 配置
const md = new MarkdownIt({
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang }).value;
      } catch (__) {}
    }
    return ''; 
  }
})

const renderMarkdown = (content) => {
  // XSS 防护：markdown-it 可能直通内嵌 HTML，渲染结果必须消毒后再交给 v-html
  return DOMPurify.sanitize(md.render(content || ''))
}

const userStore = useUserStore()
const query = ref('')
const messages = ref([])
const isStreaming = ref(false)
const agentMode = ref(false)           // Agent 智能模式开关
const userLocation = ref({ lat: null, lng: null })  // 用户地理位置
const locationTried = ref(false)       // 是否已尝试获取过位置（避免重复弹授权）
const currentAiMessage = ref('')
const messageBox = ref(null)
let abortController = null
const conversations = ref([])
const activeConversationId = ref(null)

const analyzing = ref(false)
const foodAnalysisLoading = ref(false)
const reportFileInput = ref(null)
const foodImageInput = ref(null)
const chatImageInput = ref(null)

// 语音输入
const speechSupported = ref(false)
const isRecording = ref(false)
let recognition = null

// 图片粘贴
const pasteImage = ref(null)

const userAvatar = userStore.userInfo?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const aiAvatar = 'https://api.dicebear.com/7.x/bottts/svg?seed=medifood' 

const suggestedQueries = [
  '春季如何养肝？',
  '脾胃虚寒吃什么食谱？',
  '推荐一款祛湿茶',
  '黄连有哪些功效？'
]

const DEFAULT_CONVERSATION_TITLE = '新会话'

const formatTime = (ts) => {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${hh}:${mm}`
  } catch {
    return ''
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageBox.value) {
    messageBox.value.scrollTop = messageBox.value.scrollHeight
  }
}

const handleEnter = (e) => {
  if (e.shiftKey) return
  sendMessage()
}

const handleSuggestedQuery = (q) => {
  query.value = q
  sendMessage()
}

const loadConversations = async () => {
  try {
    const res = await listConversations()
    conversations.value = res.data || []
    if (!activeConversationId.value && conversations.value.length > 0) {
      activeConversationId.value = conversations.value[0].id
      await loadMessages(activeConversationId.value)
    }
  } catch {}
}

const selectConversation = async (id) => {
  activeConversationId.value = id
  await loadMessages(id)
}

const loadMessages = async (id) => {
  try {
    const res = await getMessages(id)
    messages.value = (res.data || []).map(m => ({ role: m.role, content: m.content }))
    await scrollToBottom()
  } catch {}
}

const deriveConversationTitle = (text) => {
  const normalized = (text || '').replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return DEFAULT_CONVERSATION_TITLE
  }
  return normalized.slice(0, 20)
}

const updateConversationTitleLocally = (id, text) => {
  const idx = conversations.value.findIndex(c => c.id === id)
  if (idx < 0) return
  const currentTitle = (conversations.value[idx].title || '').trim()
  if (!currentTitle || currentTitle === DEFAULT_CONVERSATION_TITLE) {
    conversations.value[idx].title = deriveConversationTitle(text)
  }
}

const newConversation = async () => {
  const t = query.value.trim()
  const title = t ? deriveConversationTitle(t) : DEFAULT_CONVERSATION_TITLE
  try {
    const res = await createConversation(title)
    const conv = res.data
    activeConversationId.value = conv.id
    conversations.value.unshift(conv)
    messages.value = []
  } catch {}
}

const renameSelected = async () => {
  if (!activeConversationId.value) return
  const name = window.prompt('请输入新的标题')
  if (!name) return
  try {
    await renameConversation(activeConversationId.value, name)
    const idx = conversations.value.findIndex(c => c.id === activeConversationId.value)
    if (idx >= 0) {
      conversations.value[idx].title = name
    }
  } catch {}
}

const deleteSelected = async () => {
  if (!activeConversationId.value) return
  try {
    await deleteConversation(activeConversationId.value)
    const idx = conversations.value.findIndex(c => c.id === activeConversationId.value)
    if (idx >= 0) conversations.value.splice(idx, 1)
    activeConversationId.value = conversations.value[0]?.id || null
    messages.value = []
    if (activeConversationId.value) await loadMessages(activeConversationId.value)
  } catch {}
}

const sendMessage = async () => {
  const text = query.value.trim()
  if (!text || isStreaming.value) return

  // 获取最近的历史记录（排除最后一条占位符）
  const history = messages.value.slice(-6).map(m => ({ role: m.role, content: m.content }))
  
  // 添加用户消息
  messages.value.push({ role: 'user', content: text })
  if (!activeConversationId.value) {
    const title = deriveConversationTitle(text)
    try {
      const res = await createConversation(title)
      const conv = res.data
      activeConversationId.value = conv.id
      conversations.value.unshift(conv)
    } catch {}
  }
  try {
    if (activeConversationId.value) {
      await appendMessage(activeConversationId.value, 'user', text)
      updateConversationTitleLocally(activeConversationId.value, text)
    }
  } catch {}
  
  query.value = ''
  isStreaming.value = true
  currentAiMessage.value = ''
  
  // 添加占位符 AI 消息
  const aiMsgIndex = messages.value.push({ role: 'assistant', content: '' }) - 1
  
  scrollToBottom()
  
  abortController = new AbortController()
  
  try {
    // Agent 智能模式：先获取用户位置（只在第一次尝试，避免重复弹授权）
    if (agentMode.value && !locationTried.value) {
      await getUserLocationAsync()
    }

    const baseURL = request.defaults.baseURL || ''
    // Agent 模式使用 /ai/agent/reactStream，普通模式使用 /ai/ragStream
    const apiPath = agentMode.value ? '/ai/agent/reactStream' : '/ai/ragStream'
    const response = await fetch(`${baseURL}${apiPath}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userStore.token}`
      },
      body: JSON.stringify({
        message: text,
        history: history,
        conversationId: activeConversationId.value,  // 多轮对话上下文
        userId: userStore.userInfo?.id,              // 用户画像个性化
        lat: userLocation.value.lat,                 // 用户纬度（天气查询）
        lng: userLocation.value.lng                  // 用户经度（天气查询）
      }),
      signal: abortController.signal
    })

    if (!response.ok) throw new Error('网络响应异常')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 留存最后一条可能不完整的行
      
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine || !trimmedLine.startsWith('data:')) continue
        
        try {
          const jsonStr = trimmedLine.substring(5).trim()
          if (jsonStr === '[DONE]') break
          
          const data = JSON.parse(jsonStr)
          // 兼容多种返回格式 (Spring AI 不同版本或不同模型可能略有差异)
          const content = data.result?.output?.content 
            || data.result?.output?.text 
            || data.content 
            || data.text 
            || ''
            
          if (content) {
            messages.value[aiMsgIndex].content += content
            currentAiMessage.value += content // 更新这个值以消除 typing 动画
            scrollToBottom()
          }
        } catch (e) {
          console.warn('SSE JSON 解析失败:', e, trimmedLine)
        }
      }
    }
    const finalText = messages.value[aiMsgIndex].content
    if (finalText && activeConversationId.value) {
      try {
        // 估算 Token 用量（中文约 1.5 字符/token，英文约 4 字符/token）
        const estimatedTokens = Math.ceil(finalText.length / 2)
        const appendRes = await appendMessage(activeConversationId.value, 'assistant', finalText, estimatedTokens)
        // 捕获后端返回的真实 messageId，用于后续反馈关联
        if (appendRes?.data?.messageId) {
          messages.value[aiMsgIndex].messageId = appendRes.data.messageId
        }
        const idx = conversations.value.findIndex(c => c.id === activeConversationId.value)
        if (idx >= 0) {
          const now = Date.now()
          conversations.value[idx].updatedAt = now
          const conv = conversations.value.splice(idx, 1)[0]
          conversations.value.unshift(conv)
        }
      } catch {}
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      console.log('用户停止了生成')
    } else {
      ElMessage.error('问答服务暂时不可用，请稍后再试')
      messages.value[aiMsgIndex].content = '抱歉，系统遇到了一点小麻烦，请稍后重新提问。'
    }
  } finally {
    isStreaming.value = false
    abortController = null
  }
}

const stopStream = () => {
  if (abortController) {
    abortController.abort()
  }
}

const pickReportFile = () => {
  reportFileInput.value?.click()
}

const handleReportUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  const allowed = ['.pdf', '.docx', '.txt', '.png', '.jpg', '.jpeg', '.webp']
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!allowed.includes(ext)) {
    ElMessage.warning('仅支持 PDF、Word、TXT、图片格式')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 20MB')
    return
  }

  messages.value.push({ role: 'user', content: '📎 上传了体检报告：' + file.name })
  await scrollToBottom()

  analyzing.value = true
  const loadingIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', analyzing: true })

  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await analyzeReport(formData)
    const data = res.data
    if (data.success) {
      let display = '### 📋 体检报告分析\n\n'
      if (data.reportPreview) {
        display += '> 报告预览：' + data.reportPreview + '\n\n'
      }
      display += (data.analysis || '分析完成，暂无详细内容')
      if (data.relatedKnowledge?.length) {
        display += '\n\n---\n**📚 相关知识推荐：**\n'
        data.relatedKnowledge.forEach(k => {
          display += '- [《' + k.title + '》](id:' + k.id + ')\n'
        })
      }
      messages.value[loadingIdx] = { role: 'assistant', content: display }
    } else {
      messages.value[loadingIdx] = { role: 'assistant', content: '❌ ' + (data.error || '分析失败，请稍后重试') }
    }
  } catch (err) {
    messages.value[loadingIdx] = { role: 'assistant', content: '❌ 报告分析服务暂不可用，请稍后重试' }
  } finally {
    analyzing.value = false
    e.target.value = ''
    await scrollToBottom()
  }
}

const pickFoodImage = (mode) => {
  if (foodImageInput.value) {
    foodImageInput.value._mode = mode
    foodImageInput.value.click()
  }
}

const handleFoodImageUpload = async (e) => {
  const file = e.target.files?.[0]
  const mode = e.target._mode || (foodImageInput.value && foodImageInput.value._mode) || 'ingredient'
  if (foodImageInput.value) foodImageInput.value._mode = null
  if (!file) return

  const allowed = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.warning('仅支持 JPG/PNG/WebP 图片格式')
    return
  }

  const label = mode === 'ingredient' ? '食材识别' : '菜品分析'
  messages.value.push({ role: 'user', content: '📷 ' + label + '：已上传图片' })

  const imgUrl = URL.createObjectURL(file)
  messages.value.push({ role: 'user', content: '', image: imgUrl })
  await scrollToBottom()

  foodAnalysisLoading.value = true
  const loadingIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', analyzing: true })

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('mode', mode)
    const res = await analyzeFoodImage(formData)
    const data = res.data
    if (data.identified) {
      let display = ''
      if (mode === 'ingredient') {
        display = '### 🥬 食材识别结果\n\n'
        if (data.ingredients?.length) display += '**识别食材**：' + data.ingredients.join('、') + '\n\n'
        if (data.category) display += '**类别**：' + data.category + '\n\n'
        if (data.description) display += '**简介**：' + data.description + '\n\n'
        if (data.healthBenefits?.length) display += '**功效**：\n' + data.healthBenefits.map(h => '• ' + h).join('\n') + '\n\n'
        if (data.suitableFor?.length) display += '**适合人群**：' + data.suitableFor.join('、') + '\n\n'
        if (data.contraindications?.length) display += '**⚠️ 注意事项**：' + data.contraindications.join('、') + '\n\n'
      } else {
        display = '### 🍽️ 菜品分析结果\n\n'
        if (data.dishName) display += '**菜品名称**：' + data.dishName + '\n\n'
        if (data.cuisineType) display += '**类型**：' + data.cuisineType + '\n\n'
        if (data.description) display += '**简介**：' + data.description + '\n\n'
        if (data.mainIngredients?.length) display += '**主要食材**：' + data.mainIngredients.join('、') + '\n\n'
        if (data.healthBenefits?.length) display += '**功效**：\n' + data.healthBenefits.map(h => '• ' + h).join('\n') + '\n\n'
        if (data.cookingMethod) display += '**制作方法**：' + data.cookingMethod + '\n\n'
      }
      if (data.relatedRecipes?.length) {
        display += '\n---\n**📖 相关食谱推荐：**\n'
        data.relatedRecipes.forEach(r => {
          display += '- [《' + r.title + '》](id:' + r.id + ') — ' + (r.effects || '') + '\n'
        })
      }
      messages.value[loadingIdx] = { role: 'assistant', content: display }
    } else {
      messages.value[loadingIdx] = { role: 'assistant', content: '😕 抱歉，未能识别图片中的' + (mode === 'ingredient' ? '食材' : '菜品') + '，请尝试更换一张清晰的照片' }
    }
  } catch (err) {
    messages.value[loadingIdx] = { role: 'assistant', content: '❌ 识别服务暂不可用，请稍后重试' }
  } finally {
    foodAnalysisLoading.value = false
    e.target.value = ''
    await scrollToBottom()
  }
}

// ==================== 用户地理位置 ====================

/**
 * 获取用户地理位置（仅在 Agent 模式需要天气条件时调用）
 * 返回 Promise，成功时返回 {lat, lng}，失败时返回 null（后端自动降级为 IP 定位）
 */
const getUserLocationAsync = () => {
  return new Promise((resolve) => {
    locationTried.value = true

    if (!navigator.geolocation) {
      console.log('浏览器不支持 Geolocation API，使用 IP 定位降级')
      resolve(null)
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const loc = {
          lat: position.coords.latitude,
          lng: position.coords.longitude
        }
        userLocation.value = loc
        console.log('📍 已获取用户位置:', loc)
        resolve(loc)
      },
      (error) => {
        console.log('用户拒绝位置授权，降级为 IP 定位:', error.message)
        resolve(null)
      },
      {
        enableHighAccuracy: true,
        timeout: 8000,
        maximumAge: 600000  // 10分钟内缓存
      }
    )
  })
}

onMounted(() => {
  loadConversations()
  initSpeechRecognition()
  // 注意：不在此处获取位置，只在 Agent 模式需要天气工具时才弹授权
  // 监听图片粘贴事件
  document.addEventListener('paste', handleImagePaste)
})

onBeforeUnmount(() => {
  // 移除粘贴事件监听
  document.removeEventListener('paste', handleImagePaste)
  // 停止语音识别
  stopSpeechRecognition()
})

// ==================== 阶段 7：用户反馈 ====================

const submitFeedback = async (msg, type) => {
  if (msg.feedbackSubmitted) return
  // 必须有真实的 messageId（数据库 ID）才能提交反馈
  const messageId = msg.messageId
  if (!messageId) {
    console.warn('反馈提交失败：消息缺少 messageId')
    ElMessage.warning('反馈功能暂不可用，请稍后重试')
    return
  }
  try {
    const res = await request({
      method: 'post',
      url: '/api/ai/feedback/' + messageId,
      data: { type }
    })
    if (res.data?.success !== false) {
      msg.feedbackType = type
      msg.feedbackSubmitted = true
      ElMessage.success(type === 'positive' ? '感谢您的反馈！' : '感谢您的反馈，我们会继续改进')
    } else {
      ElMessage.info(res.data?.message || '反馈已记录')
      msg.feedbackType = type
      msg.feedbackSubmitted = true
    }
  } catch (e) {
    ElMessage.warning('反馈提交失败，请稍后重试')
  }
}

// ==================== 阶段 8：图片内联聊天 ====================

const pickChatImage = () => {
  chatImageInput.value?.click()
}

const handleChatImageUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    analyzing.value = true
    const formData = new FormData()
    formData.append('file', file)
    formData.append('message', query.value || '请分析这张图片中的食材')
    formData.append('mode', 'ingredient')
    // 传递当前对话 ID，将图片分析结果关联到对话中
    if (activeConversationId.value) {
      formData.append('conversationId', activeConversationId.value)
    }

    // 添加用户消息（带图片预览）
    const reader = new FileReader()
    const imageUrl = await new Promise((resolve) => {
      reader.onload = () => resolve(reader.result)
      reader.readAsDataURL(file)
    })
    messages.value.push({ role: 'user', content: query.value || '请分析这张图片', image: imageUrl, analyzing: true })
    const userMsgIndex = messages.value.length - 1
    await scrollToBottom()

    // 注意：不设置 Content-Type，让浏览器自动设置 multipart/form-data 的 boundary
    const res = await request({
      method: 'post',
      url: '/ai/chat-with-image',
      data: formData
    })

    if (res.data?.success) {
      const answer = res.data.answer || '分析完成'
      messages.value[userMsgIndex].analyzing = false
      messages.value[userMsgIndex].content = query.value || '请分析这张图片'
      // 添加 AI 回复消息，带上 messageId 用于反馈
      messages.value.push({ 
        role: 'assistant', 
        content: answer, 
        messageId: res.data.messageId,
        index: messages.value.length 
      })
      // 如果有关联对话，保存消息
      if (activeConversationId.value) {
        try {
          await appendMessage(activeConversationId.value, 'assistant', answer)
        } catch {}
      }
    } else {
      messages.value[userMsgIndex].analyzing = false
      ElMessage.error(res.data?.error || '图片分析失败')
    }
  } catch (e) {
    ElMessage.error('图片分析失败，请稍后重试')
  } finally {
    analyzing.value = false
    query.value = ''
    if (chatImageInput.value) chatImageInput.value.value = ''
    await scrollToBottom()
  }
}

const handleImagePaste = async (e) => {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const file = item.getAsFile()
      if (file) {
        pasteImage.value = file
        // 在聊天区域任何位置粘贴图片都触发分析
        const activeEl = document.activeElement
        const isInChatArea = activeEl && (
          activeEl.closest('.chat-panel') || 
          activeEl.closest('.smart-qa-container') ||
          activeEl.closest('.input-area')
        )
        if (isInChatArea) {
          const fakeEvent = { target: { files: [file] } }
          await handleChatImageUpload(fakeEvent)
        }
      }
      break
    }
  }
}

// ==================== 阶段 8：语音输入 ====================

const initSpeechRecognition = () => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (SpeechRecognition) {
    speechSupported.value = true
    recognition = new SpeechRecognition()
    recognition.lang = 'zh-CN'
    recognition.interimResults = true
    recognition.continuous = false

    recognition.onresult = (event) => {
      let transcript = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        transcript += event.results[i][0].transcript
      }
      query.value = transcript
    }

    recognition.onerror = (event) => {
      console.error('语音识别错误:', event.error)
      isRecording.value = false
      if (event.error === 'not-allowed') {
        ElMessage.warning('请允许麦克风权限以使用语音输入')
      }
    }

    recognition.onend = () => {
      isRecording.value = false
    }
  } else {
    speechSupported.value = false
  }
}

const toggleVoiceInput = () => {
  if (!recognition) return
  if (isRecording.value) {
    stopSpeechRecognition()
  } else {
    startSpeechRecognition()
  }
}

const startSpeechRecognition = () => {
  if (!recognition) return
  try {
    recognition.start()
    isRecording.value = true
  } catch (e) {
    console.error('启动语音识别失败:', e)
  }
}

const stopSpeechRecognition = () => {
  if (recognition) {
    try {
      recognition.stop()
    } catch (e) {
      // ignore
    }
  }
  isRecording.value = false
}
</script>

<style scoped lang="scss">
.smart-qa-container {
  height: calc(100vh - 96px);
  display: flex;
  justify-content: center;
  padding: 0;
}

.chat-layout {
  width: 100%;
  max-width: 900px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: row;
  overflow: hidden;
}

.sidebar {
  width: 280px;
  border-right: 1px solid #ebeef5;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-item {
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  cursor: pointer;
}

.conversation-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.conv-title {
  font-size: 14px;
  color: #303133;
}

.conv-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.sidebar-actions {
  display: flex;
  gap: 8px;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #e0e0e0;
    border-radius: 3px;
  }
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
  text-align: center;
  
  .empty-icon {
    font-size: 64px;
    margin-bottom: 16px;
  }
  
  h3 {
    color: #303133;
    margin-bottom: 8px;
  }

  .suggested-queries {
    margin-top: 24px;
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;

    .query-tag {
      cursor: pointer;
      transition: all 0.3s;
      padding: 8px 16px;
      &:hover {
        transform: translateY(-2px);
        background-color: #ecf5ff;
        border-color: #409eff;
      }
    }
  }
}

.message-item {
  display: flex;
  margin-bottom: 24px;
  
  .avatar {
    flex-shrink: 0;
  }

  .message-content {
    margin-left: 12px;
    max-width: 80%;

    .sender-name {
      font-size: 12px;
      color: #909399;
      margin-bottom: 4px;
    }

    .bubble {
      padding: 12px 16px;
      border-radius: 12px;
      font-size: 14px;
      line-height: 1.6;
      word-break: break-word;
    }
  }

  &.user-msg {
    flex-direction: row-reverse;
    .message-content {
      margin-left: 0;
      margin-right: 12px;
      text-align: right;
      .bubble {
        background-color: #409eff;
        color: white;
        border-bottom-right-radius: 2px;
        text-align: left;
      }
    }
  }

  &.ai-msg {
    .bubble {
      background-color: #f4f4f5;
      color: #303133;
      border-bottom-left-radius: 2px;
    }
  }
}

.input-area {
  padding: 20px 24px;
  border-top: 1px solid #ebeef5;
  background-color: white;

  .input-container {
    border: 1px solid #dcdfe6;
    border-radius: 8px;
    padding: 8px;
    transition: border-color 0.2s;

    &:focus-within {
      border-color: #409eff;
    }

    :deep(.el-textarea__inner) {
      border: none;
      box-shadow: none;
      padding: 8px;
    }
  }

  .input-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 8px;
    padding: 0 8px;

    .toolbar-left {
      display: flex;
      gap: 8px;
    }

    .tip {
      font-size: 12px;
      color: #c0c4cc;
    }
  }
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 20px !important;

  .dot {
    width: 6px;
    height: 6px;
    background: #909399;
    border-radius: 50%;
    animation: typing 1.4s infinite;
    
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes typing {
  0%, 100% { transform: translateY(0); opacity: 0.3; }
  50% { transform: translateY(-4px); opacity: 1; }
}

.uploaded-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  object-fit: cover;
  display: block;
}

.analyzing-state {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 14px;
  padding: 8px 0;

  .el-icon {
    font-size: 18px;
  }
}

.markdown-body {
  :deep(p) { margin: 0 0 8px 0; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 8px 0; }
  :deep(code) { background: #e0e0e0; padding: 2px 4px; border-radius: 4px; font-family: monospace; }
  :deep(pre) { background: #f6f8fa; padding: 12px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
}

// 反馈按钮（阶段 7）
.feedback-buttons {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  padding-left: 4px;
  opacity: 0;
  transition: opacity 0.2s;

  .message-item:hover & {
    opacity: 1;
  }
}

// 语音录音按钮动画（阶段 8）
.is-recording {
  animation: pulse 1.5s infinite;
}

// 录音状态指示器
.recording-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #f56c6c;
  font-weight: 500;
  padding: 0 8px;
  
  .recording-dot {
    width: 8px;
    height: 8px;
    background: #f56c6c;
    border-radius: 50%;
    animation: blink 1s infinite;
  }
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 34, 45, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(245, 34, 45, 0); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
