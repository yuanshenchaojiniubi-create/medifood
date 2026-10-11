<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <el-button @click="goBack" text>
              <el-icon><ArrowLeft /></el-icon> 返回
            </el-button>
            <el-button
              v-if="canClose"
              type="warning"
              @click="handleClose"
              :loading="closing"
            >
              关闭咨询
            </el-button>
          </div>
        </template>

        <div v-if="consult" class="consult-detail">
          <!-- 咨询信息 -->
          <div class="consult-header">
            <h1 class="consult-title">{{ consult.title }}</h1>
            <el-tag v-if="consult.status === 'PENDING'" type="warning">待回复</el-tag>
            <el-tag v-else-if="consult.status === 'REPLIED'" type="success">已回复</el-tag>
            <el-tag v-else-if="consult.status === 'CLOSED'" type="info">已关闭</el-tag>
          </div>

          <div class="consult-meta">
            <span>患者：{{ consult.patientName }}</span>
            <span>医师：{{ consult.doctorName }}</span>
            <span>{{ formatTime(consult.createTime) }}</span>
          </div>

          <el-divider />

          <!-- 咨询内容 -->
          <div class="consult-content">
            <div class="content-bubble patient-bubble">
              <div class="bubble-avatar">
                <el-avatar :size="36" :src="consult.patientAvatar" />
                <span class="bubble-name">{{ consult.patientName }}</span>
              </div>
              <div class="bubble-body">
                <div class="bubble-text">{{ consult.content }}</div>
                <div class="bubble-time">{{ formatTime(consult.createTime) }}</div>
              </div>
            </div>
          </div>

          <el-divider />

          <!-- 回复列表 -->
          <div class="replies-section" v-if="consult.replies && consult.replies.length > 0">
            <h3>回复记录（{{ consult.replyCount }}条）</h3>
            <div
              v-for="reply in consult.replies"
              :key="reply.id"
              class="content-bubble"
              :class="reply.isDoctorReply ? 'doctor-bubble' : 'patient-bubble'"
            >
              <div class="bubble-avatar">
                <el-avatar :size="36" :src="reply.userAvatar" />
                <span class="bubble-name">
                  {{ reply.userName }}
                  <el-tag v-if="reply.isDoctorReply" type="success" size="small" effect="plain">医师</el-tag>
                </span>
              </div>
              <div class="bubble-body">
                <div class="bubble-text">{{ reply.content }}</div>
                <div class="bubble-time">{{ formatTime(reply.createTime) }}</div>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂无回复" />

          <el-divider />

          <!-- 回复输入框 -->
          <div class="reply-box" v-if="consult.status !== 'CLOSED'">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="3"
              placeholder="输入回复内容..."
              maxlength="1000"
              show-word-limit
            />
            <div class="reply-actions">
              <el-button type="primary" @click="handleReply" :loading="replying" :disabled="!replyContent.trim()">
                <el-icon><Promotion /></el-icon> 发送回复
              </el-button>
            </div>
          </div>
          <el-alert v-else title="该咨询已关闭，无法回复" type="info" show-icon :closable="false" />
        </div>

        <el-empty v-else description="咨询不存在" />
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { getConsultationDetail, replyConsultation, closeConsultation } from '@/api/doctor'
import { ArrowLeft, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const replying = ref(false)
const closing = ref(false)
const consult = ref(null)
const replyContent = ref('')

const canClose = computed(() => {
  if (!consult.value || consult.value.status === 'CLOSED') return false
  const uid = userStore.userInfo?.id
  return uid === consult.value.doctorId || uid === consult.value.patientId
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getConsultationDetail(route.params.id)
    if (res.code === 200) {
      consult.value = res.data
    } else {
      ElMessage.error('咨询不存在')
    }
  } catch (error) {
    ElMessage.error('加载咨询详情失败')
  } finally {
    loading.value = false
  }
}

const handleReply = async () => {
  if (!replyContent.value.trim()) return
  replying.value = true
  try {
    const res = await replyConsultation(route.params.id, { content: replyContent.value.trim() })
    if (res.code === 200) {
      ElMessage.success('回复成功')
      replyContent.value = ''
      loadData()
    } else {
      ElMessage.error(res.message || '回复失败')
    }
  } catch (error) {
    ElMessage.error('回复失败')
  } finally {
    replying.value = false
  }
}

const handleClose = async () => {
  try {
    await ElMessageBox.confirm('确定关闭该咨询吗？关闭后无法再回复。', '确认关闭', { type: 'warning' })
    closing.value = true
    const res = await closeConsultation(route.params.id)
    if (res.code === 200) {
      ElMessage.success('咨询已关闭')
      loadData()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('关闭失败')
    }
  } finally {
    closing.value = false
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/doctor/consultations')
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.consult-detail {
  max-width: 800px;
}

.consult-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.consult-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  color: #1a1a1a;
}

.consult-meta {
  display: flex;
  gap: 20px;
  color: #909399;
  font-size: 14px;
}

.consult-content {
  margin: 16px 0;
}

.content-bubble {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px;
  border-radius: 12px;
}

.patient-bubble {
  background: #f0f9ff;
}

.doctor-bubble {
  background: #f0fdf4;
  flex-direction: row-reverse;
}

.bubble-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.bubble-name {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.bubble-body {
  flex: 1;
}

.bubble-text {
  font-size: 15px;
  line-height: 1.7;
  color: #333;
  white-space: pre-wrap;
}

.bubble-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 8px;
}

.replies-section h3 {
  font-size: 16px;
  margin: 0 0 16px 0;
  color: #333;
}

.reply-box {
  margin-top: 16px;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>