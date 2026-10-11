<template>
  <PageLayout>
    <template #main>
      <el-card class="qa-card">
        <template #header>
          <div class="page-header">
            <h2>问答交流</h2>
            <el-button type="primary" v-if="userStore.isLoggedIn" @click="showQuestionDialog = true">
              <el-icon><Plus /></el-icon> 提问
            </el-button>
          </div>
        </template>

        <!-- 搜索和筛选 -->
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索问题..."
            style="width: 300px; margin-right: 10px;"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-select v-model="queryParams.categoryId" placeholder="选择分类" clearable style="width: 150px; margin-left: 10px;">
            <el-option label="全部" value="" />
            <el-option label="食疗咨询" :value="1" />
            <el-option label="药膳搭配" :value="2" />
            <el-option label="疾病咨询" :value="3" />
          </el-select>
        </div>

        <!-- 问题列表 -->
        <div v-loading="loading" class="question-list">
          <el-card
            v-for="question in questionList"
            :key="question.id"
            shadow="hover"
            class="question-item"
            @click="$router.push(`/qa/${question.id}`)"
          >
            <div class="question-header">
              <div class="question-title">{{ question.title }}</div>
              <el-tag v-if="question.bestAnswerId" type="success" size="small">已解决</el-tag>
            </div>
            <div class="question-content">{{ question.content?.substring(0, 200) }}...</div>
            <div class="question-footer">
              <div class="question-meta">
                <span><el-icon><User /></el-icon> {{ question.nickname || question.username }}</span>
                <span><el-icon><View /></el-icon> {{ question.viewCount }}</span>
                <span><LikeIcon :active="question.isLiked" /> {{ question.likeCount }}</span>
                <span><el-icon><ChatDotRound /></el-icon> {{ question.answerCount }} 回答</span>
              </div>
              <div class="question-time">{{ formatTime(question.createTime) }}</div>
            </div>
          </el-card>
          <el-empty v-if="!loading && questionList.length === 0" description="暂无问题" />
        </div>

        <!-- 分页 -->
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
          style="margin-top: 20px; justify-content: center;"
        />
      </el-card>
    </template>
  </PageLayout>

    <!-- 提问对话框 -->
    <el-dialog v-model="showQuestionDialog" title="提问" width="700px">
      <el-form :model="questionForm" :rules="questionRules" ref="questionFormRef" label-width="100px">
        <el-form-item label="问题标题" prop="title">
          <el-input v-model="questionForm.title" placeholder="请输入问题标题" />
        </el-form-item>
        <el-form-item label="问题分类" prop="categoryId">
          <el-select v-model="questionForm.categoryId" placeholder="请选择分类">
            <el-option label="食疗咨询" :value="1" />
            <el-option label="药膳搭配" :value="2" />
            <el-option label="疾病咨询" :value="3" />
            <el-option label="健康养生" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题内容" prop="content">
          <el-input v-model="questionForm.content" type="textarea" :rows="8" placeholder="请详细描述你的问题..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showQuestionDialog = false">取消</el-button>
        <el-button type="primary" @click="handlePublishQuestion" :loading="publishing">发布</el-button>
      </template>
    </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { Search, Plus, User, View, ChatDotRound } from '@element-plus/icons-vue'
import { getQuestionList, searchQuestion, publishQuestion } from '@/api/qa'
import { ElMessage } from 'element-plus'
import LikeIcon from '../../components/icon/LikeIcon.vue'

const userStore = useUserStore()

const loading = ref(false)
const questionList = ref([])
const total = ref(0)
const searchKeyword = ref('')
const showQuestionDialog = ref(false)
const publishing = ref(false)
const questionFormRef = ref(null)

const queryParams = reactive({
  page: 1,
  size: 10,
  categoryId: null
})

const questionForm = reactive({
  title: '',
  content: '',
  categoryId: null
})

const questionRules = {
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入问题内容', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

const loadQuestions = async () => {
  loading.value = true
  try {
    let res
    if (searchKeyword.value) {
      res = await searchQuestion(searchKeyword.value, queryParams.page, queryParams.size)
    } else {
      res = await getQuestionList(queryParams)
    }
    if (res.code === 200) {
      questionList.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    ElMessage.error('加载问题列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  loadQuestions()
}

const handlePageChange = () => {
  loadQuestions()
}

const handleSizeChange = () => {
  queryParams.page = 1
  loadQuestions()
}

const handlePublishQuestion = async () => {
  if (!questionFormRef.value) return
  await questionFormRef.value.validate(async (valid) => {
    if (valid) {
      publishing.value = true
      try {
        const res = await publishQuestion(questionForm)
        if (res.code === 200) {
          ElMessage.success('提问成功')
          showQuestionDialog.value = false
          Object.assign(questionForm, {
            title: '',
            content: '',
            categoryId: null
          })
          loadQuestions()
        }
      } catch (error) {
        ElMessage.error('提问失败')
      } finally {
        publishing.value = false
      }
    }
  })
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

watch(() => queryParams.categoryId, () => {
  queryParams.page = 1
  loadQuestions()
})

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.qa-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.qa-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-header h2 {
  margin: 0;
}

.filter-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

.question-list {
  min-height: 400px;
}

.question-item {
  margin-bottom: 15px;
  cursor: pointer;
  transition: all 0.3s;
}

.question-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.question-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  flex: 1;
}

.question-content {
  font-size: 14px;
  color: #666;
  margin-bottom: 15px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.question-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #999;
}

.question-meta {
  display: flex;
  gap: 15px;
}

.question-meta span {
  display: flex;
  align-items: center;
  gap: 5px;
}
</style>

