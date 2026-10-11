<template>
  <PageLayout>
    <template #main>
      <el-card class="main-card">
        <template #header>
          <div class="page-header">
            <h2>健康食谱</h2>
            <el-button
              v-if="userStore.isLoggedIn && userStore.isDoctor"
              type="primary"
              @click="showPublishDialog = true"
            >
              <el-icon><Plus /></el-icon> 发布食谱
            </el-button>
            <el-tooltip
              v-else-if="userStore.isLoggedIn && !userStore.isDoctor"
              content="仅认证医师可发布食谱，请在设置中申请医师认证"
              placement="bottom"
            >
              <el-button type="primary" disabled>发布食谱</el-button>
            </el-tooltip>
          </div>
        </template>

              <div class="filter-bar">
                <div class="search-row">
                  <el-input
                    v-model="searchKeyword"
                    placeholder="搜索食谱..."
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
                <div class="tag-row" v-if="allTags.length > 0">
                  <span class="tag-row-label">标签：</span>
                  <span
                    v-for="tag in allTags"
                    :key="tag.id"
                    class="tag-chip"
                    :class="{ 'tag-chip--active': selectedTagIds.includes(tag.id) }"
                    @click="toggleTag(tag.id)"
                  >
                    {{ tag.tagName }}
                  </span>
                </div>
              </div>

              <div class="scroll-area">
                <div v-if="initialLoading" class="skeleton-grid">
                  <div v-for="i in 6" :key="'sk'+i" class="skeleton-card">
                    <div class="sk-image skeleton-pulse"></div>
                    <div class="sk-card-body">
                      <div class="sk-line sk-line--title skeleton-pulse"></div>
                      <div class="sk-line skeleton-pulse"></div>
                      <div class="sk-line sk-line--short skeleton-pulse"></div>
                      <div class="sk-tags">
                        <span class="sk-tag skeleton-pulse"></span>
                        <span class="sk-tag skeleton-pulse"></span>
                      </div>
                      <div class="sk-meta skeleton-pulse"></div>
                    </div>
                  </div>
                </div>

                <div v-else>
                  <div class="recipe-grid" v-if="recipeList.length > 0">
                    <article
                      v-for="item in recipeList"
                      :key="item.id"
                      class="recipe-card"
                      @click="$router.push(`/recipe/${item.id}`)"
                    >
                      <div class="card-image">
                        <img :src="item.coverImage || defaultCover" :alt="item.title" loading="lazy" />
                        <div class="card-image-overlay">
                          <div class="overlay-tags">
                            <el-tag size="small" v-for="tag in item.tags?.slice(0, 2)" :key="tag" effect="dark" round>
                              {{ tag }}
                            </el-tag>
                          </div>
                        </div>
                      </div>
                      <div class="card-body">
                        <h3 class="card-title">{{ item.title }}</h3>
                        <p class="card-desc">{{ item.description }}</p>
                        <div class="card-tags" v-if="item.tags && item.tags.length > 2">
                          <el-tag size="small" v-for="tag in item.tags?.slice(2, 5)" :key="tag" class="extra-tag">
                            {{ tag }}
                          </el-tag>
                        </div>
                        <div class="card-footer">
                          <div class="card-meta">
                            <span class="meta-item meta-author" v-if="item.nickname">
                              <el-avatar :size="16" :src="item.avatar" />
                              {{ item.nickname }}
                              <el-tag v-if="item.isDoctor" type="success" size="small" effect="plain" class="doctor-badge">医师</el-tag>
                            </span>
                            <span class="meta-item"><LikeIcon :active="item.isLiked" /> {{ item.likeCount }}</span>
                            <span class="meta-item"><el-icon><View /></el-icon> {{ item.viewCount }}</span>
                            <span class="meta-item"><el-icon><ChatDotRound /></el-icon> {{ item.commentCount }}</span>
                          </div>
                        </div>
                      </div>
                    </article>
                  </div>

                  <el-empty v-if="!initialLoading && recipeList.length === 0 && !loadingMore" description="暂无食谱" />

                  <div ref="sentinelRef" class="scroll-sentinel">
                    <div v-if="loadingMore" class="sentinel-loading">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>加载更多...</span>
                    </div>
                    <div v-else-if="noMore && recipeList.length > 0" class="sentinel-end">
                      — 已经到底了 —
                    </div>
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

    <el-dialog v-model="showPublishDialog" title="发布食谱" width="900px">
      <el-form :model="publishForm" :rules="publishRules" ref="publishFormRef" label-width="100px">
        <el-form-item label="食谱名称" prop="title">
          <el-input v-model="publishForm.title" placeholder="请输入食谱名称" />
        </el-form-item>
        <el-form-item label="食谱描述">
          <el-input v-model="publishForm.description" type="textarea" :rows="3" placeholder="请输入食谱描述" />
        </el-form-item>
        <el-form-item label="食材组成" prop="ingredients">
          <div v-for="(ingredient, index) in publishForm.ingredients" :key="index" style="margin-bottom: 10px; display: flex; gap: 10px;">
            <el-input v-model="ingredient.name" placeholder="食材名称" style="flex: 1;" />
            <el-input v-model="ingredient.amount" placeholder="用量" style="width: 150px;" />
            <el-button @click="removeIngredient(index)">删除</el-button>
          </div>
          <el-button @click="addIngredient">添加食材</el-button>
        </el-form-item>
        <el-form-item label="制作步骤" prop="steps">
          <div v-for="(step, index) in publishForm.steps" :key="index" style="margin-bottom: 10px;">
            <el-input v-model="step.description" type="textarea" :rows="2" :placeholder="`步骤 ${index + 1}`" />
            <el-button @click="removeStep(index)" style="margin-top: 5px;">删除步骤</el-button>
          </div>
          <el-button @click="addStep">添加步骤</el-button>
        </el-form-item>
        <el-form-item label="适用人群">
          <el-input v-model="publishForm.suitableFor" placeholder="请输入适用人群" />
        </el-form-item>
        <el-form-item label="功效说明">
          <el-input v-model="publishForm.effects" placeholder="请输入功效说明" />
        </el-form-item>
        <el-form-item label="封面图片">
          <single-upload v-model="publishForm.coverImage" />
        </el-form-item>
        <el-form-item label="更多图片">
          <multi-upload v-model="publishForm.images" />
        </el-form-item>
        <el-form-item label="食谱标签">
          <el-select
            v-model="publishForm.tagIds"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入标签"
            style="width: 100%;"
            :reserve-keyword="false"
            @create="handleCreateTag"
          >
            <el-option v-for="tag in allTags" :key="tag.id" :label="tag.tagName" :value="tag.id" />
          </el-select>
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
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { Search, Plus, View, ChatDotRound, Top, Loading } from '@element-plus/icons-vue'
import LikeIcon from '@/components/icon/LikeIcon.vue'
import { getRecipeList, searchRecipe, publishRecipe, getRecipeTags, createRecipeTag } from '@/api/recipe'
import { ElMessage } from 'element-plus'
import SingleUpload from '@/components/upload/singleUpload.vue'
import MultiUpload from '@/components/upload/multiUpload.vue'

const userStore = useUserStore()

const sentinelRef = ref(null)
const publishFormRef = ref(null)

const recipeList = ref([])
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
const pageSize = 9

const defaultCover = 'data:image/svg+xml,%3Csvg xmlns%3D%22http://www.w3.org/2000/svg%22 viewBox%3D%220 0 400 300%22%3E%3Crect fill%3D%22%23f0f0f0%22 width%3D%22400%22 height%3D%22300%22/%3E%3Ctext fill%3D%22%23c0c4cc%22 font-size%3D%2220%22 text-anchor%3D%22middle%22 x%3D%22200%22 y%3D%22155%22%3E暂无图片%3C/text%3E%3C/svg%3E'

const queryParams = reactive({
  page: 1,
  size: pageSize,
  sort: 'latest'
})

const publishForm = reactive({
  title: '',
  description: '',
  ingredients: [{ name: '', amount: '' }],
  steps: [{ description: '' }],
  suitableFor: '',
  effects: '',
  coverImage: '',
  images: [],
  tagIds: []
})

const allTags = ref([])
const selectedTagIds = ref([])

const publishRules = {
  title: [{ required: true, message: '请输入食谱名称', trigger: 'blur' }],
  ingredients: [{ required: true, message: '请至少添加一个食材', trigger: 'change' }],
  steps: [{ required: true, message: '请至少添加一个步骤', trigger: 'change' }]
}

const fetchRecipes = async (append = false) => {
  if (!append) {
    initialLoading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const params = { ...queryParams, page: currentPage }
    if (selectedTagIds.value.length > 0) {
      params.tagIds = selectedTagIds.value.join(',')
    }
    let res
    if (searchKeyword.value) {
      res = await searchRecipe(searchKeyword.value, currentPage, pageSize)
    } else {
      res = await getRecipeList(params)
    }
    if (res && res.code === 200) {
      const records = res.data.records || []
      totalRecords = res.data.total || 0
      if (append) {
        recipeList.value = [...recipeList.value, ...records]
      } else {
        recipeList.value = records
      }
      noMore.value = recipeList.value.length >= totalRecords
    }
  } catch (error) {
    ElMessage.error('加载食谱列表失败')
  } finally {
    initialLoading.value = false
    loadingMore.value = false
    attachObserver()
  }
}

const loadTags = async () => {
  try {
    const res = await getRecipeTags()
    if (res.code === 200) {
      allTags.value = res.data || []
    }
  } catch (error) {}
}

const toggleTag = (tagId) => {
  const idx = selectedTagIds.value.indexOf(tagId)
  if (idx >= 0) {
    selectedTagIds.value.splice(idx, 1)
  } else {
    selectedTagIds.value.push(tagId)
  }
  resetAndReload()
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
      fetchRecipes(true)
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

const handleSearch = () => {
  resetAndReload()
}

const resetAndReload = () => {
  currentPage = 1
  noMore.value = false
  recipeList.value = []
  fetchRecipes(false)
  nextTick(() => setupObserver())
}

const handleCreateTag = async (tagName) => {
  try {
    const res = await createRecipeTag(tagName)
    if (res.code === 200 && res.data) {
      allTags.value.push(res.data)
      publishForm.tagIds.push(res.data.id)
      ElMessage.success(`标签"${tagName}"创建成功`)
    }
  } catch (error) {
    ElMessage.error('创建标签失败')
  }
}

const addIngredient = () => {
  publishForm.ingredients.push({ name: '', amount: '' })
}

const removeIngredient = (index) => {
  publishForm.ingredients.splice(index, 1)
}

const addStep = () => {
  publishForm.steps.push({ description: '' })
}

const removeStep = (index) => {
  publishForm.steps.splice(index, 1)
}

const handlePublish = async () => {
  if (!publishFormRef.value) return
  await publishFormRef.value.validate(async (valid) => {
    if (valid) {
      publishing.value = true
      try {
        const res = await publishRecipe(publishForm)
        if (res.code === 200) {
          ElMessage.success('发布成功')
          showPublishDialog.value = false
          Object.assign(publishForm, {
            title: '', description: '',
            ingredients: [{ name: '', amount: '' }], steps: [{ description: '' }],
            suitableFor: '', effects: '', coverImage: '', images: [], tagIds: []
          })
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

onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })
  loadTags()
  await fetchRecipes(false)
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

.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-row-label {
  font-size: 13px;
  color: #999;
  flex-shrink: 0;
}

.tag-chip {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  background: #f5f5f5;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  user-select: none;
}

.tag-chip:hover {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-chip--active {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

/* ===== 列表区域 ===== */
.scroll-area {
  padding: 16px;
}

/* ===== 骨架屏 ===== */
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.skeleton-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.sk-image {
  width: 100%;
  height: 180px;
}

.sk-card-body {
  padding: 16px;
}

.sk-line {
  height: 14px;
  border-radius: 4px;
  margin-bottom: 10px;
}

.sk-line--title {
  width: 70%;
  height: 20px;
  margin-bottom: 14px;
}

.sk-line--short {
  width: 50%;
}

.sk-tags {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  margin-bottom: 12px;
}

.sk-tag {
  width: 50px;
  height: 22px;
  border-radius: 12px;
}

.sk-meta {
  height: 12px;
  width: 40%;
  border-radius: 4px;
  margin-top: 14px;
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

/* ===== 食谱卡片 ===== */
.recipe-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.recipe-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #f0f0f0;
  transition: box-shadow 0.25s, transform 0.25s;
}

.recipe-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-4px);
}

.card-image {
  position: relative;
  width: 100%;
  height: 180px;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s;
}

.recipe-card:hover .card-image img {
  transform: scale(1.08);
}

.card-image-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 30px 12px 10px;
  background: linear-gradient(transparent, rgba(0,0,0,0.5));
}

.overlay-tags {
  display: flex;
  gap: 6px;
}

.card-body {
  padding: 14px 16px 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  margin: 0 0 10px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.extra-tag {
  background: #f5f7fa;
  border-color: #e4e7ed;
  color: #909399;
}

.card-footer {
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.card-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #999;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-author {
  color: #555;
  font-weight: 500;
}

.doctor-badge {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
  margin-left: 2px;
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