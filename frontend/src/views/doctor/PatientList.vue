<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <h2>👥 患者管理</h2>
          </div>
        </template>

        <!-- 患者表格 -->
        <el-table :data="patientList" style="width: 100%" empty-text="暂无患者数据">
          <el-table-column label="患者" min-width="180">
            <template #default="{ row }">
              <div class="patient-cell">
                <el-avatar :size="36" :src="row.avatar" />
                <span class="patient-name">{{ row.nickname }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="planCount" label="膳食计划" width="100" align="center" />
          <el-table-column prop="consultationCount" label="咨询数" width="80" align="center" />
          <el-table-column prop="lastInteractionTime" label="最近交互" width="170">
            <template #default="{ row }">
              {{ formatTime(row.lastInteractionTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="goToPlans(row.userId)">查看计划</el-button>
              <el-button type="success" link size="small" @click="createPlan(row)">创建计划</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrap" v-if="total > 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import { getPatients } from '@/api/doctor'
import { ElMessage } from 'element-plus'

const router = useRouter()

const loading = ref(false)
const patientList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getPatients({ page: currentPage.value, size: pageSize.value })
    if (res.code === 200) {
      patientList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载患者列表失败')
  } finally {
    loading.value = false
  }
}

const goToPlans = (patientId) => {
  router.push(`/doctor/diet-plans?patientId=${patientId}`)
}

const createPlan = (row) => {
  router.push({ path: '/doctor/diet-plan/create', query: { patientId: row.userId, patientName: row.nickname } })
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
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

.card-header h2 {
  margin: 0;
  font-size: 20px;
}

.patient-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.patient-name {
  font-size: 14px;
  font-weight: 500;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>