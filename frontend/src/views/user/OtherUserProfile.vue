<template>
  <PageLayout>
    <template #main>
      <el-card class="profile-card" v-loading="loading">
        <el-row class="profile-layout" gutter="20">
          <el-col :xs="24" :sm="24" :md="8" :lg="6" class="profile-left">
            <div class="profile-sidebar">
              <div class="sidebar-top">
                <div class="avatar-wrap">
                  <el-avatar :size="120" :src="userInfo?.avatar">
                    {{ userInfo?.nickname?.[0] || userInfo?.username?.[0] }}
                  </el-avatar>
                </div>

                <div class="sidebar-name">
                  <h3>{{ userInfo?.nickname || userInfo?.username }}</h3>
                  <div class="meta-line">
                    <el-tag v-if="userInfo?.isDoctor" type="success" effect="light">认证医师</el-tag>
                    <el-icon v-if="userInfo?.gender === 1">男</el-icon>
                    <el-icon v-else-if="userInfo?.gender === 0">女</el-icon>
                  </div>

                  <div v-if="userInfo?.isDoctor" class="doctor-info-card">
                    <div class="doctor-info-item" v-if="userInfo?.hospital">
                      <span class="doctor-info-label">🏥 执业医院</span>
                      <span class="doctor-info-value">{{ userInfo.hospital }}</span>
                    </div>
                    <div class="doctor-info-item" v-if="userInfo?.department">
                      <span class="doctor-info-label">📋 科室</span>
                      <span class="doctor-info-value">{{ userInfo.department }}</span>
                    </div>
                    <div class="doctor-info-item" v-if="userInfo?.title">
                      <span class="doctor-info-label">🎖️ 职称</span>
                      <span class="doctor-info-value">{{ userInfo.title }}</span>
                    </div>
                    <div class="doctor-info-item" v-if="userInfo?.practiceYears">
                      <span class="doctor-info-label">📅 从业年限</span>
                      <span class="doctor-info-value">{{ userInfo.practiceYears }} 年</span>
                    </div>
                    <div class="doctor-info-item" v-if="userInfo?.specialties">
                      <span class="doctor-info-label">💡 擅长领域</span>
                      <span class="doctor-info-value">{{ userInfo.specialties }}</span>
                    </div>
                  </div>

                  <p class="bio" v-if="userInfo?.bio">{{ userInfo.bio }}</p>
                </div>
              </div>

              <div class="sidebar-stats">
                <div class="stat-item" @click="goToFollowList('following')" style="cursor: pointer;">
                  <div class="num">{{ followingCount }}</div>
                  <div class="label">关注</div>
                </div>
                <div class="stat-item" @click="goToFollowList('followers')" style="cursor: pointer;">
                  <div class="num">{{ followersCount }}</div>
                  <div class="label">粉丝</div>
                </div>
                <div class="stat-item" @click="goToMutualList" style="cursor: pointer;">
                  <div class="num">{{ mutualCount }}</div>
                  <div class="label">互关</div>
                </div>
              </div>

              <div class="sidebar-actions">
                <el-button :type="isFollowing ? 'default' : 'primary'" @click="handleFollow">
                  {{ isFollowing ? '已关注' : '关注' }}
                </el-button>
                <el-button
                  v-if="userInfo?.isDoctor"
                  type="success"
                  @click="handleConsult"
                >
                  <el-icon><ChatLineRound /></el-icon> 发起咨询
                </el-button>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="24" :md="16" :lg="18" class="profile-right">
            <el-tabs :model-value="selectedTab" @tab-change="onTabChange" class="profile-tabs">
              <el-tab-pane label="TA发布的" name="published" />
              <el-tab-pane label="TA喜欢的" name="liked" />
              <el-tab-pane label="TA收藏的" name="collected" />
            </el-tabs>

            <div class="module-content-wrapper">
              <div class="sub-sidebar">
                <div
                  v-for="tab in availableSubTabs"
                  :key="tab.value"
                  class="sub-nav-item"
                  :class="{ active: subTab === tab.value }"
                  @click="handleSubTabClick(tab.value)"
                >
                  <span>{{ tab.label }}</span>
                </div>
              </div>

              <div class="module-list" v-loading="loadingItems">
                <el-card v-if="items.length === 0" class="empty-card" shadow="never">
                  <el-empty description="暂无数据" :image-size="100" />
                </el-card>
                <div v-else>
                  <div v-for="item in items" :key="item.id" class="list-item">
                    <el-card shadow="hover" class="item-card" @click="goToDetail(item)" style="cursor: pointer; margin-bottom: 12px;">
                      <div class="item-container" style="display: flex; gap: 15px;">
                        <div v-if="item.cover" class="item-cover" style="flex-shrink: 0;">
                          <el-image :src="item.cover" style="width: 120px; height: 90px; object-fit: cover; border-radius: 4px;" fit="cover" />
                        </div>
                        <div class="item-content" style="flex: 1; overflow: hidden;">
                          <div class="item-header">
                            <h4 class="item-title" style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin: 0; font-size: 16px;">{{ item.title }}</h4>
                            <div class="item-meta">{{ item.date }}</div>
                          </div>
                          <div class="item-body" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-top: 8px; color: #666; font-size: 14px;">
                            {{ item.summary }}
                          </div>
                        </div>
                      </div>
                    </el-card>
                  </div>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { getUserInfoById } from '@/api/user'
import { followUser, unfollowUser, getUserPosts, getUserLikedPosts, getUserCollectedPosts } from '@/api/community'
import { getCollectedKnowledge, getUserPublishedKnowledge, getUserLikedKnowledge } from '@/api/knowledge'
import { getUserPublishedRecipes, getUserLikedRecipes, getUserCollectedRecipes } from '@/api/recipe'
import { getUserQuestions, getUserLikedQuestions } from '@/api/qa'
import { ElMessage } from 'element-plus'
import { ChatLineRound } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const userInfo = ref(null)
const isFollowing = ref(false)
const followingCount = ref(0)
const followersCount = ref(0)
const mutualCount = ref(0)

const selectedTab = ref('published')
const subTab = ref('knowledge')
const items = ref([])
const loadingItems = ref(false)

const allSubTabs = [
  { label: '知识', value: 'knowledge' },
  { label: '食谱', value: 'recipe' },
  { label: '动态', value: 'post' },
  { label: '问答', value: 'question' }
]

const availableSubTabs = computed(() => {
  if (selectedTab.value === 'collected') {
    return allSubTabs.filter(tab => tab.value === 'knowledge' || tab.value === 'recipe')
  }
  return allSubTabs
})

const loadUserInfo = async () => {
  loading.value = true
  try {
    const currentUserId = userStore.userInfo?.id || null
    const res = await getUserInfoById(route.params.id, currentUserId)
    if (res.code === 200) {
      userInfo.value = res.data
      followingCount.value = res.data.followingCount ?? 0
      followersCount.value = res.data.followersCount ?? 0
      mutualCount.value = res.data.mutualCount ?? 0
      isFollowing.value = !!res.data.isFollowing
    }
  } catch (error) {
    ElMessage.error('加载用户信息失败')
  } finally {
    loading.value = false
  }
}

const handleFollow = async () => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  try {
    if (isFollowing.value) {
      await unfollowUser(route.params.id)
      isFollowing.value = false
      followersCount.value = Math.max(0, followersCount.value - 1)
      ElMessage.success('取消关注成功')
    } else {
      await followUser(route.params.id)
      isFollowing.value = true
      followersCount.value = followersCount.value + 1
      ElMessage.success('关注成功')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleConsult = () => {
  router.push({
    path: '/doctor/my-consultations',
    query: { action: 'create', doctorId: userInfo.value?.id, doctorName: userInfo.value?.nickname }
  })
}

const goToFollowList = (type) => {
  router.push(`/user/${route.params.id}/follow?type=${type}`)
}

const goToMutualList = () => {
  router.push(`/user/${route.params.id}/mutual`)
}

let currentRequestId = 0
const loadModuleData = async () => {
  const requestId = ++currentRequestId
  loadingItems.value = true
  items.value = []
  try {
    let res = null
    const uid = route.params.id

    const apiConfig = {
      published: {
        knowledge: getUserPublishedKnowledge,
        recipe: getUserPublishedRecipes,
        post: getUserPosts,
        question: getUserQuestions
      },
      liked: {
        knowledge: getUserLikedKnowledge,
        recipe: getUserLikedRecipes,
        post: getUserLikedPosts,
        question: getUserLikedQuestions
      },
      collected: {
        knowledge: getCollectedKnowledge,
        recipe: getUserCollectedRecipes
      }
    }

    const currentApi = apiConfig[selectedTab.value]?.[subTab.value]
    if (currentApi) res = await currentApi(uid)
    if (requestId !== currentRequestId) return

    if (res && res.code === 200) {
      items.value = (res.data.records || []).map(item => {
        const type = subTab.value
        let title = '', summary = '', cover = ''

        if (type === 'knowledge') {
          title = item.title
          summary = item.content ? (item.content.length > 100 ? item.content.substring(0, 100) + '...' : item.content) : ''
          cover = item.coverImage
        } else if (type === 'recipe') {
          title = item.title || item.name
          summary = item.description || ''
          cover = item.coverImage
        } else if (type === 'post') {
          const content = item.content || ''
          title = content.length > 20 ? content.substring(0, 20) + '...' : (content || '无内容')
          summary = content
          cover = item.images && item.images.length > 0 ? item.images[0] : ''
        } else if (type === 'question') {
          title = item.title
          summary = item.content ? (item.content.length > 100 ? item.content.substring(0, 100) + '...' : item.content) : ''
          cover = item.images && item.images.length > 0 ? item.images[0] : ''
        }

        return { id: item.id, title, summary, date: formatDateTime(item.createTime), type, cover }
      })
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('加载数据失败')
  } finally {
    loadingItems.value = false
  }
}

const formatDateTime = (timeStr) => {
  if (!timeStr) return ''
  return new Date(timeStr).toLocaleString()
}

const onTabChange = (tab) => {
  selectedTab.value = tab.name || tab
  const isValid = availableSubTabs.value.some(t => t.value === subTab.value)
  if (!isValid) subTab.value = availableSubTabs.value[0].value
  loadModuleData()
}

const handleSubTabClick = (tab) => { subTab.value = tab; loadModuleData() }

const goToDetail = (item) => {
  if (item.type === 'knowledge') router.push(`/knowledge/${item.id}`)
  else if (item.type === 'recipe') router.push(`/recipe/${item.id}`)
  else if (item.type === 'post') router.push(`/community/post/${item.id}`)
  else if (item.type === 'question') router.push(`/qa/${item.id}`)
}

watch(() => route.params.id, (newId) => {
  if (newId) { loadUserInfo(); loadModuleData() }
})

onMounted(() => { loadUserInfo(); loadModuleData() })
</script>

<style scoped>
.profile-card { border-radius: 12px; border: 1px solid #f0f0f0; }
.profile-layout { align-items: flex-start; }
.profile-left .profile-sidebar { background: #fff; padding: 20px; border-radius: 8px; }
.sidebar-top { display: flex; flex-direction: column; gap: 8px; align-items: center; margin-bottom: 12px; }
.sidebar-name h3 { margin: 0; font-size: 18px; color: #333; text-align: center; }
.meta-line { display: flex; gap: 8px; align-items: center; margin-top: 6px; color: #999; justify-content: center; }
.bio { color: #666; margin-top: 10px; text-align: center; }
.sidebar-stats { display: flex; gap: 20px; margin-top: 16px; margin-bottom: 12px; justify-content: center; }
.stat-item { text-align: center; }
.stat-item .num { font-weight: 600; font-size: 16px; }
.stat-item .label { color: #999; font-size: 12px; }
.sidebar-actions { margin-top: 10px; display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }

/* 医师专业信息卡片 */
.doctor-info-card {
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 8px;
  padding: 12px;
  margin-top: 12px;
  width: 100%;
}

.doctor-info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
}

.doctor-info-item:last-child {
  margin-bottom: 0;
}

.doctor-info-label {
  font-size: 12px;
  color: #67c23a;
  font-weight: 500;
}

.doctor-info-value {
  font-size: 13px;
  color: #333;
  line-height: 1.5;
}
.profile-tabs { background: #fff; padding: 10px 20px 0; border-radius: 6px 6px 0 0; border-bottom: 1px solid #e4e7ed; }
.profile-right .module-content-wrapper { display: flex; gap: 16px; margin-top: 0; background: #fff; padding: 20px; border-radius: 0 0 6px 6px; min-height: 400px; }
.sub-sidebar { width: 100px; flex-shrink: 0; display: flex; flex-direction: column; gap: 4px; }
.sub-nav-item { padding: 8px 12px; border-radius: 4px; cursor: pointer; font-size: 14px; color: #606266; transition: all 0.2s; }
.sub-nav-item:hover { background: #ecf5ff; color: #409eff; }
.sub-nav-item.active { background: #409eff; color: #fff; }
.module-list { flex: 1; min-width: 0; }
.item-card { margin-bottom: 12px; transition: all 0.3s; }
.item-card:hover { transform: translateY(-2px); }
.item-header { display: flex; justify-content: space-between; align-items: center; }
.item-title { margin: 0; font-size: 16px; }
.item-meta { color: #999; font-size: 12px; }
.item-body { color: #666; margin-top: 8px; }
.avatar-wrap { display: flex; align-items: center; gap: 12px; }
</style>