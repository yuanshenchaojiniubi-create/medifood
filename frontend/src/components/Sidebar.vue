<template>
  <aside class="sidebar">
    <!-- 用户信息卡片（登录后显示） -->
    <div v-if="userStore.isLoggedIn" class="sidebar-card user-card">
      <div class="user-card-bg"></div>
      <div class="user-card-body">
        <el-avatar :size="48" :src="userStore.userInfo?.avatar" class="user-card-avatar">
          {{ userStore.userInfo?.nickname?.[0] || 'U' }}
        </el-avatar>
        <div class="user-card-name">
          {{ userStore.userInfo?.nickname || '用户' }}
        </div>
        <el-tag v-if="userStore.userInfo?.isDoctor" type="success" size="small" effect="plain">认证医师</el-tag>
        <div class="user-card-stats">
          <div class="stat" @click="goToMyFollow">
            <span class="stat-num">{{ userStats.following }}</span>
            <span class="stat-label">关注</span>
          </div>
          <div class="stat" @click="goToMyFollowers">
            <span class="stat-num">{{ userStats.followers }}</span>
            <span class="stat-label">粉丝</span>
          </div>
          <div class="stat">
            <span class="stat-num">{{ userStats.posts }}</span>
            <span class="stat-label">动态</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 热门标签 -->
    <div class="sidebar-card">
      <div class="sidebar-card-header">
        <el-icon :size="16"><Collection /></el-icon>
        <span>热门标签</span>
      </div>
      <div class="sidebar-card-body">
        <div class="tag-cloud">
          <span
            v-for="tag in hotTags"
            :key="tag"
            class="tag"
            :class="getTagClass(tag)"
            @click="handleTagClick(tag)"
          >
            {{ tag }}
          </span>
        </div>
      </div>
    </div>

    <!-- 推荐内容 -->
    <div class="sidebar-card">
      <div class="sidebar-card-header">
        <el-icon :size="16"><TrendCharts /></el-icon>
        <span>热门推荐</span>
      </div>
      <div class="sidebar-card-body">
        <div v-if="recommendations.length === 0" class="sidebar-empty">暂无推荐</div>
        <div
          v-for="(item, idx) in recommendations"
          :key="item.id"
          class="recommend-item"
          @click="goToItem(item)"
        >
          <span class="recommend-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
          <div class="recommend-info">
            <div class="recommend-title">{{ item.title }}</div>
            <div class="recommend-meta">
              <el-icon :size="12"><View /></el-icon> {{ item.viewCount || 0 }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 关于我们 -->
    <div class="sidebar-card sidebar-footer">
      <div class="sidebar-card-body footer-links">
        <a href="#" @click.prevent>关于我们</a>
        <span class="dot">·</span>
        <a href="#" @click.prevent>用户协议</a>
        <span class="dot">·</span>
        <a href="#" @click.prevent>隐私政策</a>
      </div>
      <div class="copyright">© 2025 药食同源</div>
    </div>
  </aside>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Collection, TrendCharts, View } from '@element-plus/icons-vue'
import { getHotKnowledge } from '@/api/knowledge'
import { getHotRecipes } from '@/api/recipe'

const router = useRouter()
const userStore = useUserStore()

const hotTags = ref([
  '食疗', '药膳', '养生', '健康饮食', '季节养生',
  '减肥食谱', '美容养颜', '三高调理', '增强免疫', '中医食疗'
])

const recommendations = ref([])

const userStats = ref({
  following: 0,
  followers: 0,
  posts: 0
})

const getTagClass = (tag) => {
  const colors = ['tag-red', 'tag-orange', 'tag-green', 'tag-blue', 'tag-purple']
  const idx = tag.length % colors.length
  return colors[idx]
}

const handleTagClick = (tag) => {
  router.push({ path: '/knowledge', query: { keyword: tag } })
}

const goToItem = (item) => {
  if (item.type === 'knowledge') {
    router.push(`/knowledge/${item.id}`)
  } else if (item.type === 'recipe') {
    router.push(`/recipe/${item.id}`)
  }
}

const goToMyFollow = () => {
  const id = userStore.userInfo?.id
  if (id) router.push(`/user/${id}/follow`)
}

const goToMyFollowers = () => {
  const id = userStore.userInfo?.id
  if (id) router.push(`/user/${id}/follow?tab=followers`)
}

const loadHotContent = async () => {
  try {
    const [knowRes, recipeRes] = await Promise.allSettled([
      getHotKnowledge(5),
      getHotRecipes(5)
    ])
    const items = []
    if (knowRes.status === 'fulfilled' && knowRes.value?.data) {
      knowRes.value.data.slice(0, 3).forEach(k => {
        items.push({ id: k.id, title: k.title, viewCount: k.viewCount, type: 'knowledge' })
      })
    }
    if (recipeRes.status === 'fulfilled' && recipeRes.value?.data) {
      recipeRes.value.data.slice(0, 3).forEach(r => {
        items.push({ id: r.id, title: r.title, viewCount: r.viewCount, type: 'recipe' })
      })
    }
    recommendations.value = items.sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0)).slice(0, 5)
  } catch {
    /* ignore */
  }
}

onMounted(() => {
  loadHotContent()
})
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ===== 卡片 ===== */
.sidebar-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.sidebar-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 16px 10px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  border-bottom: 1px solid #f5f5f5;
}

.sidebar-card-body {
  padding: 12px 16px;
}

.sidebar-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 16px 0;
}

/* ===== 用户卡片 ===== */
.user-card {
  position: relative;
}

.user-card-bg {
  height: 50px;
  background: linear-gradient(135deg, #e6f7ff, #f0f5ff);
}

.user-card-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 16px 16px;
  margin-top: -24px;
}

.user-card-avatar {
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.user-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-top: 8px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card-stats {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
  width: 100%;
  justify-content: center;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  padding: 0 8px;
}

.stat-num {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}

.stat-label {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

/* ===== 标签云 ===== */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 4px 12px;
  border-radius: 14px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.tag-red    { background: #fff1f0; color: #f5222d; }
.tag-orange  { background: #fff7e6; color: #fa8c16; }
.tag-green   { background: #f6ffed; color: #52c41a; }
.tag-blue    { background: #e6f7ff; color: #1890ff; }
.tag-purple  { background: #f9f0ff; color: #722ed1; }

/* ===== 推荐列表 ===== */
.recommend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #fafafa;
  cursor: pointer;
  transition: background 0.15s;
}

.recommend-item:last-child {
  border-bottom: none;
}

.recommend-item:hover {
  background: #fafafa;
  margin: 0 -16px;
  padding-left: 16px;
  padding-right: 16px;
  border-radius: 6px;
}

.recommend-rank {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #999;
  background: #f5f5f5;
  flex-shrink: 0;
}

.recommend-rank.rank-1 { background: #fff1f0; color: #f5222d; }
.recommend-rank.rank-2 { background: #fff7e6; color: #fa8c16; }
.recommend-rank.rank-3 { background: #fffbe6; color: #fa8c16; }

.recommend-info {
  flex: 1;
  min-width: 0;
}

.recommend-title {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.recommend-meta {
  font-size: 11px;
  color: #bbb;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ===== 底部 ===== */
.sidebar-footer {
  background: transparent;
  border: none;
}

.sidebar-footer .sidebar-card-body {
  padding: 8px 16px;
}

.footer-links {
  display: flex;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
}

.footer-links a {
  color: #999;
  text-decoration: none;
}

.footer-links a:hover {
  color: #409eff;
}

.dot {
  color: #ddd;
}

.copyright {
  text-align: center;
  font-size: 11px;
  color: #ccc;
  padding: 0 16px 12px;
}
</style>