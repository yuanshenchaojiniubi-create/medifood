<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <h2>💬 咨询管理</h2>
          </div>
        </template>

        <!-- 状态筛选 -->
        <el-tabs v-model="activeStatus" @tab-change="handleStatusChange" class="status-tabs">
          <el-tab-pane label="全部" name="" />
          <el-tab-pane label="待回复" name="PENDING" />
          <el-tab-pane label="已回复" name="REPLIED" />
          <el-tab-pane label="已关闭" name="CLOSED" />
        </el-tabs>

        <!-- 咨询列表 -->
        <el-table :data="consultList" style="width: 100%" empty-text="暂无咨询">
          <el-table-column prop="title" label="咨询标题" min-width="200">
            <template #default="{ row }">
              <el-link type="primary" @click="$router.push(`/doctor/consultation/${row.id}`)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="patientName" label="患者" width="120" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'PENDING'" type="warning" size="small">待回复</el-tag>
              <el-tag v-else-if="row.status === 'REPLIED'" type="success" size="small">已回复</el-tag>
              <el-tag v-else-if="row.status === 'CLOSED'" type="info" size="small">已关闭</el-tag>
              <el-tag v-else size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="replyCount" label="回复数" width="80" align="center" />
          <el-table-column prop="createTime" label="创建时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="$router.push(`/doctor/consultation/${row.id}`)">
                {{ row.status === 'PENDING' ? '回复' : '查看' }}
              </el-button>
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
import PageLayout from '@/components/PageLayout.vue'
import { getDoctorConsultations } from '@/api/doctor'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const consultList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const activeStatus = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (activeStatus.value) {
      params.status = activeStatus.value
    }
    const res = await getDoctorConsultations(params)
    if (res.code === 200) {
      consultList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载咨询列表失败')
  } finally {
    loading.value = false
  }
}

const handleStatusChange = () => {
  currentPage.value = 1
  loadData()
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

.status-tabs {
  margin-bottom: 16px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>