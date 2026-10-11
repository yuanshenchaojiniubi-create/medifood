<template>
  <PageLayout>
    <template #main>
      <el-card class="feed-card">
        <template #header>
          <div class="page-header">
            <el-tabs v-model="activeTab" @tab-change="handleTabChange">
              <el-tab-pane label="关注动态" name="feed" />
              <el-tab-pane label="热门动态" name="hot" />
              <el-tab-pane v-if="searchKeyword" label="搜索结果" name="search" />
            </el-tabs>
            <div class="header-actions">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索动态..."
                class="search-input"
                @keyup.enter="handleSearch"
                clearable
                @clear="handleSearchClear"
              >
                <template #append>
                  <el-button :icon="Search" @click="handleSearch" />
                </template>
              </el-input>
              <el-button type="primary" @click="showPostDialog = true">
                <el-icon><Plus /></el-icon> 发布动态
              </el-button>
            </div>
          </div>
        </template>

              <transition name="slide-bar">
                <div v-if="newPostsCount > 0" class="new-posts-bar" @click="refreshFeed">
                  <el-icon><Top /></el-icon>
                  有 {{ newPostsCount }} 条新动态，点击刷新
                  <el-icon class="close-hint" @click.stop="newPostsCount = 0"><Close /></el-icon>
                </div>
              </transition>

              <div class="post-scroll-area" ref="scrollAreaRef" @scroll="onScroll">
                <div v-if="initialLoading" class="skeleton-list">
                  <div v-for="i in 3" :key="'sk'+i" class="skeleton-card">
                    <div class="sk-header">
                      <div class="sk-avatar skeleton-pulse"></div>
                      <div class="sk-info">
                        <div class="sk-name skeleton-pulse"></div>
                        <div class="sk-time skeleton-pulse"></div>
                      </div>
                    </div>
                    <div class="sk-content">
                      <div class="sk-line skeleton-pulse"></div>
                      <div class="sk-line skeleton-pulse"></div>
                      <div class="sk-line sk-line-short skeleton-pulse"></div>
                    </div>
                  </div>
                </div>

                <div v-else class="post-list">
                  <div
                    v-for="post in postList"
                    :key="post.id"
                    class="post-item"
                    @click="goDetail(post)"
                  >
                    <div class="post-header">
                      <el-avatar :size="44" :src="post.avatar" class="post-avatar">
                        {{ post.nickname?.[0] }}
                      </el-avatar>
                      <div class="post-user-info">
                        <div class="post-username">{{ post.nickname || post.username }}</div>
                        <div class="post-time">{{ formatTime(post.createTime) }}</div>
                      </div>
                    </div>

                    <div class="post-type">
                      <el-tag size="small" :type="getPostTypeTag(post.postType)">
                        {{ getPostTypeName(post.postType) }}
                      </el-tag>
                    </div>

                    <div
                      class="post-content"
                      :class="{ 'post-content--collapsed': isContentLong(post.content) && !expandedPosts.has(post.id) }"
                      :ref="el => { if (el && isContentLong(post.content)) contentRefs[post.id] = el }"
                    >
                      {{ post.content }}
                    </div>
                    <span
                      v-if="isContentLong(post.content)"
                      class="expand-btn"
                      @click.stop="toggleExpand(post.id)"
                    >
                      {{ expandedPosts.has(post.id) ? '收起' : '展开全文' }}
                    </span>

                    <div v-if="post.images && post.images.length > 0" class="post-images">
                      <el-image
                        v-for="(img, index) in post.images"
                        :key="index"
                        :src="img"
                        :preview-src-list="post.images"
                        :initial-index="index"
                        fit="cover"
                        loading="lazy"
                        class="post-image-thumb"
                      />
                    </div>

                    <div class="post-actions">
                      <el-button text @click.stop="handleLikePost(post)" :loading="post.liking">
                        <LikeIcon :active="post.isLiked" style="margin-right: 4px;" />
                        <span :style="{ color: post.isLiked ? '#f56c6c' : '' }">
                          {{ post.isLiked ? '已赞' : '点赞' }} ({{ post.likeCount }})
                        </span>
                      </el-button>
                      <el-button text @click.stop="showCommentDialog = true; currentPost = post">
                        <el-icon><ChatDotRound /></el-icon>
                        评论 ({{ post.commentCount }})
                      </el-button>
                      <el-button text>
                        <el-icon><Share /></el-icon>
                        分享 ({{ post.shareCount }})
                      </el-button>
                      <el-button
                        v-if="userStore.userInfo && post.userId === userStore.userInfo.id"
                        text
                        type="danger"
                        @click.stop="handleDeletePost(post)"
                      >
                        <el-icon><Delete /></el-icon>
                        删除
                      </el-button>
                    </div>
                  </div>

                  <div ref="sentinelRef" class="scroll-sentinel">
                    <div v-if="loadingMore" class="sentinel-loading">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>加载中...</span>
                    </div>
                    <div v-else-if="noMore && postList.length > 0" class="sentinel-end">
                      — 已经到底了 —
                    </div>
                    <el-empty v-if="!initialLoading && postList.length === 0 && !loadingMore" description="暂无动态" />
                  </div>
                </div>
              </div>
            </el-card>
    </template>
  </PageLayout>

    <transition name="fade-scale">
      <div v-if="showBackTop" class="back-to-top-btn" @click="scrollToTop">
        <el-icon :size="22"><Top /></el-icon>
      </div>
    </transition>

    <el-dialog v-model="showPostDialog" title="发布动态" width="600px">
      <el-form :model="postForm" :rules="postRules" ref="postFormRef" label-width="100px">
        <el-form-item label="动态类型" prop="postType">
          <el-radio-group v-model="postForm.postType">
            <el-radio :label="1">普通动态</el-radio>
            <el-radio :label="2">健康打卡</el-radio>
            <el-radio :label="3">心得体会</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="postForm.content" type="textarea" :rows="6" placeholder="分享你的养生心得..." />
        </el-form-item>
        <el-form-item label="图片">
          <multi-upload v-model="postForm.images" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPostDialog = false">取消</el-button>
        <el-button type="primary" @click="handlePublishPost" :loading="posting">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCommentDialog" title="发表评论" width="600px">
      <el-form :model="commentForm" :rules="commentRules" ref="commentFormRef">
        <el-form-item prop="content">
          <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="5"
            placeholder="请输入评论内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCommentDialog = false">取消</el-button>
        <el-button type="primary" @click="handleComment" :loading="commenting">发表</el-button>
      </template>
    </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { Plus, ChatDotRound, Share, Search, Delete, Top, Loading, Close } from '@element-plus/icons-vue'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import MultiUpload from '@/components/upload/multiUpload.vue'
import { getFeed, getHotPosts, publishPost, likePost, unlikePost, commentPost, searchPosts, deletePost } from '@/api/community'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()

const scrollAreaRef = ref(null)
const sentinelRef = ref(null)
const postFormRef = ref(null)
const commentFormRef = ref(null)
const contentRefs = ref({})
const expandedPosts = ref(new Set())

const postList = ref([])
const activeTab = ref('feed')
const showPostDialog = ref(false)
const showCommentDialog = ref(false)
const posting = ref(false)
const commenting = ref(false)
const searchKeyword = ref('')
const currentPost = ref(null)
const initialLoading = ref(true)
const loadingMore = ref(false)
const noMore = ref(false)
const showBackTop = ref(false)
const newPostsCount = ref(0)

let observer = null
let currentPage = 1
let totalRecords = 0
const pageSize = 10

const postForm = reactive({
  content: '',
  postType: 1,
  images: []
})

const commentForm = reactive({
  content: ''
})

const postRules = {
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  postType: [{ required: true, message: '请选择动态类型', trigger: 'change' }]
}

const commentRules = {
  content: [{ required: true, message: '请输入评论内容', trigger: 'blur' }]
}

const CONTENT_MAX_LINES = 6

const isContentLong = (content) => {
  if (!content) return false
  return content.length > 200 || content.split('\n').length > CONTENT_MAX_LINES
}

const toggleExpand = (postId) => {
  if (expandedPosts.value.has(postId)) {
    expandedPosts.value.delete(postId)
  } else {
    expandedPosts.value.add(postId)
  }
  expandedPosts.value = new Set(expandedPosts.value)
}

const fetchPosts = async (append = false) => {
  if (!append) {
    initialLoading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    let res
    if (activeTab.value === 'feed') {
      res = await getFeed(currentPage, pageSize)
    } else if (activeTab.value === 'hot') {
      res = await getHotPosts(currentPage, pageSize)
    } else if (activeTab.value === 'search') {
      res = await searchPosts(searchKeyword.value, currentPage, pageSize)
    }
    if (res && res.code === 200) {
      const records = res.data.records || []
      totalRecords = res.data.total || 0
      if (append) {
        postList.value = [...postList.value, ...records]
      } else {
        postList.value = records
      }
      noMore.value = postList.value.length >= totalRecords
    }
  } catch (error) {
    ElMessage.error('加载动态失败')
  } finally {
    initialLoading.value = false
    loadingMore.value = false
  }
}

const setupObserver = () => {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && !loadingMore.value && !noMore.value && !initialLoading.value) {
      currentPage++
      fetchPosts(true)
    }
  }, { root: scrollAreaRef.value, threshold: 0.1 })
  nextTick(() => {
    if (sentinelRef.value) observer.observe(sentinelRef.value)
  })
}

const onScroll = () => {
  if (!scrollAreaRef.value) return
  showBackTop.value = scrollAreaRef.value.scrollTop > 500
}

const scrollToTop = () => {
  if (scrollAreaRef.value) {
    scrollAreaRef.value.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

const refreshFeed = async () => {
  newPostsCount.value = 0
  currentPage = 1
  await fetchPosts(false)
  scrollToTop()
}

const handleSearch = () => {
  if (!searchKeyword.value.trim()) {
    activeTab.value = 'feed'
    resetAndReload()
    return
  }
  activeTab.value = 'search'
  resetAndReload()
}

const handleSearchClear = () => {
  searchKeyword.value = ''
  activeTab.value = 'feed'
  resetAndReload()
}

const handleTabChange = () => {
  resetAndReload()
}

const resetAndReload = () => {
  currentPage = 1
  noMore.value = false
  postList.value = []
  newPostsCount.value = 0
  expandedPosts.value = new Set()
  fetchPosts(false)
  nextTick(() => setupObserver())
}

const handleDeletePost = (post) => {
  ElMessageBox.confirm('确定要删除这条动态吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        const res = await deletePost(post.id)
        if (res.code === 200) {
          ElMessage.success('删除成功')
          postList.value = postList.value.filter(p => p.id !== post.id)
        }
      } catch (error) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

const handlePublishPost = async () => {
  if (!postFormRef.value) return
  await postFormRef.value.validate(async (valid) => {
    if (valid) {
      posting.value = true
      try {
        const data = { ...postForm }
        const res = await publishPost(data)
        if (res.code === 200) {
          ElMessage.success('发布成功')
          showPostDialog.value = false
          Object.assign(postForm, { content: '', postType: 1, images: [] })
          resetAndReload()
        }
      } catch (error) {
        ElMessage.error('发布失败')
      } finally {
        posting.value = false
      }
    }
  })
}

const handleLikePost = async (post) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  post.liking = true
  try {
    if (post.isLiked) {
      await unlikePost(post.id)
      post.isLiked = false
      post.likeCount--
    } else {
      await likePost(post.id)
      post.isLiked = true
      post.likeCount++
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    post.liking = false
  }
}

const handleComment = async () => {
  if (!commentFormRef.value || !currentPost.value) return
  await commentFormRef.value.validate(async (valid) => {
    if (valid) {
      commenting.value = true
      try {
        const res = await commentPost(currentPost.value.id, commentForm.content)
        if (res.code === 200) {
          ElMessage.success('评论成功')
          showCommentDialog.value = false
          commentForm.content = ''
          currentPost.value.commentCount++
        }
      } catch (error) {
        ElMessage.error('评论失败')
      } finally {
        commenting.value = false
      }
    }
  })
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

const goDetail = (post) => {
  router.push(`/community/post/${post.id}`)
}

onMounted(async () => {
  await fetchPosts(false)
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})
</script>

<style scoped>
.feed-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.feed-card :deep(.el-card__header) {
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.feed-card :deep(.el-card__body) {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input {
  width: 240px;
}

/* ===== 新动态提示条 ===== */
.new-posts-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  margin: 0 16px;
  background: linear-gradient(135deg, #e6f7ff, #bae7ff);
  border: 1px solid #91d5ff;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #1890ff;
  transition: background 0.2s;
}

.new-posts-bar:hover {
  background: linear-gradient(135deg, #bae7ff, #91d5ff);
}

.close-hint {
  margin-left: auto;
  opacity: 0.5;
  cursor: pointer;
  font-size: 12px;
}

/* ===== 滚动区域 ===== */
.post-scroll-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 16px 20px;
  scroll-behavior: smooth;
}

.post-scroll-area::-webkit-scrollbar {
  width: 6px;
}

.post-scroll-area::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

/* ===== 骨架屏 ===== */
.skeleton-list {
  padding: 4px 0;
}

.skeleton-card {
  padding: 20px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.sk-header {
  display: flex;
  gap: 14px;
  margin-bottom: 18px;
}

.sk-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
}

.sk-info {
  flex: 1;
}

.sk-name {
  width: 100px;
  height: 16px;
  border-radius: 4px;
  margin-bottom: 8px;
}

.sk-time {
  width: 60px;
  height: 12px;
  border-radius: 4px;
}

.sk-content {
  margin-left: 58px;
}

.sk-line {
  height: 14px;
  border-radius: 4px;
  margin-bottom: 10px;
}

.sk-line-short {
  width: 60%;
}

.skeleton-pulse {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ===== 动态列表 ===== */
.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-item {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 18px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.post-item:hover {
  border-color: #d9d9d9;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.post-header {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  align-items: center;
}

.post-avatar {
  flex-shrink: 0;
}

.post-user-info {
  flex: 1;
}

.post-username {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.post-time {
  font-size: 12px;
  color: #999;
}

.post-type {
  margin-bottom: 12px;
}

.post-content {
  margin-bottom: 4px;
  line-height: 1.8;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-content--collapsed {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 6;
  overflow: hidden;
}

.expand-btn {
  display: inline-block;
  color: #409eff;
  font-size: 14px;
  cursor: pointer;
  margin-bottom: 12px;
  user-select: none;
}

.expand-btn:hover {
  color: #66b1ff;
}

.post-images {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-image-thumb {
  width: 130px;
  height: 130px;
  border-radius: 6px;
  object-fit: cover;
}

.post-actions {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

/* ===== 滚动哨兵 ===== */
.scroll-sentinel {
  padding: 24px 0 8px;
  text-align: center;
}

.sentinel-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 14px;
}

.sentinel-end {
  color: #c0c4cc;
  font-size: 13px;
}

/* ===== 回顶部按钮 ===== */
.back-to-top-btn {
  position: fixed;
  right: 40px;
  bottom: 60px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  color: #606266;
  z-index: 100;
  transition: box-shadow 0.2s, transform 0.2s;
}

.back-to-top-btn:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
  transform: translateY(-2px);
  color: #409eff;
}

/* ===== 过渡动画 ===== */
.slide-bar-enter-active,
.slide-bar-leave-active {
  transition: all 0.3s ease;
}

.slide-bar-enter-from,
.slide-bar-leave-to {
  opacity: 0;
  transform: translateY(-10px);
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin-bottom: 0;
  border-width: 0;
}

.fade-scale-enter-active,
.fade-scale-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.fade-scale-enter-from,
.fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.8);
}
</style>
</｜DSML｜tool_calls