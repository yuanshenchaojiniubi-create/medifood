import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 10000
})

let isRefreshing = false
let refreshSubscribers = []

function subscribeTokenRefresh(cb) {
  refreshSubscribers.push(cb)
}

function onRefreshed(token) {
  refreshSubscribers.forEach(cb => {
    try {
      cb(token)
    } catch (e) {
      console.warn('refresh subscriber error', e)
    }
  })
  refreshSubscribers = []
}

/** 刷新失败时通知队列中的请求结束，避免永久挂起 */
function onRefreshFailed() {
  const subs = [...refreshSubscribers]
  refreshSubscribers = []
  subs.forEach(cb => {
    try {
      cb(null)
    } catch (e) {
      /* ignore */
    }
  })
}

async function refreshTokenRequest() {
  const base = request?.defaults?.baseURL || ''
  const refreshUrl = base.replace(/\/$/, '') + '/user/refresh'
  const resp = await axios.post(refreshUrl, null, { withCredentials: true, timeout: 10000 })
  const data = resp.data || resp
  return data?.data?.accessToken || data?.accessToken || data?.token
}

async function fetchUserInfoAfterRefresh(token) {
  const base = request?.defaults?.baseURL || ''
  const url = base.replace(/\/$/, '') + '/user/info'
  try {
    const resp = await axios.get(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      withCredentials: true,
      timeout: 10000
    })
    const data = resp.data || resp
    return data?.data || data
  } catch (e) {
    return null
  }
}

const pendingControllers = new Set()

export function clearPendingRequests() {
  for (const controller of pendingControllers) {
    controller.abort()
  }
  pendingControllers.clear()
}

async function applyNewTokenAndRetry(originalConfig, newToken) {
  const userStore = useUserStore()
  if (typeof userStore.setToken === 'function') {
    userStore.setToken(newToken)
  } else {
    userStore.token = newToken
  }
  try {
    const userInfo = await fetchUserInfoAfterRefresh(newToken)
    if (userInfo) {
      if (typeof userStore.setUser === 'function') {
        userStore.setUser(userInfo)
      } else {
        userStore.userInfo = userInfo
      }
    }
  } catch (e) {
    /* ignore */
  }
  onRefreshed(newToken)
  originalConfig._retry = true
  originalConfig.headers = originalConfig.headers || {}
  originalConfig.headers.Authorization = `Bearer ${newToken}`
  return request(originalConfig)
}

function queueRetryWhileRefreshing(originalConfig) {
  return new Promise((resolve, reject) => {
    subscribeTokenRefresh((token) => {
      if (!token) {
        reject(new Error('登录已过期'))
        return
      }
      try {
        originalConfig._retry = true
        originalConfig.headers = originalConfig.headers || {}
        originalConfig.headers.Authorization = `Bearer ${token}`
        resolve(request(originalConfig))
      } catch (e) {
        reject(e)
      }
    })
  })
}

async function handle401Refresh(originalConfig, isHttpStatus401) {
  if (originalConfig._retry) {
    const userStore = useUserStore()
    userStore.logout && userStore.logout()
    if (router.currentRoute.value.path !== '/login') router.push('/login')
    return Promise.reject(new Error(isHttpStatus401 ? '未授权' : '未授权'))
  }

  if (!isRefreshing) {
    isRefreshing = true
    try {
      const newToken = await refreshTokenRequest()
      if (newToken) {
        return await applyNewTokenAndRetry(originalConfig, newToken)
      }
      throw new Error('刷新未返回新 token')
    } catch (err) {
      const userStore = useUserStore()
      userStore.logout && userStore.logout()
      onRefreshFailed()
      if (router.currentRoute.value.path !== '/login') router.push('/login')
      ElMessage.warning('登录已过期，请重新登录')
      return Promise.reject(err)
    } finally {
      isRefreshing = false
    }
  }
  return queueRetryWhileRefreshing(originalConfig)
}

request.interceptors.request.use(
  config => {
    const controller = new AbortController()
    config.signal = controller.signal
    config.__controller = controller
    pendingControllers.add(controller)

    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  async response => {
    if (response.config && response.config.__controller) {
      pendingControllers.delete(response.config.__controller)
    }

    const res = response.data
    const originalConfig = response.config || {}

    if (res && res.code === 401) {
      return handle401Refresh(originalConfig, false)
    }

    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  async error => {
    if (error.config && error.config.__controller) {
      pendingControllers.delete(error.config.__controller)
    }

    if (axios.isCancel(error)) {
      console.log('请求已取消:', error.message)
      return new Promise(() => {})
    }

    if (error.response) {
      const status = error.response.status
      const originalConfig = error.config || {}

      if (status === 401) {
        return handle401Refresh(originalConfig, true)
      }

      if (status >= 500) {
        ElMessage.error('服务器错误，请稍后重试')
      } else if (status === 404) {
        console.warn('请求的资源不存在:', error.config.url)
      } else {
        ElMessage.error(error.message || '网络错误')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (error.message === 'Network Error') {
      console.warn('网络连接失败，可能是后端服务未启动')
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default request
