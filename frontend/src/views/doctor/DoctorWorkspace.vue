<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="workspace-header">
            <h2>🏥 医师工作台</h2>
            <span class="welcome-text">欢迎回来，{{ userStore.userInfo?.nickname || '医师' }}</span>
          </div>
        </template>

        <!-- 统计卡片 -->
        <el-row :gutter="20" class="stats-row">
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-number">{{ data?.todayPlanCount || 0 }}</div>
              <div class="stat-label">今日新增计划</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-number">{{ data?.totalPlanCount || 0 }}</div>
              <div class="stat-label">累计膳食计划</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card stat-pending">
              <div class="stat-number">{{ data?.pendingConsultationCount || 0 }}</div>
              <div class="stat-label">待回复咨询</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-number">{{ data?.patientCount || 0 }}</div>
              <div class="stat-label">患者总数</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 快捷操作 -->
        <el-card class="quick-actions">
          <el-button type="primary" @click="$router.push('/doctor/diet-plan/create')">
            <el-icon><Plus /></el-icon> 创建膳食计划
          </el-button>
          <el-button @click="$router.push('/doctor/diet-plans')">
            <el-icon><Document /></el-icon> 计划管理
          </el-button>
          <el-button @click="$router.push('/doctor/consultations')">
            <el-icon><ChatLineRound /></el-icon> 咨询管理
          </el-button>
          <el-button @click="$router.push('/doctor/patients')">
            <el-icon><User /></el-icon> 患者管理
          </el-button>
        </el-card>

        <!-- 最近内容 -->
        <el-tabs v-model="activeTab" class="content-tabs">
          <el-tab-pane label="最近膳食计划" name="plans">
            <el-table :data="data?.recentPlans || []" style="width: 100%" v-if="data?.recentPlans?.length">
              <el-table-column prop="title" label="计划标题" />
              <el-table-column prop="patientName" label="患者" width="120" />
              <el-table-column prop="createTime" label="创建时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="$router.push(`/doctor/diet-plan/${row.id}`)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无膳食计划" />
          </el-tab-pane>
          <el-tab-pane label="待回复咨询" name="consultations">
            <el-table :data="data?.pendingConsultations || []" style="width: 100%" v-if="data?.pendingConsultations?.length">
              <el-table-column prop="title" label="咨询标题" />
              <el-table-column prop="patientName" label="患者" width="120" />
              <el-table-column prop="createTime" label="创建时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="$router.push(`/doctor/consultation/${row.id}`)">回复</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无待回复咨询" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { getWorkspaceData } from '@/api/doctor'
import { Plus, Document, ChatLineRound, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)
const data = ref(null)
const activeTab = ref('plans')

const loadData = async () => {
  loading.value = true
  try {
    const res = await getWorkspaceData()
    if (res.code === 200) {
      data.value = res.data
    }
  } catch (error) {
    ElMessage.error('加载工作台数据失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.workspace-header h2 {
  margin: 0;
  font-size: 20px;
}

.welcome-text {
  color: #909399;
  font-size: 14px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
}

.stat-pending .stat-number {
  color: #e6a23c;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.quick-actions {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.content-tabs {
  margin-top: 10px;
}
</style>