import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getUserInfo } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)
  /** 有 token 且尚未完成首次拉取用户信息时为 true，避免首屏「先显示未登录再变已登录」闪烁 */
  const initialAuthPending = ref(!!token.value)

  /**
   * 真正视为已登录：有 access token 且已成功拿到用户信息（或首屏校验尚未结束）
   * 避免 localStorage 残留过期 token 却显示已登录、却无 userId 导致 /user/undefined
   */
  const isLoggedIn = computed(() => {
    if (!token.value) return false
    if (initialAuthPending.value) return true
    return !!userInfo.value
  })

  // 登录
  const userLogin = async (loginData) => {
    try {
      const response = await login(loginData)
      if (response.code === 200 && response.data?.accessToken) {
        token.value = response.data.accessToken
        localStorage.setItem('token', token.value)
        initialAuthPending.value = true
        const ok = await fetchUserInfo()
        if (!ok) {
          logout()
          return false
        }
        return true
      }
      return false
    } catch (error) {
      console.error('登录失败:', error)
      return false
    }
  }

  // 注册
  const userRegister = async (registerData) => {
    try {
      const response = await register(registerData)
      return response.code === 200
    } catch (error) {
      console.error('注册失败:', error)
      return false
    }
  }

  /**
   * 拉取当前用户信息
   * @returns {Promise<boolean>}
   */
  const fetchUserInfo = async () => {
    if (!token.value) {
      initialAuthPending.value = false
      return false
    }
    try {
      const response = await getUserInfo()
      if (response.code === 200 && response.data != null) {
        userInfo.value = response.data
        return true
      }
      userInfo.value = null
      logout()
      return false
    } catch (error) {
      console.error('获取用户信息失败:', error)
      userInfo.value = null
      // 无 response 视为网络异常：不强制清 token，由调用方决定（如登录后失败会 logout）
      if (!error.response) {
        return false
      }
      logout()
      return false
    } finally {
      initialAuthPending.value = false
    }
  }

  /** 供 request 拦截器在刷新 token 后同步状态（不触发 initialAuthPending） */
  const setToken = (t) => {
    token.value = t || ''
    if (t) {
      localStorage.setItem('token', t)
    } else {
      localStorage.removeItem('token')
      userInfo.value = null
    }
  }

  const setUser = (u) => {
    userInfo.value = u
  }

  // 退出登录
  const logout = () => {
    token.value = ''
    userInfo.value = null
    initialAuthPending.value = false
    localStorage.removeItem('token')
  }

  if (token.value) {
    fetchUserInfo()
  } else {
    initialAuthPending.value = false
  }

  const isDoctor = computed(() => !!userInfo.value?.isDoctor)

  const isAdmin = computed(() => !!userInfo.value?.isAdmin)

  return {
    token,
    userInfo,
    initialAuthPending,
    isLoggedIn,
    isDoctor,
    isAdmin,
    userLogin,
    userRegister,
    fetchUserInfo,
    setToken,
    setUser,
    logout
  }
})
