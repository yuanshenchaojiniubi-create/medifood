<template>
  <PageLayout>
    <template #main>
      <!-- 轮播图 -->
      <el-carousel v-if="banners.length > 0" height="320px" :interval="4000" type="card" class="home-carousel">
        <el-carousel-item v-for="item in banners" :key="item.id">
          <img :src="item.image" :alt="item.title" class="banner-image" />
        </el-carousel-item>
      </el-carousel>

      <el-card v-if="seasonalDiet.sourceName" class="seasonal-diet-card" shadow="hover">
        <template #header>
          <div class="seasonal-header">
            <h2>
              <el-icon :size="20"><Sunny /></el-icon>
              {{ seasonalDiet.sourceType === 'TERM' ? seasonalDiet.sourceName + '时节' : seasonalDiet.sourceName + ' 饮食推荐' }}
              <el-tag v-if="seasonalDiet.specialTerm" type="warning" size="large" effect="dark" style="margin-left: 8px">特殊节气</el-tag>
            </h2>
            <span class="seasonal-desc-text">{{ seasonalDiet.sourceDesc }}</span>
          </div>
        </template>

              <div class="date-weather-row" v-if="seasonalDiet.date || seasonalDiet.weather">
                <div class="date-info" v-if="seasonalDiet.date">
                  <el-icon :size="18"><Calendar /></el-icon>
                  <span class="date-text">{{ seasonalDiet.date }}</span>
                </div>
                <div class="weather-info" v-if="seasonalDiet.weather">
                  <span class="city-name" v-if="seasonalDiet.weather.city">
                    <el-icon :size="14"><Location /></el-icon> {{ seasonalDiet.weather.city }}
                  </span>
                  <el-tag type="info" effect="plain" class="weather-tag">
                    🌡 {{ seasonalDiet.weather.temperature }}
                  </el-tag>
                  <el-tag :type="weatherTagType(seasonalDiet.weather.condition)" effect="plain" class="weather-tag">
                    {{ weatherIcon(seasonalDiet.weather.condition) }} {{ seasonalDiet.weather.condition }}
                  </el-tag>
                  <el-tooltip content="点击重新授权位置以获取更精准的本地天气" placement="top" v-if="showReAuthButton">
                    <el-button type="warning" size="small" circle @click="reAuthorize" class="re-auth-btn" :loading="reauthLoading">
                      <el-icon :size="14"><RefreshRight /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <span class="weather-freshness" v-if="freshnessText">
                    <el-tag :type="freshnessTagType" size="small" effect="light">{{ freshnessText }}</el-tag>
                  </span>
                </div>
              </div>

              <div class="seasonal-principle-row">
                <div class="principle-left">
                  <el-icon color="#e6a23c"><DishDot /></el-icon>
                  <strong>饮食原则：</strong>{{ seasonalDiet.dietPrinciple }}
                </div>
                <div class="principle-right">
                  <el-icon color="#67c23a"><Bell /></el-icon>
                  <span class="health-tip-text">{{ seasonalDiet.healthTip }}</span>
                </div>
              </div>

              <el-row :gutter="16" class="meal-row">
                <el-col :span="8">
                  <div class="meal-panel breakfast">
                    <div class="meal-head">
                      <el-icon :size="18"><Sunrise /></el-icon>
                      <span>早餐推荐</span>
                    </div>
                    <div class="meal-body" v-if="seasonalDiet.breakfast && seasonalDiet.breakfast.length > 0">
                      <div class="meal-recipe" v-for="item in seasonalDiet.breakfast" :key="item.id"
                           @click="$router.push(`/recipe/${item.id}`)">
                        <el-image v-if="item.coverImage" :src="item.coverImage" fit="cover" class="meal-thumb">
                          <template #error><div class="thumb-fallback">🍽️</div></template>
                        </el-image>
                        <div v-else class="thumb-fallback">🥣</div>
                        <div class="meal-info">
                          <div class="meal-name">{{ item.title }}</div>
                          <div class="meal-effects" v-if="item.effects">{{ item.effects }}</div>
                        </div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无推荐" :image-size="30" />
                  </div>
                </el-col>

                <el-col :span="8">
                  <div class="meal-panel lunch">
                    <div class="meal-head">
                      <el-icon :size="18"><Sunny /></el-icon>
                      <span>午餐推荐</span>
                    </div>
                    <div class="meal-body" v-if="seasonalDiet.lunch && seasonalDiet.lunch.length > 0">
                      <div class="meal-recipe" v-for="item in seasonalDiet.lunch" :key="item.id"
                           @click="$router.push(`/recipe/${item.id}`)">
                        <el-image v-if="item.coverImage" :src="item.coverImage" fit="cover" class="meal-thumb">
                          <template #error><div class="thumb-fallback">🍲</div></template>
                        </el-image>
                        <div v-else class="thumb-fallback">🍲</div>
                        <div class="meal-info">
                          <div class="meal-name">{{ item.title }}</div>
                          <div class="meal-effects" v-if="item.effects">{{ item.effects }}</div>
                        </div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无推荐" :image-size="30" />
                  </div>
                </el-col>

                <el-col :span="8">
                  <div class="meal-panel dinner">
                    <div class="meal-head">
                      <el-icon :size="18"><Moon /></el-icon>
                      <span>晚餐推荐</span>
                    </div>
                    <div class="meal-body" v-if="seasonalDiet.dinner && seasonalDiet.dinner.length > 0">
                      <div class="meal-recipe" v-for="item in seasonalDiet.dinner" :key="item.id"
                           @click="$router.push(`/recipe/${item.id}`)">
                        <el-image v-if="item.coverImage" :src="item.coverImage" fit="cover" class="meal-thumb">
                          <template #error><div class="thumb-fallback">🥘</div></template>
                        </el-image>
                        <div v-else class="thumb-fallback">🥘</div>
                        <div class="meal-info">
                          <div class="meal-name">{{ item.title }}</div>
                          <div class="meal-effects" v-if="item.effects">{{ item.effects }}</div>
                        </div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无推荐" :image-size="30" />
                  </div>
                </el-col>
              </el-row>
            </el-card>

            <!-- 排行榜区域 -->
            <el-row :gutter="20" style="margin-top: 20px;">
              <!-- 热门知识 -->
              <el-col :span="12">
                <el-card class="section-card">
                  <template #header>
                    <div class="section-header">
                      <h3><el-icon><Trophy /></el-icon> 热门养生知识榜</h3>
                      <el-button type="text" @click="$router.push('/knowledge')">更多 →</el-button>
                    </div>
                  </template>
                  <div class="ranking-list" v-loading="knowledgeLoading">
                    <el-empty v-if="hotKnowledge.length === 0 && !knowledgeLoading" description="暂无热门知识" />
                    <div class="ranking-item" v-for="(item, index) in hotKnowledge" :key="item.id" @click="$router.push(`/knowledge/${item.id}`)">
                      <div class="rank-number" :class="'rank-' + (index + 1)">{{ index + 1 }}</div>
                      <div class="ranking-content">
                        <div class="ranking-title">{{ item.title }}</div>
                        <div class="ranking-meta">
                          <span><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
                          <span><LikeIcon :active="item.isLiked" /> {{ item.likeCount || 0 }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </el-card>
              </el-col>

              <!-- 热门食谱 -->
              <el-col :span="12">
                <el-card class="section-card">
                  <template #header>
                    <div class="section-header">
                      <h3><el-icon><Trophy /></el-icon> 热门健康食谱榜</h3>
                      <el-button type="text" @click="$router.push('/recipe')">更多 →</el-button>
                    </div>
                  </template>
                  <div class="ranking-list" v-loading="recipeLoading">
                    <el-empty v-if="hotRecipes.length === 0 && !recipeLoading" description="暂无热门食谱" />
                    <div class="ranking-item" v-for="(item, index) in hotRecipes" :key="item.id" @click="$router.push(`/recipe/${item.id}`)">
                      <div class="rank-number" :class="'rank-' + (index + 1)">{{ index + 1 }}</div>
                      <div class="ranking-content">
                        <div class="ranking-title">{{ item.title }}</div>
                        <div class="ranking-meta">
                          <span><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
                          <span><LikeIcon :active="item.isLiked" /> {{ item.likeCount || 0 }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { View, ChatDotRound, Trophy, Sunny, Sunrise, Moon, DishDot, Bell, Calendar, Location, RefreshRight } from '@element-plus/icons-vue'
import { getHotKnowledge } from '@/api/knowledge'
import { getHotRecipes, getSeasonalDiet } from '@/api/recipe'
import { getBanners } from '@/api/banner'
import { ElMessage, ElMessageBox } from 'element-plus'
import LikeIcon from '../components/icon/LikeIcon.vue'

const banners = ref([])

const hotKnowledge = ref([])
const hotRecipes = ref([])
const seasonalDiet = ref({})
const knowledgeLoading = ref(false)
const recipeLoading = ref(false)

const loadHotKnowledge = async () => {
  knowledgeLoading.value = true
  try {
    // 调用专用热门知识接口，无需登录，返回 List<KnowledgeDTO>
    const res = await getHotKnowledge(10)
    if (res && res.code === 200) {
      hotKnowledge.value = Array.isArray(res.data) ? res.data : []
    } else {
      hotKnowledge.value = []
    }
  } catch (error) {
    console.error('加载热门知识失败:', error)
    hotKnowledge.value = []
  } finally {
    knowledgeLoading.value = false
  }
}

const loadHotRecipes = async () => {
  recipeLoading.value = true
  try {
    // 调用专用热门食谱接口，无需登录，返回 List<RecipeDTO>
    const res = await getHotRecipes(10)
    if (res && res.code === 200) {
      hotRecipes.value = Array.isArray(res.data) ? res.data : []
    } else {
      hotRecipes.value = []
    }
  } catch (error) {
    console.error('加载热门食谱失败:', error)
    hotRecipes.value = []
  } finally {
    recipeLoading.value = false
  }
}

const userStore = useUserStore()

const hasLocationPermission = ref(null) // null=未询问, true=已授权, false=已拒绝
const showReAuthButton = ref(false) // 是否显示重新授权按钮
const reauthLoading = ref(false) // 重新授权按钮加载状态
const weatherFetchTime = ref(null) // 天气数据获取时间

// 获取今天的日期字符串（YYYY-MM-DD），用于判断是否当天已授权
const getToday = () => new Date().toISOString().slice(0, 10)

// 获取本地存储的定位权限状态（带当天日期过期判断）
const getSavedPermission = () => {
  try {
    const saved = localStorage.getItem('locationPermission')
    if (!saved) return null
    const data = JSON.parse(saved)
    // 检查是否是今天的记录，过期则清除
    if (data.date === getToday()) {
      return data
    }
    // 过期记录，清除
    localStorage.removeItem('locationPermission')
  } catch (e) {
    console.warn('📍 [定位] 读取本地存储失败:', e)
  }
  return null
}

// 保存定位权限状态到本地存储（带当天日期）
const savePermission = (status, coords = null) => {
  const data = {
    date: getToday(),
    status: status, // 'granted' | 'denied'
    coords: coords // { lat, lng }
  }
  localStorage.setItem('locationPermission', JSON.stringify(data))
  console.log('📍 [定位] 权限状态已保存:', data)
}

const loadSeasonalDiet = async () => {
  console.log('🏠 [首页] 开始加载节气饮食...')
  const coords = await tryGetLocation()
  try {
    const params = coords ? { lat: coords.lat, lng: coords.lng } : {}
    console.log('🏠 [首页] 请求节气饮食API，参数:', JSON.stringify(params))
    const res = await getSeasonalDiet(params)
    console.log('🏠 [首页] 节气饮食API返回: code=', res?.code, 'data=', res?.data ? {
      date: res.data.date,
      sourceName: res.data.sourceName,
      weather: res.data.weather
    } : null)
    if (res && res.code === 200) {
      seasonalDiet.value = res.data
      weatherFetchTime.value = new Date()
    }
  } catch (error) {
    console.error('🏠 [首页] 节气饮食加载失败:', error)
  }
}

// 重新授权按钮点击处理
const reAuthorize = async () => {
  reauthLoading.value = true
  console.log('📍 [定位] 用户点击重新授权按钮...')
  try {
    // 清除旧记录，重新走完整授权流程
    localStorage.removeItem('locationPermission')
    showReAuthButton.value = false
    const coords = await tryGetLocation(true) // force=true 强制弹窗
    if (coords) {
      // 重新获取天气数据
      const params = { lat: coords.lat, lng: coords.lng }
      console.log('📍 [定位] 重新授权成功，使用新坐标请求天气:', params)
      const res = await getSeasonalDiet(params)
      if (res && res.code === 200) {
        seasonalDiet.value = res.data
        weatherFetchTime.value = new Date()
        ElMessage.success('已获取当前位置天气！')
      }
    } else {
      ElMessage.warning('未能获取位置，请检查浏览器定位权限设置')
    }
  } catch (error) {
    console.error('📍 [定位] 重新授权失败:', error)
    ElMessage.error('重新授权失败，请稍后再试')
  } finally {
    reauthLoading.value = false
  }
}

const tryGetLocation = async (force = false) => {
  console.log('📍 [定位] 开始获取用户位置... force=', force)

  // 浏览器不支持 Geolocation API，直接走 IP 定位
  if (!navigator.geolocation) {
    console.warn('📍 [定位] 浏览器不支持 Geolocation API，将使用 IP 定位')
    return null
  }
  console.log('📍 [定位] 浏览器支持 Geolocation API')

  // 检查当前页面协议（HTTP 下 Geolocation 会被静默拒绝）
  const protocol = window.location.protocol
  console.log('📍 [定位] 当前页面协议:', protocol)

  // 第一步：检查本地存储中今天的权限记录（非强制模式）
  if (!force) {
    const saved = getSavedPermission()
    if (saved) {
      if (saved.status === 'granted' && saved.coords) {
        console.log('📍 [定位] ✅ 今天已授权，直接使用已保存坐标:', saved.coords)
        hasLocationPermission.value = true
        showReAuthButton.value = false
        return saved.coords
      }
      if (saved.status === 'denied') {
        console.log('📍 [定位] 今天用户已拒绝授权，使用 IP 定位，显示重新授权按钮')
        hasLocationPermission.value = false
        showReAuthButton.value = true
        return null
      }
    }
  }

  // 第二步：今天还没有记录（或强制重新授权），弹出友好询问弹窗
  console.log('📍 [定位] 今天还未授权，弹出授权询问弹窗...')
  try {
    await ElMessageBox.confirm(
      '获取您的位置信息，可以为您推荐更适合当地天气的个性化食谱，是否同意？',
      '位置授权',
      {
        confirmButtonText: '同意',
        cancelButtonText: '暂不',
        type: 'info'
      }
    )
    console.log('📍 [定位] 用户点击了"同意"')
  } catch {
    // 用户点击了"暂不"，后端会用 IP 定位兜底
    hasLocationPermission.value = false
    showReAuthButton.value = true
    savePermission('denied')
    console.log('📍 [定位] 用户点击了"暂不"，显示重新授权按钮，使用 IP 定位')
    return null
  }

  // 第三步：调用浏览器 Geolocation API
  console.log('📍 [定位] 调用 navigator.geolocation.getCurrentPosition...')
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        hasLocationPermission.value = true
        showReAuthButton.value = false
        const coords = {
          lat: position.coords.latitude,
          lng: position.coords.longitude
        }
        savePermission('granted', coords)
        console.log('📍 [定位] ✅ 浏览器定位成功! lat=%s, lng=%s', coords.lat, coords.lng)
        resolve(coords)
      },
      (error) => {
        hasLocationPermission.value = false
        showReAuthButton.value = true
        savePermission('denied')
        const errorMap = {
          1: 'PERMISSION_DENIED(用户拒绝授权)',
          2: 'POSITION_UNAVAILABLE(位置不可用)',
          3: 'TIMEOUT(超时)'
        }
        console.error('📍 [定位] ❌ 浏览器定位失败: code=%s, reason=%s, message=%s',
          error.code, errorMap[error.code] || '未知', error.message)
        resolve(null)
      },
      { timeout: 8000, enableHighAccuracy: false }
    )
  })
}

const loadBanners = async () => {
  try {
    const res = await getBanners()
    if (res && res.code === 200) {
      banners.value = (res.data || []).map(b => ({
        id: b.id,
        title: b.title,
        image: b.imageUrl,
        linkUrl: b.linkUrl
      }))
    }
  } catch (e) {
    console.error('加载轮播图失败:', e)
  }
}

const weatherIcon = (condition) => {
  const map = { '晴热': '☀️', '多云': '⛅', '阴天': '☁️', '雨天': '🌧️', '寒冷': '❄️', '干燥': '🍂' }
  return map[condition] || '🌤️'
}

const weatherTagType = (condition) => {
  const map = { '晴热': 'danger', '多云': '', '阴天': 'info', '雨天': '', '寒冷': 'primary', '干燥': 'warning' }
  return map[condition] || ''
}

onMounted(async () => {
  // 进入首页：若本地仍有 token（登录态），先拉取当前用户信息供顶栏等展示
  if (userStore.token) {
    await userStore.fetchUserInfo()
  }
  loadHotKnowledge()
  loadHotRecipes()
  loadSeasonalDiet()
  loadBanners()
})
</script>

<style scoped>
.home-carousel {
  margin-bottom: 20px;
}

.banner-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}

.section-card {
  border-radius: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-header h3 {
  margin: 0;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-header h3 .el-icon {
  color: #f5222d;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ranking-item {
  display: flex;
  align-items: center;
  padding: 12px 15px;
  border-radius: 8px;
  background: #fff;
  transition: all 0.3s;
  cursor: pointer;
  border: 1px solid #ebeef5;
}

.ranking-item:hover {
  background: #fdfdfd;
  transform: translateX(5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border-color: #fca5a5;
}

.rank-number {
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  border-radius: 50%;
  font-weight: bold;
  font-size: 16px;
  color: #999;
  background: #f0f2f5;
  margin-right: 15px;
  flex-shrink: 0;
}

.rank-number.rank-1 { background: #fff1f0; color: #f5222d; }
.rank-number.rank-2 { background: #fff7e6; color: #fa541c; }
.rank-number.rank-3 { background: #fffbe6; color: #fa8c16; }

.ranking-content {
  flex: 1;
  min-width: 0;
}

.ranking-title {
  font-size: 15px;
  font-weight: bold;
  color: #333;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ranking-meta {
  display: flex;
  gap: 15px;
  font-size: 13px;
  color: #999;
}

.seasonal-diet-card {
  margin-bottom: 20px;
  border: 2px solid #f0e6d3;
  border-radius: 12px;
}
.seasonal-diet-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #fdf6ec, #fff8e1);
  border-bottom: 1px solid #f0e6d3;
  padding: 14px 20px;
}
.seasonal-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5d4037;
  margin: 0 0 4px 0;
  font-size: 18px;
}
.seasonal-desc-text {
  color: #8d6e63;
  font-style: italic;
  font-size: 13px;
}
.seasonal-principle-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 14px;
  background: #fdf6ec;
  border-left: 4px solid #e6a23c;
  border-radius: 0 8px 8px 0;
  font-size: 14px;
  color: #5d4037;
}
.principle-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.principle-right {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #67c23a;
  font-size: 13px;
}
.health-tip-text {
  color: #67c23a;
}
.date-weather-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 14px;
  background: linear-gradient(135deg, #e8f4fd, #f0f7ff);
  border-radius: 10px;
  border: 1px solid #d6e8f7;
}
.date-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #409eff;
}
.date-text {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}
.weather-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.city-name {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 13px;
}
.weather-tag {
  font-size: 13px;
}
.re-auth-btn {
  margin-left: 4px;
  transition: all 0.3s;
}
.re-auth-btn:hover {
  transform: scale(1.1);
}
.weather-freshness {
  margin-left: 4px;
}
.weather-badge {
  display: flex;
  gap: 6px;
  margin-left: auto;
}
.meal-row {
  padding: 0 4px;
}
.meal-panel {
  border-radius: 10px;
  padding: 12px;
  height: 100%;
  min-height: 160px;
}
.meal-panel.breakfast {
  background: linear-gradient(135deg, #fff9e6, #fff3cd);
}
.meal-panel.lunch {
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
}
.meal-panel.dinner {
  background: linear-gradient(135deg, #e3f2fd, #bbdefb);
}
.meal-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
  font-size: 15px;
  color: #333;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #ddd;
}
.meal-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.meal-recipe {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.meal-recipe:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  background: white;
}
.meal-thumb {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  flex-shrink: 0;
}
.thumb-fallback {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 8px;
  font-size: 22px;
  flex-shrink: 0;
}
.meal-info {
  flex: 1;
  min-width: 0;
}
.meal-info .meal-name {
  font-weight: 600;
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meal-info .meal-effects {
  font-size: 12px;
  color: #888;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
