<template>
  <PageLayout>
    <template #main>
      <el-card class="detail-card" v-loading="loading">
        <div v-if="recipe">
          <!-- 标题 -->
          <div class="detail-header">
            <h1>{{ recipe.title }}</h1>
            <div class="detail-meta">
              <span class="author-link" @click="goToUser(recipe.userId)">
                <el-icon><User /></el-icon> {{ recipe.nickname || recipe.username }}
                <el-tag v-if="recipe.isDoctor" type="success" size="small" effect="plain" class="doctor-badge-inline">医师</el-tag>
              </span>
              <span><el-icon><Clock /></el-icon> {{ formatTime(recipe.createTime) }}</span>
              <span><el-icon><View /></el-icon> {{ recipe.viewCount }} 浏览</span>
            </div>
          </div>

          <!-- 封面图 -->
          <div v-if="recipe.coverImage" class="cover-image">
            <img :src="recipe.coverImage" alt="" />
          </div>

          <!-- 描述 -->
          <div v-if="recipe.description" class="description-box">
            <el-icon :size="18"><InfoFilled /></el-icon>
            <span>{{ recipe.description }}</span>
          </div>

          <!-- 基本信息 -->
          <div class="info-cards">
            <div class="info-card">
              <div class="info-label">难度</div>
              <div class="info-value">
                <el-rate v-model="recipe.difficulty" disabled show-score />
              </div>
            </div>
            <div class="info-card">
              <div class="info-label">制作时间</div>
              <div class="info-value">{{ recipe.cookingTime }} 分钟</div>
            </div>
            <div class="info-card">
              <div class="info-label">份量</div>
              <div class="info-value">{{ recipe.servings }} 人份</div>
            </div>
          </div>

          <!-- 食材组成 -->
          <div class="section">
            <h3>🥬 食材组成</h3>
            <el-table :data="recipe.ingredients" border stripe class="ingredient-table">
              <el-table-column prop="name" label="食材名称" />
              <el-table-column prop="amount" label="用量" />
            </el-table>
          </div>

          <!-- 制作步骤 -->
          <div class="section">
            <h3>👨‍🍳 制作步骤</h3>
            <div v-for="(step, index) in recipe.steps" :key="index" class="step-item">
              <div class="step-number">{{ index + 1 }}</div>
              <div class="step-content">
                <div class="step-description">{{ step.description }}</div>
                <div v-if="step.image" class="step-image">
                  <img :src="step.image" alt="" />
                </div>
              </div>
            </div>
          </div>

          <!-- 适用人群和功效 -->
          <div class="info-row" v-if="recipe.suitableFor || recipe.effects">
            <div class="info-block" v-if="recipe.suitableFor">
              <h3>👥 适用人群</h3>
              <p>{{ recipe.suitableFor }}</p>
            </div>
            <div class="info-block" v-if="recipe.effects">
              <h3>💪 功效说明</h3>
              <p>{{ recipe.effects }}</p>
            </div>
          </div>

          <!-- 标签 -->
          <div v-if="recipe.tags && recipe.tags.length > 0" class="tags-section">
            <el-tag v-for="tag in recipe.tags" :key="tag" class="recipe-tag">{{ tag }}</el-tag>
          </div>

          <!-- 操作栏 -->
          <div class="action-bar">
            <el-button v-if="canEdit" type="primary" plain @click="goToEdit">编辑</el-button>
            <el-button @click="handleLike" :loading="likeLoading" :class="{ 'liked-btn': recipe.isLiked }">
              <LikeIcon :active="recipe.isLiked" style="margin-right: 6px;" />
              <span>{{ recipe.isLiked ? '已点赞' : '点赞' }} ({{ recipe.likeCount }})</span>
            </el-button>
            <el-button @click="handleCollect" :loading="collectLoading" :class="{ 'collected-btn': recipe.isCollected }">
              <el-icon><StarFilled v-if="recipe.isCollected" style="color: #e6a23c;" /></el-icon>
              <span>{{ recipe.isCollected ? '已收藏' : '收藏' }} ({{ recipe.collectCount }})</span>
            </el-button>
            <el-button @click="showCommentDialog = true">
              <el-icon><ChatDotRound /></el-icon>
              评论 ({{ recipe.commentCount }})
            </el-button>
            <el-button @click="showShareDialog = true">
              <el-icon><Share /></el-icon>
              分享
            </el-button>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="食谱不存在或已被删除" />
      </el-card>
    </template>
  </PageLayout>

  <!-- 评论弹窗 -->
  <el-dialog v-model="showCommentDialog" title="评论" width="600px">
    <div class="comments-list" v-if="comments && comments.length">
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <div class="comment-meta">
          <strong>{{ c.nickname || c.username || c.userName || '匿名' }}</strong>
          <span class="time">{{ formatTime(c.createTime) }}</span>
        </div>
        <div class="comment-content">{{ c.content }}</div>
      </div>
    </div>
    <div v-else class="no-comments">暂无评论，快来抢沙发~</div>

    <div class="comment-box" style="margin-top:15px;">
      <el-input type="textarea" v-model="commentContent" :rows="4" placeholder="请输入评论内容" />
      <div style="text-align:right;margin-top:10px;">
        <el-button @click="showCommentDialog = false">取消</el-button>
        <el-button type="primary" :loading="commentLoading" @click="handleCommentSubmit">提交</el-button>
      </div>
    </div>
  </el-dialog>

  <ShareDialog
    v-model="showShareDialog"
    share-type="RECIPE"
    :share-id="recipe?.id"
    :share-title="recipe?.title"
    :share-image="recipe?.coverImage"
    :share-url="`/recipe/${recipe?.id}`"
  />
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { User, Clock, View, ChatDotRound, StarFilled, Share, InfoFilled } from '@element-plus/icons-vue'
import { getRecipeDetail, likeRecipe, unlikeRecipe, commentRecipe, collectRecipe, uncollectRecipe } from '@/api/recipe'
import { ElMessage } from 'element-plus'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import ShareDialog from '@/components/ShareDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const recipe = ref(null)
const likeLoading = ref(false)
const collectLoading = ref(false)
const showCommentDialog = ref(false)
const showShareDialog = ref(false)

const comments = ref([])
const commentContent = ref('')
const commentLoading = ref(false)

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getRecipeDetail(route.params.id)
    if (res.code === 200) {
      recipe.value = res.data
      comments.value = res.data.comments || []
    }
  } catch (error) {
    ElMessage.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

const handleCommentSubmit = async () => {
  if (!userStore.isLoggedIn && !userStore.token) {
    ElMessage.warning('请先登录')
    return
  }
  const content = (commentContent.value || '').trim()
  if (!content) {
    ElMessage.warning('请输入评论内容')
    return
  }
  commentLoading.value = true
  try {
    const res = await commentRecipe(route.params.id, content, null)
    if (res && res.code === 200) {
      const newComment = res.data || {
        id: Date.now(),
        content,
        nickname: userStore.user?.nickname || userStore.username || '我',
        createTime: new Date().toISOString()
      }
      comments.value.unshift(newComment)
      if (recipe.value) recipe.value.commentCount = (recipe.value.commentCount || 0) + 1
      commentContent.value = ''
      ElMessage.success('评论成功')
    } else {
      ElMessage.error(res?.message || '评论失败')
    }
  } catch (e) {
    ElMessage.error('评论失败')
  } finally {
    commentLoading.value = false
  }
}

const handleCollect = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  collectLoading.value = true
  try {
    if (recipe.value.isCollected) {
      await uncollectRecipe(route.params.id)
      recipe.value.isCollected = false
      recipe.value.collectCount--
    } else {
      await collectRecipe(route.params.id)
      recipe.value.isCollected = true
      recipe.value.collectCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    collectLoading.value = false
  }
}

const handleLike = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  likeLoading.value = true
  try {
    if (recipe.value.isLiked) {
      await unlikeRecipe(route.params.id)
      recipe.value.isLiked = false
      recipe.value.likeCount--
    } else {
      await likeRecipe(route.params.id)
      recipe.value.isLiked = true
      recipe.value.likeCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    likeLoading.value = false
  }
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
  if (!recipe.value || !userStore.userInfo?.id) return false
  return String(recipe.value.userId) === String(userStore.userInfo.id)
})

const goToEdit = () => {
  router.push(`/recipe/${route.params.id}/edit`)
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

.description-box {
  margin: 24px 0;
  padding: 16px 20px;
  background: #f0f9ff;
  border-radius: 10px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  line-height: 1.8;
  color: #555;
  font-size: 15px;
}

/* 基本信息卡片 */
.info-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin: 28px 0;
}

.info-card {
  text-align: center;
  padding: 20px 16px;
  background: #fafafa;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}

.info-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 10px;
}

.info-value {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

/* 章节 */
.section {
  margin: 32px 0;
}

.section h3 {
  margin-bottom: 16px;
  color: #1a1a1a;
  font-size: 18px;
}

.ingredient-table {
  border-radius: 8px;
  overflow: hidden;
}

.step-item {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}

.step-number {
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  border-radius: 50%;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.step-content {
  flex: 1;
}

.step-description {
  line-height: 1.8;
  color: #333;
  margin-bottom: 10px;
}

.step-image {
  margin-top: 10px;
}

.step-image img {
  max-width: 100%;
  border-radius: 8px;
}

/* 适用人群和功效 */
.info-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin: 28px 0;
}

.info-block {
  padding: 20px;
  background: #fafafa;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}

.info-block h3 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: #1a1a1a;
}

.info-block p {
  margin: 0;
  line-height: 1.8;
  color: #555;
}

/* 标签 */
.tags-section {
  margin: 20px 0;
  padding: 16px;
  background: #fafafa;
  border-radius: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.recipe-tag {
  margin: 0;
}

/* 操作栏 */
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

.collected-btn {
  border-color: #e6a23c !important;
}

/* 评论弹窗 */
.comments-list { max-height: 300px; overflow: auto; padding-right: 8px; }
.comment-item { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.comment-meta { display: flex; justify-content: space-between; color: #666; font-size: 13px; }
.comment-content { margin-top: 6px; color: #333; line-height: 1.6; }
.no-comments { color: #999; text-align: center; padding: 20px 0; }

.author-link { color: #409eff; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
.author-link:hover { color: #66b1ff; }

.doctor-badge-inline {
  margin-left: 2px;
  vertical-align: middle;
}
</style>