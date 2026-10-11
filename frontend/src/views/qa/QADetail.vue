<template>
  <PageLayout>
    <template #main>
      <!-- 问题详情 -->
      <el-card class="detail-card" v-loading="loading">
        <div v-if="question">
          <div class="question-header">
            <h1>{{ question.title }}</h1>
            <el-tag v-if="question.bestAnswerId" type="success" effect="dark">已解决</el-tag>
          </div>
          <div class="question-meta">
            <span><el-icon><User /></el-icon> {{ question.nickname || question.username }}</span>
            <span><el-icon><Clock /></el-icon> {{ formatTime(question.createTime) }}</span>
            <span><el-icon><View /></el-icon> {{ question.viewCount }} 浏览</span>
          </div>
          <div class="question-content">{{ question.content }}</div>
          <div class="question-actions">
            <el-button @click="handleLikeQuestion" :loading="likeLoading" :class="{ 'liked-btn': question.isLiked }">
              <LikeIcon :active="question.isLiked" style="margin-right: 6px;" />
              <span>{{ question.isLiked ? '已点赞' : '点赞' }} ({{ question.likeCount }})</span>
            </el-button>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="问题不存在或已被删除" />
      </el-card>

      <!-- 回答列表 -->
      <el-card class="answer-card">
        <template #header>
          <div class="answer-header">
            <h3>回答 ({{ answerList.length }})</h3>
            <el-button type="primary" v-if="userStore.isLoggedIn" @click="showAnswerDialog = true">
              <el-icon><Plus /></el-icon> 写回答
            </el-button>
          </div>
        </template>
        <div v-loading="answerLoading">
          <div v-for="answer in answerList" :key="answer.id" class="answer-item" :class="{ 'best-answer': answer.isBest }">
            <div v-if="answer.isBest" class="best-badge">
              <el-icon><Trophy /></el-icon> 最佳答案
            </div>
            <div class="answer-header-info">
              <el-avatar :size="44" :src="answer.avatar" class="answer-avatar">
                {{ answer.nickname?.[0] }}
              </el-avatar>
              <div class="answer-user-info">
                <div class="answer-username">{{ answer.nickname || answer.username }}</div>
                <div class="answer-time">{{ formatTime(answer.createTime) }}</div>
              </div>
              <div class="answer-actions">
                <el-button text @click="handleLikeAnswer(answer)">
                  <LikeIcon :active="answer.isLiked" style="margin-right: 4px;" />
                  <span :style="{ color: answer.isLiked ? '#f56c6c' : '' }">{{ answer.likeCount }}</span>
                </el-button>
                <el-button
                  v-if="userStore.userInfo?.id === question?.userId && !answer.isBest"
                  type="success"
                  size="small"
                  @click="handleSetBestAnswer(answer.id)"
                >
                  设为最佳答案
                </el-button>
              </div>
            </div>
            <div class="answer-content">{{ answer.content }}</div>
          </div>
          <el-empty v-if="!answerLoading && answerList.length === 0" description="暂无回答，快来抢沙发~" />
        </div>
      </el-card>
    </template>
  </PageLayout>

  <!-- 回答对话框 -->
  <el-dialog v-model="showAnswerDialog" title="写回答" width="700px">
    <el-form :model="answerForm" :rules="answerRules" ref="answerFormRef" label-width="100px">
      <el-form-item label="回答内容" prop="content">
        <el-input v-model="answerForm.content" type="textarea" :rows="8" placeholder="请输入你的回答..." />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showAnswerDialog = false">取消</el-button>
      <el-button type="primary" @click="handlePublishAnswer" :loading="answering">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { User, Clock, View, Plus, Trophy } from '@element-plus/icons-vue'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import {
  getQuestionDetail, likeQuestion, unlikeQuestion,
  getAnswerList, publishAnswer, likeAnswer, unlikeAnswer, setBestAnswer
} from '@/api/qa'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const question = ref(null)
const answerList = ref([])
const answerLoading = ref(false)
const likeLoading = ref(false)
const showAnswerDialog = ref(false)
const answering = ref(false)
const answerFormRef = ref(null)

const answerForm = reactive({
  questionId: null,
  content: ''
})

const answerRules = {
  content: [{ required: true, message: '请输入回答内容', trigger: 'blur' }]
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getQuestionDetail(route.params.id)
    if (res.code === 200) {
      question.value = res.data
      answerForm.questionId = res.data.id
      loadAnswers()
    }
  } catch (error) {
    ElMessage.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

const loadAnswers = async () => {
  answerLoading.value = true
  try {
    const res = await getAnswerList(route.params.id, 1, 50)
    if (res.code === 200) {
      answerList.value = res.data.records || []
    }
  } catch (error) {
    ElMessage.error('加载回答列表失败')
  } finally {
    answerLoading.value = false
  }
}

const handleLikeQuestion = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  likeLoading.value = true
  try {
    if (question.value.isLiked) {
      await unlikeQuestion(route.params.id)
      question.value.isLiked = false
      question.value.likeCount--
    } else {
      await likeQuestion(route.params.id)
      question.value.isLiked = true
      question.value.likeCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    likeLoading.value = false
  }
}

const handleLikeAnswer = async (answer) => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  try {
    if (answer.isLiked) {
      await unlikeAnswer(answer.id)
      answer.isLiked = false
      answer.likeCount--
    } else {
      await likeAnswer(answer.id)
      answer.isLiked = true
      answer.likeCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handlePublishAnswer = async () => {
  if (!answerFormRef.value) return
  await answerFormRef.value.validate(async (valid) => {
    if (valid) {
      answering.value = true
      try {
        const res = await publishAnswer(answerForm)
        if (res.code === 200) {
          ElMessage.success('回答成功')
          showAnswerDialog.value = false
          answerForm.content = ''
          loadAnswers()
          if (question.value) question.value.answerCount++
        }
      } catch (error) {
        ElMessage.error('回答失败')
      } finally {
        answering.value = false
      }
    }
  })
}

const handleSetBestAnswer = async (answerId) => {
  try {
    const res = await setBestAnswer(answerId, route.params.id)
    if (res.code === 200) {
      ElMessage.success('已设为最佳答案')
      loadAnswers()
      if (question.value) question.value.bestAnswerId = answerId
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.detail-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  margin-bottom: 20px;
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.question-header h1 {
  margin: 0;
  font-size: 24px;
  color: #1a1a1a;
  line-height: 1.4;
  flex: 1;
}

.question-meta {
  display: flex;
  gap: 24px;
  color: #999;
  font-size: 14px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
}

.question-meta span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.question-content {
  margin: 20px 0;
  line-height: 1.9;
  font-size: 16px;
  color: #333;
  white-space: pre-wrap;
}

.question-actions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.liked-btn {
  border-color: #f56c6c !important;
}

/* 回答卡片 */
.answer-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.answer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.answer-header h3 {
  margin: 0;
  font-size: 18px;
}

.answer-item {
  padding: 20px;
  margin-bottom: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  position: relative;
  transition: box-shadow 0.2s;
}

.answer-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.answer-item.best-answer {
  border-color: #67c23a;
  background: #f6ffed;
}

.best-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: #67c23a;
  color: #fff;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.answer-header-info {
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
  align-items: center;
}

.answer-avatar {
  flex-shrink: 0;
}

.answer-user-info {
  flex: 1;
}

.answer-username {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  font-size: 15px;
}

.answer-time {
  font-size: 12px;
  color: #bbb;
}

.answer-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.answer-content {
  line-height: 1.8;
  color: #444;
  white-space: pre-wrap;
  font-size: 15px;
}
</style>