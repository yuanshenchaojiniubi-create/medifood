<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="follow-card" v-loading="loading">
          <template #header>
            <div class="card-header">
              <el-button @click="$router.back()" text>
                <el-icon><ArrowLeft /></el-icon>
                返回
              </el-button>
              <span class="header-title">互关列表</span>
            </div>
          </template>

          <div v-if="list.length === 0 && !loading" class="empty-state">
            <el-empty description="暂无互关好友" />
          </div>

          <div v-else class="follow-list">
            <div v-for="item in list" :key="item.userId" class="follow-item">
              <div class="user-info" @click="goToUser(item.userId)">
                <el-avatar :size="48" :src="item.avatar">
                  {{ item.nickname?.[0] || item.username?.[0] }}
                </el-avatar>
                <div class="user-text">
                  <div class="nickname">
                    {{ item.nickname || item.username }}
                    <el-tag type="success" size="small" effect="light" class="mutual-tag">互关</el-tag>
                  </div>
                  <div class="username">@{{ item.username }}</div>
                </div>
              </div>
              <div class="follow-action">
                <el-button
                  v-if="!isSelf(item.userId)"
                  :type="item.isFollowed ? 'default' : 'primary'"
                  size="small"
                  @click="handleToggleFollow(item)"
                >
                  {{ item.isFollowed ? '已关注' : '关注' }}
                </el-button>
                <el-button
                  v-if="!isSelf(item.userId)"
                  type="primary"
                  size="small"
                  plain
                  @click="goToChat(item.userId)"
                >
                  <el-icon><ChatDotRound /></el-icon>
                  私信
                </el-button>
              </div>
            </div>
          </div>

          <div v-if="total > pageSize" class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              @current-change="loadData"
            />
          </div>
        </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { getMutualFollows, followUser, unfollowUser } from '@/api/community'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const userId = computed(() => route.params.id)

const isSelf = (uid) => {
  const loginId = userStore.userInfo?.id
  if (loginId == null || uid == null) return false
  return String(loginId) === String(uid)
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getMutualFollows(userId.value, currentPage.value, pageSize.value)
    if (res.code === 200) {
      list.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleToggleFollow = async (item) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  try {
    if (item.isFollowed) {
      await unfollowUser(item.userId)
      item.isFollowed = false
      ElMessage.success('取消关注成功')
    } else {
      await followUser(item.userId)
      item.isFollowed = true
      ElMessage.success('关注成功')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const goToUser = (uid) => {
  router.push(`/user/${uid}`)
}

const goToChat = (uid) => {
  router.push(`/messages/chat/${uid}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.follow-list-page {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.empty-state {
  padding: 40px 0;
}

.follow-list {
  display: flex;
  flex-direction: column;
}

.follow-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.follow-item:last-child {
  border-bottom: none;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  flex: 1;
}

.user-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nickname {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.mutual-tag {
  font-size: 11px;
}

.username {
  font-size: 13px;
  color: #909399;
}

.follow-action {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>