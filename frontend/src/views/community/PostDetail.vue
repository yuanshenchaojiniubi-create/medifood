<template>
  <PageLayout>
    <template #main>
      <el-card v-loading="loading" class="detail-card">
        <template #header>
          <div class="page-header">
            <el-button @click="router.back()" text>
              <el-icon><ArrowLeft /></el-icon> 返回
            </el-button>
            <span class="header-title">动态详情</span>
          </div>
        </template>

        <div v-if="post" class="post-content-wrapper">
          <!-- 作者信息 -->
          <div class="post-header">
            <el-avatar :size="48" :src="post.avatar" @click="goToUser(post.userId)" class="clickable-avatar">
              {{ post.nickname?.[0] || post.username?.[0] }}
            </el-avatar>
            <div class="post-user-info">
              <div class="post-username" @click="goToUser(post.userId)" style="cursor: pointer;">
                {{ post.nickname || post.username }}
              </div>
              <div class="post-time">{{ formatTime(post.createTime) }}</div>
            </div>
            <el-tag size="small" :type="getPostTypeTag(post.postType)" effect="plain">
              {{ getPostTypeName(post.postType) }}
            </el-tag>
          </div>

          <!-- 动态内容 -->
          <div class="post-body">
            <div class="content-text">{{ post.content }}</div>
            <div v-if="post.images && post.images.length > 0" class="post-images">
              <el-image
                v-for="(img, index) in post.images"
                :key="index"
                :src="img"
                :preview-src-list="post.images"
                :initial-index="index"
                fit="cover"
                class="post-img"
              />
            </div>
          </div>

          <!-- 操作栏 -->
          <div class="post-actions">
            <el-button text @click="handleLikePost" :loading="liking">
              <LikeIcon :active="post.isLiked" style="margin-right: 4px;" />
              <span :style="{ color: post.isLiked ? '#f56c6c' : '' }">
                {{ post.isLiked ? '已赞' : '点赞' }} ({{ post.likeCount }})
              </span>
            </el-button>
            <el-button text @click="showCommentDialog = true">
              <el-icon><ChatDotRound /></el-icon>
              评论 ({{ post.commentCount }})
            </el-button>
            <el-button text @click="showShareDialog = true">
              <el-icon><Share /></el-icon>
              分享
            </el-button>
          </div>

          <!-- 评论列表 -->
          <div class="comments-section">
            <h3>评论 ({{ post.commentCount }})</h3>
            <div v-loading="commentsLoading">
              <div v-for="c in comments" :key="c.id" class="comment-item">
                <div class="comment-header">
                  <el-avatar :size="40" :src="c.avatar" @click="goToUser(c.userId)" class="clickable-avatar">
                    {{ c.nickname?.[0] }}
                  </el-avatar>
                  <div class="comment-info">
                    <div class="comment-author" @click="goToUser(c.userId)" style="cursor: pointer;">
                      {{ c.nickname || c.username }}
                    </div>
                    <div class="comment-time">{{ formatTime(c.createTime) }}</div>
                  </div>
                </div>
                <div class="comment-content">{{ c.content }}</div>
                <div class="comment-actions">
                  <el-button text @click="handleReply(c)">
                    <el-icon><ChatDotRound /></el-icon> 回复
                  </el-button>
                  <el-button text>
                    <el-icon><StarFilled /></el-icon> {{ c.likeCount || 0 }}
                  </el-button>
                </div>
                <!-- 回复列表 -->
                <div v-if="c.replies && c.replies.length" class="reply-list">
                  <div v-for="r in c.replies" :key="r.id" class="reply-item">
                    <div class="reply-header">
                      <el-avatar :size="30" :src="r.avatar" class="clickable-avatar">{{ r.nickname?.[0] }}</el-avatar>
                      <div class="reply-info">
                        <div class="reply-author">{{ r.nickname || r.username }}</div>
                        <div class="reply-time">{{ formatTime(r.createTime) }}</div>
                      </div>
                    </div>
                    <div class="reply-content">{{ r.content }}</div>
                  </div>
                </div>
              </div>
              <el-empty v-if="!commentsLoading && comments.length === 0" description="暂无评论，快来抢沙发~" />
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="动态不存在或已被删除" />
      </el-card>
    </template>
  </PageLayout>

  <!-- 评论对话框 -->
  <el-dialog v-model="showCommentDialog" title="发表评论" width="600px">
    <el-input v-model="commentContent" type="textarea" :rows="5" placeholder="请输入评论内容..." />
    <template #footer>
      <el-button @click="showCommentDialog = false">取消</el-button>
      <el-button type="primary" @click="submitComment" :loading="submitting">发送</el-button>
    </template>
  </el-dialog>

  <ShareDialog
    v-model="showShareDialog"
    share-type="POST"
    :share-id="post?.id"
    :share-title="post?.content?.slice(0, 30)"
    :share-image="post?.images?.[0]"
    :share-url="`/community/post/${post?.id}`"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostDetail, likePost, unlikePost, getPostComments, commentPost } from '@/api/community'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ChatDotRound, StarFilled, Share } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import ShareDialog from '@/components/ShareDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const loading = ref(false)
const liking = ref(false)
const comments = ref([])
const commentsLoading = ref(false)
const showCommentDialog = ref(false)
const showShareDialog = ref(false)
const commentContent = ref('')
const replyParentId = ref(null)
const submitting = ref(false)

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getPostDetail(route.params.id)
    if (res.code === 200) {
      post.value = res.data
    }
  } catch (error) {
    ElMessage.error('获取动态详情失败')
  } finally {
    loading.value = false
  }
}

const handleLikePost = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  liking.value = true
  try {
    if (post.value.isLiked) {
      await unlikePost(post.value.id)
      post.value.isLiked = false
      post.value.likeCount--
    } else {
      await likePost(post.value.id)
      post.value.isLiked = true
      post.value.likeCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    liking.value = false
  }
}

const loadComments = async () => {
  commentsLoading.value = true
  try {
    const res = await getPostComments(route.params.id, 1, 10)
    if (res.code === 200) {
      comments.value = res.data.records || []
    }
  } catch (error) {
    ElMessage.error('获取评论失败')
  } finally {
    commentsLoading.value = false
  }
}

const submitComment = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  const content = commentContent.value.trim()
  if (!content) { ElMessage.warning('请输入评论内容'); return }
  submitting.value = true
  try {
    await commentPost(route.params.id, content, replyParentId.value)
    ElMessage.success('评论成功')
    showCommentDialog.value = false
    commentContent.value = ''
    replyParentId.value = null
    if (post.value) post.value.commentCount++
    loadComments()
  } catch (error) {
    ElMessage.error('评论失败')
  } finally {
    submitting.value = false
  }
}

const handleReply = (comment) => {
  replyParentId.value = comment.id
  showCommentDialog.value = true
}

const getPostTypeName = (type) => {
  const types = { 1: '普通动态', 2: '健康打卡', 3: '心得体会' }
  return types[type] || '动态'
}

const getPostTypeTag = (type) => {
  const tags = { 1: '', 2: 'success', 3: 'warning' }
  return tags[type] || ''
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

onMounted(() => {
  loadDetail()
  loadComments()
})
</script>

<style scoped>
.detail-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-weight: 600;
  font-size: 17px;
  color: #333;
}

/* 作者信息 */
.post-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
}

.post-user-info {
  flex: 1;
}

.post-username {
  font-weight: 600;
  font-size: 16px;
  color: #333;
}

.post-time {
  font-size: 12px;
  color: #bbb;
  margin-top: 4px;
}

/* 内容 */
.post-body {
  margin-bottom: 24px;
}

.content-text {
  font-size: 16px;
  line-height: 1.9;
  color: #333;
  white-space: pre-wrap;
  margin-bottom: 16px;
}

.post-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.post-img {
  width: 200px;
  height: 200px;
  border-radius: 8px;
  object-fit: cover;
}

/* 操作栏 */
.post-actions {
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  gap: 8px;
}

/* 评论 */
.comments-section {
  margin-top: 24px;
}

.comments-section h3 {
  margin-bottom: 16px;
  font-size: 17px;
  color: #1a1a1a;
}

.comment-item {
  padding: 14px 0;
  border-bottom: 1px solid #f5f5f5;
}

.comment-header {
  display: flex;
  gap: 12px;
  align-items: center;
}

.comment-info {
  display: flex;
  flex-direction: column;
}

.comment-author {
  font-weight: 600;
  font-size: 14px;
  color: #333;
}

.comment-time {
  font-size: 12px;
  color: #bbb;
}

.comment-content {
  margin: 8px 0;
  line-height: 1.7;
  color: #555;
  padding-left: 52px;
}

.comment-actions {
  display: flex;
  gap: 12px;
  padding-left: 52px;
}

.reply-list {
  margin-left: 52px;
  margin-top: 10px;
  padding: 10px;
  background: #fafafa;
  border-radius: 8px;
}

.reply-item {
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.reply-item:last-child {
  border-bottom: none;
}

.reply-header {
  display: flex;
  gap: 10px;
  align-items: center;
}

.reply-info {
  display: flex;
  gap: 8px;
  align-items: center;
}

.reply-author {
  font-weight: 500;
  font-size: 13px;
  color: #333;
}

.reply-time {
  font-size: 12px;
  color: #bbb;
}

.reply-content {
  margin-top: 6px;
  padding-left: 40px;
  color: #666;
  font-size: 14px;
  line-height: 1.6;
}

.clickable-avatar {
  cursor: pointer;
}
</style>