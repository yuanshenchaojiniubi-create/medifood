<template>
  <div class="register-container">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <div class="register-wrapper">
      <!-- 品牌 Logo -->
      <div class="brand-area">
        <div class="brand-icon">
          <el-icon :size="36"><DishDot /></el-icon>
        </div>
        <h1 class="brand-name">药食同源</h1>
        <p class="brand-slogan">加入我们，开启健康养生之旅</p>
      </div>

      <el-card class="register-card" shadow="always">
        <template #header>
          <div class="card-header">
            <h2>用户注册</h2>
            <p>填写以下信息创建您的账号</p>
          </div>
        </template>
        <el-form :model="registerForm" :rules="rules" ref="registerFormRef" label-width="0">
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码（6-20个字符）"
              size="large"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              size="large"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input
              v-model="registerForm.nickname"
              placeholder="请输入昵称（可选）"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              placeholder="请输入邮箱（可选）"
              size="large"
              :prefix-icon="Message"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleRegister" :loading="loading" class="submit-btn" size="large">
              注 册
            </el-button>
          </el-form-item>
          <el-form-item>
            <div class="footer-links">
              <span class="text-muted">已有账号？</span>
              <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
            </div>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { User, Lock, Message, DishDot } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在 3 到 50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const success = await userStore.userRegister({
          username: registerForm.username,
          password: registerForm.password,
          nickname: registerForm.nickname || undefined,
          email: registerForm.email || undefined
        })
        if (success) {
          ElMessage.success('注册成功，请登录')
          router.push('/login')
        } else {
          ElMessage.error('注册失败，请重试')
        }
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
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
  left: -100px;
  animation: float 20s ease-in-out infinite;
}

.circle-2 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #e6a23c, #f56c6c);
  bottom: -80px;
  right: -80px;
  animation: float 18s ease-in-out infinite reverse;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  top: 40%;
  right: 15%;
  animation: float 15s ease-in-out infinite 5s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

.register-wrapper {
  width: 100%;
  max-width: 480px;
  padding: 20px;
  position: relative;
  z-index: 1;
}

/* 品牌区域 */
.brand-area {
  text-align: center;
  margin-bottom: 24px;
}

.brand-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #67c23a, #409eff);
  border-radius: 16px;
  color: #fff;
  box-shadow: 0 6px 20px rgba(103, 194, 58, 0.3);
}

.brand-name {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 6px;
  letter-spacing: 2px;
}

.brand-slogan {
  font-size: 14px;
  color: #888;
  margin: 0;
}

/* 注册卡片 */
.register-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
}

.register-card :deep(.el-card__header) {
  padding: 20px 28px 12px;
  border-bottom: 1px solid #f5f5f5;
}

.register-card :deep(.el-card__body) {
  padding: 20px 28px 12px;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 6px 0;
  color: #1a1a1a;
  font-size: 22px;
  font-weight: 600;
}

.card-header p {
  margin: 0;
  color: #999;
  font-size: 13px;
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