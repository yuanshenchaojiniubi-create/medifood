import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { clearPendingRequests } from '@/api/request'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页', public: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true, hideHeader: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', public: true, hideHeader: true }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/knowledge/KnowledgeList.vue'),
    meta: { title: '养生知识' }
  },
  {
    path: '/knowledge/:id',
    name: 'KnowledgeDetail',
    component: () => import('@/views/knowledge/KnowledgeDetail.vue'),
    meta: { title: '知识详情' }
  },
  {
    path: '/knowledge/:id/edit',
    name: 'KnowledgeEdit',
    component: () => import('@/views/knowledge/KnowledgeEdit.vue'),
    meta: { title: '编辑知识' }
  },
  {
    path: '/recipe',
    name: 'Recipe',
    component: () => import('@/views/recipe/RecipeList.vue'),
    meta: { title: '健康食谱' }
  },
  {
    path: '/recipe/:id',
    name: 'RecipeDetail',
    component: () => import('@/views/recipe/RecipeDetail.vue'),
    meta: { title: '食谱详情' }
  },
  {
    path: '/recipe/:id/edit',
    name: 'RecipeEdit',
    component: () => import('@/views/recipe/RecipeEdit.vue'),
    meta: { title: '编辑食谱' }
  },
  {
    path: '/community',
    name: 'Community',
    component: () => import('@/views/community/Community.vue'),
    meta: { title: '社区' }
  },
  {
    path: '/community/post/:id',
    name: 'PostDetail',
    component: () => import('@/views/community/PostDetail.vue'),
    meta: { title: '动态详情' }
  },
  {
    path: '/qa',
    name: 'QA',
    component: () => import('@/views/qa/QAList.vue'),
    meta: { title: '问答交流' }
  },
  {
    path: '/smart-qa',
    name: 'SmartQA',
    component: () => import('@/views/qa/SmartQA.vue'),
    meta: { title: '智能问答' }
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('@/views/message/MessageCenter.vue'),
    meta: { title: '消息中心' }
  },
  {
    path: '/messages/chat/:userId',
    name: 'PrivateChat',
    component: () => import('@/views/message/PrivateChat.vue'),
    meta: { title: '私信' }
  },
  {
    path: '/qa/:id',
    name: 'QADetail',
    component: () => import('@/views/qa/QADetail.vue'),
    meta: { title: '问题详情' }
  },
  {
    path: '/user/:id',
    name: 'UserProfile',
    component: () => import('@/views/user/UserProfile.vue'),
    meta: { title: '个人中心' }
  },
  {
    path: '/user/:id/follow',
    name: 'FollowList',
    component: () => import('@/views/user/FollowList.vue'),
    meta: { title: '关注/粉丝' }
  },
  {
    path: '/user/:id/mutual',
    name: 'MutualFollowList',
    component: () => import('@/views/user/MutualFollowList.vue'),
    meta: { title: '互关列表' }
  },
  {
    path: '/profile/:id',
    name: 'OtherUserProfile',
    component: () => import('@/views/user/OtherUserProfile.vue'),
    meta: { title: '用户主页' }
  },
  {
    path: '/user/settings',
    name: 'UserSettings',
    component: () => import('@/views/user/UserSettings.vue'),
    meta: { title: '个人设置' }
  },
  // ============ 医师功能 ============
  {
    path: '/doctor/workspace',
    name: 'DoctorWorkspace',
    component: () => import('@/views/doctor/DoctorWorkspace.vue'),
    meta: { title: '医师工作台', requiresDoctor: true }
  },
  {
    path: '/doctor/diet-plans',
    name: 'DietPlanList',
    component: () => import('@/views/doctor/DietPlanList.vue'),
    meta: { title: '膳食计划管理', requiresDoctor: true }
  },
  {
    path: '/doctor/diet-plan/create',
    name: 'DietPlanCreate',
    component: () => import('@/views/doctor/DietPlanEdit.vue'),
    meta: { title: '创建膳食计划', requiresDoctor: true }
  },
  {
    path: '/doctor/diet-plan/:id',
    name: 'DietPlanDetail',
    component: () => import('@/views/doctor/DietPlanDetail.vue'),
    meta: { title: '膳食计划详情' }
  },
  {
    path: '/doctor/diet-plan/:id/edit',
    name: 'DietPlanEdit',
    component: () => import('@/views/doctor/DietPlanEdit.vue'),
    meta: { title: '编辑膳食计划', requiresDoctor: true }
  },
  {
    path: '/doctor/consultations',
    name: 'ConsultationList',
    component: () => import('@/views/doctor/ConsultationList.vue'),
    meta: { title: '咨询管理', requiresDoctor: true }
  },
  {
    path: '/doctor/consultation/:id',
    name: 'ConsultationDetail',
    component: () => import('@/views/doctor/ConsultationDetail.vue'),
    meta: { title: '咨询详情' }
  },
  {
    path: '/doctor/my-consultations',
    name: 'MyConsultationList',
    component: () => import('@/views/doctor/MyConsultationList.vue'),
    meta: { title: '我的咨询' }
  },
  {
    path: '/doctor/patients',
    name: 'PatientList',
    component: () => import('@/views/doctor/PatientList.vue'),
    meta: { title: '患者管理', requiresDoctor: true }
  },
  {
    path: '/admin/doctor-certs',
    name: 'AdminDoctorCerts',
    component: () => import('@/views/admin/DoctorCertReview.vue'),
    meta: { title: '医师认证审核', requiresAdmin: true }
  },
  {
    path: '/admin/banners',
    name: 'AdminBanners',
    component: () => import('@/views/admin/BannerManage.vue'),
    meta: { title: '轮播图管理', requiresAdmin: true }
  },
  {
    path: '/admin/content-audit',
    name: 'AdminContentAudit',
    component: () => import('@/views/admin/ContentAuditReview.vue'),
    meta: { title: '内容审核台', requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 访客：仅可浏览首页内容与登录/注册；其它任意功能需登录。
 * 本地有 token 时，进入受保护页面前会先 await 拉取用户信息（含双 token 刷新）。
 */
router.beforeEach(async (to, from, next) => {
  clearPendingRequests()

  if (to.meta && to.meta.title) {
    document.title = `${to.meta.title} - 药食同源养生交流平台`
  } else {
    document.title = '药食同源养生交流平台'
  }

  let userStore
  try {
    userStore = useUserStore()
  } catch (error) {
    console.error('路由守卫错误:', error)
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 登录 / 注册：已登录则回首页
  if (to.name === 'Login' || to.name === 'Register') {
    if (userStore.isLoggedIn) {
      next({ path: '/' })
      return
    }
    next()
    return
  }

  // 首页：未登录也可进；有 token 时由 Home 与 store 负责拉用户信息
  if (to.name === 'Home') {
    next()
    return
  }

  // 其它 meta.public（若以后扩展）仍放行
  if (to.meta && to.meta.public) {
    next()
    return
  }

  // 受保护页面：有 access 时先尝试拉齐用户信息再判断是否已登录
  if (userStore.token && (!userStore.userInfo || userStore.initialAuthPending)) {
    await userStore.fetchUserInfo()
  }

  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再访问该页面')
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta && to.meta.requiresAdmin) {
    try {
      if (!userStore.userInfo) {
        await userStore.fetchUserInfo()
      }
      if (!userStore.isAdmin) {
        next({ path: '/' })
        return
      }
    } catch (error) {
      console.error('路由守卫错误:', error)
      next({ path: '/' })
      return
    }
  }

  if (to.meta && to.meta.requiresDoctor) {
    try {
      if (!userStore.userInfo) {
        await userStore.fetchUserInfo()
      }
      if (!userStore.isDoctor) {
        ElMessage.warning('仅认证医师可访问此功能')
        next({ path: '/' })
        return
      }
    } catch (error) {
      console.error('路由守卫错误:', error)
      next({ path: '/' })
      return
    }
  }

  next()
})

export default router
