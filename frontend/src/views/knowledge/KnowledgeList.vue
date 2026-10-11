<template>
  <PageLayout>
    <template #main>
      <el-card class="main-card">
        <template #header>
          <div class="page-header">
            <h2>养生知识</h2>
            <el-button
              v-if="userStore.isLoggedIn && userStore.isDoctor"
              type="primary"
              @click="showPublishDialog = true"
            >
              <el-icon><Plus /></el-icon> 发布知识
            </el-button>
            <el-tooltip
              v-else-if="userStore.isLoggedIn && !userStore.isDoctor"
              content="仅认证医师可发布养生知识，请在设置中申请医师认证"
              placement="bottom"
            >
              <el-button type="primary" disabled>发布知识</el-button>
            </el-tooltip>
          </div>
        </template>

              <div class="filter-bar">
                <div class="search-row">
                  <el-input
                    v-model="searchKeyword"
                    placeholder="搜索养生知识..."
                    class="search-input"
                    clearable
                    @keyup.enter="handleSearch"
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                  <el-select v-model="queryParams.sort" class="sort-select">
                    <el-option label="最新" value="latest" />
                    <el-option label="最热" value="hot" />
                  </el-select>
                  <el-button type="primary" @click="handleSearch">搜索</el-button>
                </div>
                <div class="category-row">
                  <span
                    v-for="cat in categories"
                    :key="cat.value"
                    class="category-chip"
                    :class="{ 'category-chip--active': queryParams.categoryId === cat.value }"
                    @click="selectCategory(cat.value)"
                  >
                    {{ cat.icon }} {{ cat.label }}
                  </span>
                </div>
              </div>

              <div class="scroll-area">
                <div v-if="initialLoading" class="skeleton-list">
                  <div v-for="i in 4" :key="'sk'+i" class="skeleton-card">
                    <div class="sk-left skeleton-pulse"></div>
                    <div class="sk-body">
                      <div class="sk-line sk-line--title skeleton-pulse"></div>
                      <div class="sk-line skeleton-pulse"></div>
                      <div class="sk-line skeleton-pulse"></div>
                      <div class="sk-line sk-line--short skeleton-pulse"></div>
                      <div class="sk-meta skeleton-pulse"></div>
                    </div>
                    <div class="sk-right skeleton-pulse"></div>
                  </div>
                </div>

                <div v-else class="knowledge-items">
                  <article
                    v-for="item in knowledgeList"
                    :key="item.id"
                    class="knowledge-card"
                    @click="$router.push(`/knowledge/${item.id}`)"
                  >
                    <div
                      class="card-accent"
                      :class="'accent--' + (item.categoryId || 1)"
                    ></div>
                    <div class="card-body">
                      <div class="card-category">
                        <el-tag size="small" :type="getCategoryTagType(item.categoryId)" effect="dark">
                          {{ getCategoryName(item.categoryId) }}
                        </el-tag>
                        <span class="card-time">{{ formatTime(item.createTime) }}</span>
                      </div>
                      <h3 class="card-title">{{ item.title }}</h3>
                      <p class="card-content" v-html="stripHtml(item.content?.substring(0, 200))"></p>
                      <div class="card-footer">
                        <div class="card-meta">
                          <span class="meta-author">
                            <el-avatar :size="20" :src="item.avatar">
                              {{ item.nickname?.[0] || item.username?.[0] }}
                            </el-avatar>
                            {{ item.nickname || item.username }}
                            <el-tag v-if="item.isDoctor" type="success" size="small" effect="plain" class="doctor-badge">医师推荐</el-tag>
                          </span>
                          <span><el-icon><View /></el-icon> {{ item.viewCount }}</span>
                          <span><LikeIcon :active="item.isLiked" :size="14" /> {{ item.likeCount }}</span>
                          <span><el-icon><ChatDotRound /></el-icon> {{ item.commentCount }}</span>
                        </div>
                      </div>
                    </div>
                    <div class="card-cover" v-if="item.coverImage">
                      <img :src="item.coverImage" :alt="item.title" loading="lazy" />
                    </div>
                  </article>

                  <div ref="sentinelRef" class="scroll-sentinel">
                    <div v-if="loadingMore" class="sentinel-loading">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>加载更多...</span>
                    </div>
                    <div v-else-if="noMore && knowledgeList.length > 0" class="sentinel-end">
                      — 已经到底了 —
                    </div>
                    <el-empty v-if="!initialLoading && knowledgeList.length === 0 && !loadingMore" description="暂无养生知识" />
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

    <el-dialog v-model="showPublishDialog" title="发布知识" width="800px">
      <el-form :model="publishForm" :rules="publishRules" ref="publishFormRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="publishForm.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="publishForm.categoryId" placeholder="请选择分类">
            <el-option label="食疗养生" :value="1" />
            <el-option label="药膳搭配" :value="2" />
            <el-option label="养生方法" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="publishForm.content" type="textarea" :rows="10" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="封面图片">
          <single-upload v-model="publishForm.coverImage" />
        </el-form-item>
        <el-form-item label="更多图片">
          <multi-upload v-model="publishForm.images" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPublishDialog = false">取消</el-button>
        <el-button type="primary" @click="handlePublish" :loading="publishing">发布</el-button>
      </template>
    </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { Search, Plus, View, ChatDotRound, Top, Loading } from '@element-plus/icons-vue'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import { getKnowledgeList, searchKnowledge, publishKnowledge } from '@/api/knowledge'
import { ElMessage } from 'element-plus'
import SingleUpload from '@/components/upload/singleUpload.vue'
import MultiUpload from '@/components/upload/multiUpload.vue'

const router = useRouter()
const userStore = useUserStore()

const sentinelRef = ref(null)
const publishFormRef = ref(null)

const knowledgeList = ref([])
const searchKeyword = ref('')
const showPublishDialog = ref(false)
const publishing = ref(false)
const initialLoading = ref(true)
const loadingMore = ref(false)
const noMore = ref(false)
const showBackTop = ref(false)

let observer = null
let currentPage = 1
let totalRecords = 0
const pageSize = 10

const categories = [
  { label: '全部', value: null, icon: '📚' },
  { label: '食疗养生', value: 1, icon: '🥗' },
  { label: '药膳搭配', value: 2, icon: '🍲' },
  { label: '养生方法', value: 3, icon: '🧘' },
]

const queryParams = reactive({
  page: 1,
  size: pageSize,
  categoryId: null,
  sort: 'latest'
})

const publishForm = reactive({
  title: '',
  content: '',
  categoryId: null,
  coverImage: '',
  images: []
})

const publishRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

const fetchKnowledge = async (append = false) => {
  if (!append) {
    initialLoading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    let res
    if (searchKeyword.value) {
      res = await searchKnowledge(searchKeyword.value, currentPage, pageSize)
    } else {
      res = await getKnowledgeList({ ...queryParams, page: currentPage })
    }
    if (res && res.code === 200) {
      const records = res.data.records || []
      totalRecords = res.data.total || 0
      if (append) {
        knowledgeList.value = [...knowledgeList.value, ...records]
      } else {
        knowledgeList.value = records
      }
      noMore.value = knowledgeList.value.length >= totalRecords
    }
  } catch (error) {
    ElMessage.error('加载知识列表失败')
  } finally {
    initialLoading.value = false
    loadingMore.value = false
    attachObserver()
  }
}

const attachObserver = () => {
  nextTick(() => {
    if (observer && sentinelRef.value) {
      observer.unobserve(sentinelRef.value)
      observer.observe(sentinelRef.value)
    }
  })
}

const setupObserver = () => {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && !loadingMore.value && !noMore.value && !initialLoading.value) {
      currentPage++
      fetchKnowledge(true)
    }
  }, { rootMargin: '200px' })
  attachObserver()
}

const onScroll = () => {
  showBackTop.value = window.scrollY > 500
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const selectCategory = (val) => {
  queryParams.categoryId = val
  resetAndReload()
}

const handleSearch = () => {
  resetAndReload()
}

const resetAndReload = () => {
  currentPage = 1
  noMore.value = false
  knowledgeList.value = []
  fetchKnowledge(false)
  nextTick(() => setupObserver())
}

const handlePublish = async () => {
  if (!publishFormRef.value) return
  await publishFormRef.value.validate(async (valid) => {
    if (valid) {
      publishing.value = true
      try {
        const res = await publishKnowledge(publishForm)
        if (res.code === 200) {
          ElMessage.success('发布成功')
          showPublishDialog.value = false
          Object.assign(publishForm, { title: '', content: '', categoryId: null, coverImage: '', images: [] })
          resetAndReload()
        }
      } catch (error) {
        ElMessage.error('发布失败')
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

const getCategoryName = (id) => ({ 1: '食疗养生', 2: '药膳搭配', 3: '养生方法' }[id] || '养生知识')

const getCategoryTagType = (id) => ({ 1: 'success', 2: '', 3: 'warning' }[id] || 'info')

const stripHtml = (str) => {
  if (!str) return ''
  return str.replace(/<[^>]*>/g, '')
}

onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })
  await fetchKnowledge(false)
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (observer) observer.disconnect()
})
</script>

<style scoped>
.main-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.main-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.main-card :deep(.el-card__body) {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-header h2 {
  margin: 0;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  padding: 0 16px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
  max-width: 360px;
}

.sort-select {
  width: 100px;
}

.category-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.category-chip {
  padding: 5px 14px;
  border-radius: 20px;
  font-size: 13px;
  background: #f5f5f5;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  user-select: none;
}

.category-chip:hover {
  background: #e6f7ff;
  color: #1890ff;
}

.category-chip--active {
  background: #e6f7ff;
  color: #1890ff;
  border-color: #91d5ff;
  font-weight: 600;
}

/* ===== 列表区域 ===== */
.scroll-area {
  padding: 16px;
}

/* ===== 骨架屏 ===== */
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skeleton-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}

.sk-left {
  width: 4px;
  border-radius: 2px;
  flex-shrink: 0;
}

.sk-body {
  flex: 1;
}

.sk-line {
  height: 14px;
  border-radius: 4px;
  margin-bottom: 10px;
}

.sk-line--title {
  width: 55%;
  height: 20px;
  margin-bottom: 14px;
}

.sk-line--short {
  width: 40%;
}

.sk-meta {
  height: 12px;
  width: 35%;
  border-radius: 4px;
  margin-top: 14px;
}

.sk-right {
  width: 120px;
  height: 90px;
  border-radius: 8px;
  flex-shrink: 0;
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

/* ===== 知识卡片 ===== */
.knowledge-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.knowledge-card {
  display: flex;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.2s;
}

.knowledge-card:hover {
  border-color: #d9d9d9;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.card-accent {
  width: 4px;
  flex-shrink: 0;
}

.accent--1 { background: linear-gradient(180deg, #67c23a, #95d475); }
.accent--2 { background: linear-gradient(180deg, #409eff, #79bbff); }
.accent--3 { background: linear-gradient(180deg, #e6a23c, #eebe77); }

.card-body {
  flex: 1;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card-category {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.card-time {
  font-size: 12px;
  color: #bbb;
}

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 10px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-content {
  font-size: 14px;
  color: #666;
  line-height: 1.7;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  margin-top: auto;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #999;
}

.card-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-author {
  color: #555;
  font-weight: 500;
  gap: 6px !important;
}

.doctor-badge {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
  margin-left: 4px;
}

.card-cover {
  width: 160px;
  height: 120px;
  flex-shrink: 0;
  overflow: hidden;
  margin: 14px 14px 14px 0;
  border-radius: 8px;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s;
}

.knowledge-card:hover .card-cover img {
  transform: scale(1.06);
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

/* ===== 回顶部 ===== */
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