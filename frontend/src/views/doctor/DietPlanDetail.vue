<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <el-button @click="$router.push('/doctor/diet-plans')" text>
              <el-icon><ArrowLeft /></el-icon> 返回列表
            </el-button>
            <div>
              <el-button
                v-if="isOwner"
                type="warning"
                @click="$router.push(`/doctor/diet-plan/${plan.id}/edit`)"
              >
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
            </div>
          </div>
        </template>

        <div v-if="plan" class="plan-detail">
          <!-- 标题 -->
          <h1 class="plan-title">{{ plan.title }}</h1>

          <!-- 元信息 -->
          <div class="plan-meta">
            <div class="meta-left">
              <span class="meta-item">
                <el-icon><View /></el-icon> {{ plan.viewCount || 0 }} 次浏览
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon> {{ formatTime(plan.createTime) }}
              </span>
            </div>
          </div>

          <el-divider />

          <!-- 医师信息 -->
          <div class="user-info-card">
            <div class="user-info-label">主治医师</div>
            <div class="user-info-content">
              <el-avatar :size="48" :src="plan.doctorAvatar" />
              <div class="user-info-text">
                <div class="user-info-name">
                  {{ plan.doctorName || '医师' }}
                  <el-tag type="success" size="small" effect="plain">认证医师</el-tag>
                </div>
                <div class="user-info-detail" v-if="plan.doctorHospital || plan.doctorTitle">
                  {{ plan.doctorHospital || '' }} {{ plan.doctorTitle || '' }}
                </div>
              </div>
            </div>
          </div>

          <!-- 患者信息 -->
          <div class="user-info-card" v-if="plan.patientName">
            <div class="user-info-label">适用患者</div>
            <div class="user-info-content">
              <el-avatar :size="40" :src="plan.patientAvatar" />
              <div class="user-info-text">
                <div class="user-info-name">{{ plan.patientName }}</div>
              </div>
            </div>
          </div>

          <el-divider />

          <!-- 计划描述 -->
          <div class="plan-section" v-if="plan.description">
            <h3>📝 计划描述</h3>
            <p class="plan-text">{{ plan.description }}</p>
          </div>

          <!-- 针对症状 -->
          <div class="plan-section" v-if="plan.targetSymptoms">
            <h3>🎯 针对症状</h3>
            <el-tag v-for="(symptom, i) in plan.targetSymptoms.split(/[,，、]/)" :key="i" class="symptom-tag" type="info">
              {{ symptom.trim() }}
            </el-tag>
          </div>

          <!-- 建议天数 -->
          <div class="plan-section" v-if="plan.durationDays">
            <h3>📅 建议执行天数</h3>
            <p class="plan-text">{{ plan.durationDays }} 天</p>
          </div>

          <!-- 详细内容 -->
          <div class="plan-section">
            <h3>📋 膳食计划详情</h3>
            <div class="plan-content" v-html="formatContent(plan.content)"></div>
          </div>

          <!-- 注意事项 -->
          <div class="plan-section" v-if="plan.notes">
            <h3>⚠️ 注意事项</h3>
            <div class="plan-notes">{{ plan.notes }}</div>
          </div>

          <el-divider />

          <!-- 更新时间 -->
          <div class="plan-footer" v-if="plan.updateTime">
            <span class="update-time">最后更新：{{ formatTime(plan.updateTime) }}</span>
          </div>
        </div>

        <el-empty v-else description="膳食计划不存在" />
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { getDietPlanDetail } from '@/api/doctor'
import { ArrowLeft, Edit, View, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const plan = ref(null)

const isOwner = computed(() => {
  return userStore.userInfo?.id && plan.value?.doctorId === userStore.userInfo.id
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getDietPlanDetail(route.params.id)
    if (res.code === 200) {
      plan.value = res.data
    } else {
      ElMessage.error('膳食计划不存在')
    }
  } catch (error) {
    ElMessage.error('加载膳食计划失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const formatContent = (content) => {
  if (!content) return ''
  // XSS 防护：换行转换后先消毒再交给 v-html 渲染
  return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plan-detail {
  max-width: 900px;
}

.plan-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 16px 0;
  color: #1a1a1a;
}

.plan-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #909399;
  font-size: 14px;
}

.meta-left {
  display: flex;
  gap: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-info-card {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  margin: 16px 0;
}

.user-info-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.user-info-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info-text {
  flex: 1;
}

.user-info-name {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info-detail {
  font-size: 14px;
  color: #606266;
  margin-top: 4px;
}

.plan-section {
  margin: 24px 0;
}

.plan-section h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: #333;
}

.plan-text {
  font-size: 15px;
  line-height: 1.8;
  color: #555;
  margin: 0;
}

.symptom-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.plan-content {
  font-size: 15px;
  line-height: 1.8;
  color: #555;
  background: #f8f9fa;
  padding: 20px;
  border-radius: 12px;
  white-space: pre-wrap;
}

.plan-notes {
  font-size: 14px;
  line-height: 1.8;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 16px;
  border-radius: 12px;
  border-left: 4px solid #e6a23c;
}

.plan-footer {
  text-align: right;
}

.update-time {
  font-size: 13px;
  color: #c0c4cc;
}
</style>