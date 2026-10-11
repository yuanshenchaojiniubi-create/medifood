<template>
  <div class="login-container">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <div class="login-wrapper">
      <!-- 品牌 Logo -->
      <div class="brand-area">
        <div class="brand-icon">
          <el-icon :size="36"><DishDot /></el-icon>
        </div>
        <h1 class="brand-name">药食同源</h1>
        <p class="brand-slogan">用最简单的食材，做最懂你的健康管家</p>
      </div>

      <el-card class="login-card" shadow="always">
        <template #header>
          <div class="card-header">
            <h2>用户登录</h2>
            <p>欢迎回到药食同源养生交流平台</p>
          </div>
        </template>
        <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-width="0">
          <el-form-item prop="account">
            <el-input
              v-model="loginForm.account"
              placeholder="用户名 / 邮箱 / 手机号"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleLogin" :loading="loading" class="submit-btn" size="large">
              登 录
            </el-button>
          </el-form-item>
          <el-form-item>
            <div class="footer-links">
              <span class="text-muted">还没有账号？</span>
              <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
            </div>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { User, Lock, DishDot } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  account: '',
  password: ''
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const success = await userStore.userLogin(loginForm)
        if (success) {
          ElMessage.success('登录成功')
          const redirect = route.query.redirect || '/'
          router.push(redirect)
        } else {
          ElMessage.error('登录失败，请检查账号密码')
        }
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f4e8 0%, #d4eaf7 30%, #e8e0f0 60%, #f5e6d3 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 背景装饰圆 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #67c23a, #409eff);
  top: -100px;
  right: -100px;
  animation: float 20s ease-in-out infinite;
}

.circle-2 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #e6a23c, #f56c6c);
  bottom: -80px;
  left: -80px;
  animation: float 18s ease-in-out infinite reverse;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: float 15s ease-in-out infinite 5s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

.login-wrapper {
  width: 100%;
  max-width: 440px;
  padding: 20px;
  position: relative;
  z-index: 1;
}

/* 品牌区域 */
.brand-area {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #67c23a, #409eff);
  border-radius: 18px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(103, 194, 58, 0.3);
}

.brand-name {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 8px;
  letter-spacing: 2px;
}

.brand-slogan {
  font-size: 14px;
  color: #888;
  margin: 0;
}

/* 登录卡片 */
.login-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
}

.login-card :deep(.el-card__header) {
  padding: 24px 28px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.login-card :deep(.el-card__body) {
  padding: 24px 28px 16px;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 8px 0;
  color: #1a1a1a;
  font-size: 24px;
  font-weight: 600;
}

.card-header p {
  margin: 0;
  color: #999;
  font-size: 14px;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #67c23a, #409eff);
  border: none;
}

.submit-btn:hover {
  opacity: 0.9;
}

/* 底部链接 */
.footer-links {
  text-align: center;
  width: 100%;
}

.text-muted {
  color: #999;
  font-size: 14px;
}
</style>