<template>
  <PageLayout>
    <template #main>
      <el-card class="profile-card" v-loading="loading">
        <!-- 修改为左右布局 -->
        <el-row class="profile-layout" gutter="20">
          <!-- 左侧：个人信息侧栏 -->
          <el-col :xs="24" :sm="24" :md="8" :lg="6" class="profile-left">
            <div class="profile-sidebar">

               
                <div class="sidebar-top">
                  <!-- 可点击的头像区域：点击头像或按钮可以修改头像（仅本人可修改） -->
                  <div class="avatar-wrap" @click="onAvatarClick" role="button">
                    <el-avatar
                      :size="120"
                      :src="avatarPreview || userInfo?.avatar"
                    >
                      {{ userInfo?.nickname?.[0] || userInfo?.username?.[0] }}
                    </el-avatar>
                    <div v-if="isSelf" class="avatar-actions">
                      <el-button size="mini" type="primary" @click.stop="openAvatarDialog">修改头像</el-button>
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

                

                <div class="sidebar-actions">
                  <el-button
                    v-if="userStore.userInfo?.id === userInfo?.id"
                    type="primary"
                    plain
                    @click="$router.push('/user/settings')"
                  >
                    编辑资料
                  </el-button>

                  <el-button
                    v-else
                    :type="isFollowing ? 'default' : 'primary'"
                    @click="handleFollow"
                  >
                    {{ isFollowing ? '已关注' : '关注' }}
                  </el-button>
                </div>

                <!-- 可扩展更多侧栏信息 -->
              </div>
            </el-col>

            <!-- 右侧：模块区 -->
            <el-col :xs="24" :sm="24" :md="16" :lg="18" class="profile-right">
              <el-tabs :model-value="selectedTab" @tab-change="onTabChange" class="profile-tabs">
                <el-tab-pane :label="isSelf ? '我发布的' : 'TA发布的'" name="published" />
                <el-tab-pane :label="isSelf ? '我喜欢的' : 'TA喜欢的'" name="liked" />
                <el-tab-pane :label="isSelf ? '我收藏的' : 'TA收藏的'" name="collected" />
              </el-tabs>

              <div class="module-content-wrapper">
                <!-- 左侧子导航：动态显示子标签 -->
                <div class="sub-sidebar">
                  <div 
                    v-for="tab in availableSubTabs"
                    :key="tab.value"
                    class="sub-nav-item" 
                    :class="{ active: subTab === tab.value }" 
                    @click="handleSubTabClick(tab.value)"
                  >
                    <el-icon><component :is="tab.icon" /></el-icon>
                    <span>{{ tab.label }}</span>
                  </div>
                </div>

                <!-- 右侧列表内容 -->
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
                                  <div style="display: flex; align-items: center; gap: 10px;">
                                    <div class="item-meta">{{ item.date }}</div>
                                    <el-button 
                                      v-if="isSelf && selectedTab === 'published'" 
                                      type="primary" 
                                      size="small" 
                                      plain 
                                      @click.stop="handleEditItem(item)"
                                    >编辑</el-button>
                                    <el-button 
                                      v-if="isSelf && selectedTab === 'published'" 
                                      type="danger" 
                                      size="small" 
                                      plain 
                                      @click.stop="handleDeleteItem(item)"
                                    >删除</el-button>
                                  </div>
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

        <!-- 新增：修改头像的弹窗 -->
        <el-dialog title="修改头像" v-model="avatarDialogVisible" width="420px">
          <div style="min-height:120px;">
            <single-upload v-model="newAvatar" />
          </div>
          <template #footer>
            <el-button @click="avatarDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="avatarSaving" @click="saveAvatar" :disabled="!newAvatar">保存</el-button>
          </template>
        </el-dialog>
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import SingleUpload from '@/components/upload/singleUpload.vue'
import { getUserInfoById ,uploadAvatar} from '@/api/user'
import { followUser, unfollowUser, getUserPosts, getUserLikedPosts, getUserCollectedPosts, deletePost } from '@/api/community'
import { getCollectedKnowledge, getUserPublishedKnowledge, getUserLikedKnowledge, deleteKnowledge } from '@/api/knowledge'
import { getUserPublishedRecipes, getUserLikedRecipes, getUserCollectedRecipes, deleteRecipe } from '@/api/recipe'
import { getUserQuestions, getUserLikedQuestions, deleteQuestion } from '@/api/qa'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Reading, Food, ChatDotRound, QuestionFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const userInfo = ref(null)
const isFollowing = ref(false)
const followingCount = ref(0)
const followersCount = ref(0)
const mutualCount = ref(0)

// 新增：右侧模块相关状态
const selectedTab = ref('published')
const subTab = ref('knowledge') // 新增子模块状态
const items = ref([])
const loadingItems = ref(false)

// 新增：头像修改相关状态
const avatarDialogVisible = ref(false)
const newAvatar = ref('')        // 单次上传返回的新头像地址
const avatarSaving = ref(false)  // 保存头像时的 loading
const avatarPreview = ref('')    // 上传后可视预览（优先显示）

// 子模块配置：定义每个子标签的图标和文字
const allSubTabs = [
  { label: '知识', value: 'knowledge', icon: Reading },
  { label: '食谱', value: 'recipe', icon: Food },
  { label: '动态', value: 'post', icon: ChatDotRound },
  { label: '问答', value: 'question', icon: QuestionFilled }
]

// 动态子模块列表：根据主模块（发布/喜欢/收藏）过滤子模块
const availableSubTabs = computed(() => {
  // 如果是“我收藏的”，则过滤掉“动态”和“问答”
  if (selectedTab.value === 'collected') {
    return allSubTabs.filter(tab => tab.value === 'knowledge' || tab.value === 'recipe')
  }
  // 发布和喜欢模块显示所有子项
  return allSubTabs
})

const isSelf = computed(() => {
  // 将两端 id 都转为字符串再比较，避免后端返回 number/string 导致比较失败
  const loginId = userStore.userInfo?.id
  const profileId = userInfo.value?.id
  if (loginId == null || profileId == null) return false
  return String(loginId) === String(profileId)
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
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
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

const goToFollowList = (type) => {
  router.push(`/user/${route.params.id}/follow?type=${type}`)
}

const goToMutualList = () => {
  router.push(`/user/${route.params.id}/mutual`)
}

// 加载模块数据
let currentRequestId = 0
const loadModuleData = async () => {
  const requestId = ++currentRequestId
  loadingItems.value = true
  items.value = []
  try {
    let res = null
    const uid = route.params.id
    
    // 建立 API 映射表，提高可读性并确保调用正确
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
    console.log(selectedTab.value)
    console.log(subTab.value)
    if (currentApi) {
      res = await currentApi(uid)
    }

    // 检查是否是当前最新的请求（竞态处理）
    if (requestId !== currentRequestId) return

    if (res && res.code === 200) {
      // 适配不同类型的数据
      items.value = res.data.records.map(item => {
        const type = subTab.value // 'knowledge', 'recipe', 'post', 'question'
        
        let title = ''
        let summary = ''
        let cover = ''
        
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

        return {
          id: item.id,
          title: title,
          summary: summary,
          date: formatDateTime(item.createTime),
          type: type,
          cover: cover
        }
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

const onTabChange = (name) => {
  // 手动更新选中的主标签
  selectedTab.value = name
  
  // 切换大标签时，检查当前子标签是否在可用列表中
  // 如果不在（比如从“发布/问答”切换到“收藏”），则自动切换到第一个可用的子标签（知识）
  const isValid = availableSubTabs.value.some(tab => tab.value === subTab.value)
  if (!isValid) {
    subTab.value = availableSubTabs.value[0].value
  }
  loadModuleData()
}

const handleSubTabClick = (tab) => {
  subTab.value = tab
  loadModuleData()
}

const handleDeleteItem = (item) => {
  ElMessageBox.confirm(
    `确定要删除该${subTab.value === 'knowledge' ? '知识' : subTab.value === 'recipe' ? '食谱' : subTab.value === 'post' ? '动态' : '问题'}吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      let res = null
      if (subTab.value === 'knowledge') res = await deleteKnowledge(item.id)
      else if (subTab.value === 'recipe') res = await deleteRecipe(item.id)
      else if (subTab.value === 'post') res = await deletePost(item.id)
      else if (subTab.value === 'question') res = await deleteQuestion(item.id)
      
      if (res && res.code === 200) {
        ElMessage.success('删除成功')
        loadModuleData()
      } else {
        ElMessage.error(res?.message || '删除失败')
      }
    } catch (error) {
      console.error('删除失败', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const handleEditItem = (item) => {
  if (item.type === 'knowledge') {
    router.push(`/knowledge/${item.id}/edit`)
  } else if (item.type === 'recipe') {
    router.push(`/recipe/${item.id}/edit`)
  }
}

const goToDetail = (item) => {
  if (item.type === 'knowledge') {
    router.push(`/knowledge/${item.id}`)
  } else if (item.type === 'recipe') {
    router.push(`/recipe/${item.id}`)
  } else if (item.type === 'post') {
    router.push(`/community/post/${item.id}`)
  } else if (item.type === 'question') {
    router.push(`/qa/${item.id}`)
  }
}

// 点击头像：仅本人可直接打开编辑对话框
const onAvatarClick = () => {
  if (isSelf.value) {
    openAvatarDialog()
  }
}

// 打开对话框并初始化为当前头像
const openAvatarDialog = () => {
  newAvatar.value = userInfo.value?.avatar || ''
  avatarPreview.value = newAvatar.value
  avatarDialogVisible.value = true
}

// 从 singleUpload 组件接收上传后返回的图片地址
const onAvatarSelected = (val) => {
  newAvatar.value = val
  avatarPreview.value = val
}

// 保存头像到后端，并更新页面与 store（示例接口：POST /user/avatar）
const saveAvatar = async () => {
  if (!newAvatar.value) {
    ElMessage.warning('请选择头像后保存')
    return
  }
  avatarSaving.value = true
  try {
    // 根据后端实际接口调整 URL 与方法（此处示例使用 POST /user/avatar）
    const res = await uploadAvatar(newAvatar.value )
    // response 经过 request 拦截器会返回 res（期望 res.code === 200）
    if (res && res.code === 200) {
      // 更新本页面的数据
      if (userInfo.value) userInfo.value.avatar = newAvatar.value
      // 更新用户 store 中的头像（如果是当前登录用户）
      if (userStore.userInfo && userStore.userInfo.id === userInfo.value.id) {
        userStore.userInfo.avatar = newAvatar.value
      }
      ElMessage.success('头像更新成功')
      avatarDialogVisible.value = false
    } else {
      ElMessage.error(res.message || '更新头像失败')
    }
  } catch (err) {
    console.error('更新头像失败', err)
    ElMessage.error('更新头像失败')
  } finally {
    avatarSaving.value = false
  }
}

// 监听路由变化，刷新数据
watch(() => route.params.id, (newId) => {
  if (newId) {
    loadUserInfo()
    loadModuleData()
  }
})

onMounted(() => {
  loadUserInfo()
  loadModuleData(selectedTab.value)
})
</script>

<style scoped>
/* 主卡片 */
.profile-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

/* 新增布局样式 */
.profile-layout {
  align-items: flex-start;
}

/* 左侧侧栏样式 */
.profile-left .profile-sidebar {
  background: #fff;
  padding: 20px;
  border-radius: 6px;
}
.sidebar-top {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.sidebar-name h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
  text-align: center;
}
.meta-line {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 6px;
  color: #999;
  justify-content: center;
}
.bio {
  color: #666;
  margin-top: 8px;
  text-align: center;
}
.sidebar-stats {
  display: flex;
  gap: 20px;
  margin-top: 16px;
  margin-bottom: 12px;
  justify-content: center;
  width: 100%;
}
.stat-item .num {
  font-weight: 600;
  font-size: 16px;
}
.stat-item .label {
  color: #999;
  font-size: 12px;
}

/* 操作按钮 */
.sidebar-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  justify-content: center;
  width: 100%;
  flex-wrap: wrap;
}

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

/* 右侧模块区样式 */
.profile-tabs {
  background-color: #fff;
  padding: 10px 20px 0;
  border-radius: 6px 6px 0 0;
  border-bottom: 1px solid #e4e7ed;
}

.module-content-wrapper {
  display: flex;
  margin-top: 0;
  gap: 20px;
  background-color: #fff;
  padding: 20px;
  border-radius: 0 0 6px 6px;
  min-height: 400px;
}

.sub-sidebar {
  width: 120px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.sub-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 15px;
  cursor: pointer;
  color: #606266;
  transition: all 0.3s;
  border-radius: 4px;
  font-size: 14px;
}

.sub-nav-item:hover {
  background-color: #f5f7fa;
  color: #409eff;
}

.sub-nav-item.active {
  background-color: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}

.profile-right .module-list {
  flex: 1;
  min-width: 0;
}

.item-card {
  margin-bottom: 12px;
  transition: all 0.3s;
}

.item-card:hover {
  transform: translateY(-2px);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.item-title {
  margin: 0;
  font-size: 16px;
}
.item-meta {
  color: #999;
  font-size: 12px;
}
.item-body {
  color: #666;
  margin-top: 8px;
}

/* 头像区域样式 */
.avatar-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
.avatar-actions {
  margin-left: 8px;
}
</style>
