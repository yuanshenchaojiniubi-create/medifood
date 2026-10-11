<template>
  <header class="header">
    <div class="header-inner">
      <!-- Logo -->
      <div class="logo" @click="$router.push('/')">
        <div class="logo-icon">
          <el-icon :size="22"><DishDot /></el-icon>
        </div>
        <span class="logo-text">药食同源</span>
      </div>

      <!-- 导航 -->
      <nav class="nav">
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
          <el-icon :size="16"><House /></el-icon>
          <span>首页</span>
        </router-link>
        <router-link to="/knowledge" class="nav-item" :class="{ active: $route.path.startsWith('/knowledge') }">
          <el-icon :size="16"><Document /></el-icon>
          <span>知识</span>
        </router-link>
        <router-link to="/recipe" class="nav-item" :class="{ active: $route.path.startsWith('/recipe') }">
          <el-icon :size="16"><Food /></el-icon>
          <span>食谱</span>
        </router-link>
        <router-link to="/community" class="nav-item" :class="{ active: $route.path.startsWith('/community') }">
          <el-icon :size="16"><ChatLineRound /></el-icon>
          <span>社区</span>
        </router-link>
        <router-link to="/qa" class="nav-item" :class="{ active: $route.path === '/qa' }">
          <el-icon :size="16"><QuestionFilled /></el-icon>
          <span>问答</span>
        </router-link>
        <router-link to="/smart-qa" class="nav-item" :class="{ active: $route.path === '/smart-qa' }">
          <el-icon :size="16"><MagicStick /></el-icon>
          <span>AI问答</span>
        </router-link>
        <router-link to="/messages" class="nav-item" :class="{ active: $route.path.startsWith('/messages') }">
          <el-icon :size="16"><Bell /></el-icon>
          <span>消息</span>
        </router-link>
      </nav>

      <!-- 右侧用户区 -->
      <div class="user-area">
        <NotificationBell class="header-notify" />

        <template v-if="userStore.isLoggedIn">
          <el-dropdown trigger="click" placement="bottom-end">
            <div class="user-trigger">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar" class="user-avatar">
                {{ userStore.userInfo?.nickname?.[0] || 'U' }}
              </el-avatar>
              <span class="user-nickname">
                {{ userStore.userInfo?.nickname || '用户' }}
              </span>
              <el-tag v-if="userStore.userInfo?.isDoctor" type="success" size="small" effect="plain" class="doctor-tag">
                医师
              </el-tag>
              <el-icon :size="12" class="arrow-icon"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goToProfile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item @click="goToSettings">
                  <el-icon><Setting /></el-icon> 账号设置
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isDoctor" @click="$router.push('/doctor/workspace')" divided>
                  <el-icon><Monitor /></el-icon> 医师工作台
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" @click="$router.push('/admin/doctor-certs')" divided>
                  <el-icon><Checked /></el-icon> 医师认证审核
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" @click="$router.push('/admin/banners')">
                  <el-icon><PictureFilled /></el-icon> 轮播图管理
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" @click="$router.push('/admin/content-audit')">
                  <el-icon><Checked /></el-icon> 内容审核台
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" size="small" @click="$router.push('/login')">登录</el-button>
          <el-button size="small" @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  House, Document, Food, ChatLineRound, QuestionFilled,
  User, Setting, SwitchButton, Checked, PictureFilled,
  ArrowDown, DishDot, Bell, MagicStick, Monitor
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const goToProfile = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.fetchUserInfo()
  }
  const id = userStore.userInfo?.id
  if (id == null) {
    ElMessage.warning('无法获取用户信息，请重新登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  router.push(`/user/${id}`)
}

const goToSettings = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.fetchUserInfo()
  }
  if (!userStore.userInfo?.id) {
    ElMessage.warning('无法获取用户信息，请重新登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  router.push('/user/settings')
}

const handleLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: #fff;
  border-bottom: 1px solid #e8eaed;
  backdrop-filter: blur(12px);
  background: rgba(255, 255, 255, 0.96);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 0;
}

/* ===== Logo ===== */
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
  margin-right: 8px;
}

.logo-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #67c23a, #409eff);
  border-radius: 8px;
  color: #fff;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #1a1a1a;
  letter-spacing: 0.5px;
}

/* ===== 导航 ===== */
.nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  justify-content: center;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: #5f6368;
  text-decoration: none;
  transition: all 0.2s ease;
  white-space: nowrap;
  position: relative;
}

.nav-item:hover {
  color: #409eff;
  background: #ecf5ff;
}

.nav-item.active {
  color: #409eff;
  background: #ecf5ff;
  font-weight: 600;
}

/* ===== 用户区 ===== */
.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 8px;
}

.header-notify {
  margin-right: 4px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
  border-radius: 24px;
  transition: background 0.2s;
}

.user-trigger:hover {
  background: #f5f6f8;
}

.user-avatar {
  flex-shrink: 0;
}

.user-nickname {
  font-size: 13px;
  color: #333;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doctor-tag {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
}

.arrow-icon {
  color: #999;
  transition: transform 0.2s;
}

.el-dropdown:hover .arrow-icon {
  transform: rotate(180deg);
}

/* 响应式 */
@media (max-width: 1024px) {
  .nav-item span {
    display: none;
  }
  .nav-item {
    padding: 8px 10px;
  }
}

@media (max-width: 768px) {
  .logo-text {
    display: none;
  }
  .user-nickname {
    display: none;
  }
}
</style>