<template>
  <PageLayout>
    <template #main>
      <el-card class="detail-card" v-loading="loading">
        <div v-if="knowledge">
          <!-- 标题 -->
          <div class="detail-header">
            <h1>{{ knowledge.title }}</h1>
            <div class="detail-meta">
              <span class="author-link" @click="goToUser(knowledge.userId)">
                <el-icon><User /></el-icon> {{ knowledge.nickname || knowledge.username }}
                <el-tag v-if="knowledge.isDoctor" type="success" size="small" effect="plain" class="doctor-badge-inline">医师</el-tag>
              </span>
              <span><el-icon><Clock /></el-icon> {{ formatTime(knowledge.createTime) }}</span>
              <span><el-icon><View /></el-icon> {{ knowledge.viewCount }} 浏览</span>
            </div>
          </div>

          <!-- 封面图 -->
          <div v-if="knowledge.coverImage" class="cover-image">
            <img :src="knowledge.coverImage" alt="" />
          </div>

          <!-- 内容 -->
          <div class="detail-content" v-html="sanitizedContent"></div>

          <!-- 标签 -->
          <div v-if="knowledge.ingredients && knowledge.ingredients.length > 0" class="tags-section">
            <el-tag v-for="ingredient in knowledge.ingredients" :key="ingredient" class="ingredient-tag">
              {{ ingredient }}
            </el-tag>
          </div>

          <!-- 操作栏 -->
          <div class="action-bar">
            <el-button v-if="canEdit" type="primary" plain @click="goToEdit">编辑</el-button>
            <el-button @click="handleLike" :loading="likeLoading" :class="{ 'liked-btn': knowledge.isLiked }">
              <LikeIcon :active="knowledge.isLiked" style="margin-right: 6px;" />
              <span>{{ knowledge.isLiked ? '已点赞' : '点赞' }} ({{ knowledge.likeCount }})</span>
            </el-button>
            <el-button :type="knowledge.isCollected ? 'warning' : 'default'" @click="handleCollect" :loading="collectLoading">
              <el-icon><Star /></el-icon>
              {{ knowledge.isCollected ? '已收藏' : '收藏' }} ({{ knowledge.collectCount }})
            </el-button>
            <el-button @click="showCommentDialog = true">
              <el-icon><ChatDotRound /></el-icon>
              评论 ({{ knowledge.commentCount }})
            </el-button>
            <el-button @click="showShareDialog = true">
              <el-icon><Share /></el-icon>
              分享
            </el-button>
          </div>

          <!-- 评论列表 -->
          <div class="comments-section">
            <h3>评论 ({{ knowledge.commentCount }})</h3>
            <div v-loading="commentLoading">
              <div v-for="comment in comments" :key="comment.id" class="comment-item">
                <div class="comment-header">
                  <el-avatar class="clickable" :size="40" :src="comment.avatar" @click="goToUser(comment.userId)">
                    {{ comment.nickname?.[0] }}
                  </el-avatar>
                  <div class="comment-info">
                    <div class="comment-author clickable" @click="goToUser(comment.userId)">
                      {{ comment.nickname || comment.username }}
                    </div>
                    <div class="comment-time">{{ formatTime(comment.createTime) }}</div>
                  </div>
                </div>
                <div class="comment-content">{{ comment.content }}</div>
                <div class="comment-actions">
                  <el-button text @click="handleReply(comment)">
                    <el-icon><ChatDotRound /></el-icon> 回复
                  </el-button>
                  <el-button text>
                    <LikeIcon :active="comment.isLiked" style="margin-right: 4px;" />
                    <span :style="{ color: comment.isLiked ? '#f56c6c' : '' }">{{ comment.likeCount }}</span>
                  </el-button>
                </div>
              </div>
              <el-empty v-if="!commentLoading && comments.length === 0" description="暂无评论，快来抢沙发~" />
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="知识不存在或已被删除" />
      </el-card>
    </template>
  </PageLayout>

  <!-- 评论对话框 -->
  <el-dialog v-model="showCommentDialog" title="发表评论" width="600px">
    <el-form :model="commentForm" :rules="commentRules" ref="commentFormRef">
      <el-form-item prop="content">
        <el-input v-model="commentForm.content" type="textarea" :rows="5" placeholder="请输入评论内容" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showCommentDialog = false">取消</el-button>
      <el-button type="primary" @click="handleComment" :loading="commenting">发表</el-button>
    </template>
  </el-dialog>

  <ShareDialog
    v-model="showShareDialog"
    share-type="KNOWLEDGE"
    :share-id="knowledge?.id"
    :share-title="knowledge?.title"
    :share-image="knowledge?.coverImage"
    :share-url="`/knowledge/${knowledge?.id}`"
  />
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { User, Clock, View, Star, ChatDotRound, Share } from '@element-plus/icons-vue'
import {
  getKnowledgeDetail, likeKnowledge, unlikeKnowledge,
  collectKnowledge, uncollectKnowledge, commentKnowledge, getKnowledgeComments
} from '@/api/knowledge'
import { ElMessage } from 'element-plus'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import ShareDialog from '@/components/ShareDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const knowledge = ref(null)

// XSS 防护：富文本内容渲染前先消毒，剥离脚本等危险标签
const sanitizedContent = computed(() => DOMPurify.sanitize(knowledge.value?.content || ''))
const comments = ref([])
const commentLoading = ref(false)
const likeLoading = ref(false)
const collectLoading = ref(false)
const showCommentDialog = ref(false)
const showShareDialog = ref(false)
const commenting = ref(false)
const commentFormRef = ref(null)

const commentForm = reactive({
  content: '',
  parentId: null
})

const commentRules = {
  content: [{ required: true, message: '请输入评论内容', trigger: 'blur' }]
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeDetail(route.params.id)
    if (res.code === 200) {
      knowledge.value = res.data
      loadComments()
    }
  } catch (error) {
    ElMessage.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

const loadComments = async () => {
  commentLoading.value = true
  try {
    const res = await getKnowledgeComments(route.params.id, 1, 20)
    if (res.code === 200) {
      comments.value = res.data.records || []
    }
  } catch (error) {
    /* ignore */
  } finally {
    commentLoading.value = false
  }
}

const handleLike = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  likeLoading.value = true
  try {
    if (knowledge.value.isLiked) {
      await unlikeKnowledge(route.params.id)
      knowledge.value.isLiked = false
      knowledge.value.likeCount--
    } else {
      await likeKnowledge(route.params.id)
      knowledge.value.isLiked = true
      knowledge.value.likeCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    likeLoading.value = false
  }
}

const handleCollect = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  collectLoading.value = true
  try {
    if (knowledge.value.isCollected) {
      await uncollectKnowledge(route.params.id)
      knowledge.value.isCollected = false
      knowledge.value.collectCount--
    } else {
      await collectKnowledge(route.params.id)
      knowledge.value.isCollected = true
      knowledge.value.collectCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    collectLoading.value = false
  }
}

const handleComment = async () => {
  if (!commentFormRef.value) return
  await commentFormRef.value.validate(async (valid) => {
    if (valid) {
      commenting.value = true
      try {
        const res = await commentKnowledge({
          knowledgeId: route.params.id,
          content: commentForm.content,
          parentId: commentForm.parentId
        })
        if (res.code === 200) {
          ElMessage.success('评论成功')
          showCommentDialog.value = false
          commentForm.content = ''
          commentForm.parentId = null
          loadComments()
          if (knowledge.value) knowledge.value.commentCount++
        }
      } catch (error) {
        ElMessage.error('评论失败')
      } finally {
        commenting.value = false
      }
    }
  })
}

const handleReply = (comment) => {
  commentForm.parentId = comment.id
  showCommentDialog.value = true
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

const goToUser = (userId) => {
  if (userId == null) return
  const loginId = userStore.userInfo?.id
  if (loginId != null && String(loginId) === String(userId)) {
    router.push({ path: `/user/${userId}` })
  } else {
    router.push({ path: `/profile/${userId}` })
  }
}

const canEdit = computed(() => {
  if (!knowledge.value || !userStore.userInfo?.id) return false
  return String(knowledge.value.userId) === String(userStore.userInfo.id)
})

const goToEdit = () => {
  router.push(`/knowledge/${route.params.id}/edit`)
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.detail-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.detail-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-header h1 {
  margin: 0 0 16px 0;
  font-size: 26px;
  color: #1a1a1a;
  line-height: 1.4;
}

.detail-meta {
  display: flex;
  gap: 24px;
  color: #999;
  font-size: 14px;
  flex-wrap: wrap;
}

.detail-meta span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cover-image {
  margin: 24px 0;
  border-radius: 10px;
  overflow: hidden;
}

.cover-image img {
  width: 100%;
  max-height: 420px;
  object-fit: cover;
}

.detail-content {
  margin: 28px 0;
  line-height: 1.9;
  font-size: 16px;
  color: #333;
}

.detail-content :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

.tags-section {
  margin: 20px 0;
  padding: 16px;
  background-color: #fafafa;
  border-radius: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ingredient-tag {
  margin: 0;
}

.action-bar {
  margin: 28px 0;
  padding: 20px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.liked-btn {
  border-color: #f56c6c !important;
}

.comments-section {
  margin-top: 32px;
}

.comments-section h3 {
  margin-bottom: 20px;
  color: #1a1a1a;
  font-size: 18px;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid #f5f5f5;
}

.comment-header {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
}

.comment-info {
  flex: 1;
}

.comment-author {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.comment-time {
  font-size: 12px;
  color: #bbb;
}

.comment-content {
  margin: 10px 0;
  line-height: 1.7;
  color: #555;
}

.comment-actions {
  display: flex;
  gap: 8px;
}

.clickable { cursor: pointer; }
.author-link { color: #409eff; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
.author-link:hover { color: #66b1ff; }

.doctor-badge-inline {
  margin-left: 2px;
  vertical-align: middle;
}
</style>