<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <h2>📋 膳食计划管理</h2>
            <el-button type="primary" @click="$router.push('/doctor/diet-plan/create')">
              <el-icon><Plus /></el-icon> 创建计划
            </el-button>
          </div>
        </template>

        <!-- 搜索筛选 -->
        <el-form :inline="true" class="search-form">
          <el-form-item label="患者筛选">
            <el-input v-model="searchPatient" placeholder="输入患者ID或昵称" clearable @clear="loadData" style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadData">
              <el-icon><Search /></el-icon> 搜索
            </el-button>
            <el-button @click="searchPatient = ''; loadData()">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 表格 -->
        <el-table :data="planList" style="width: 100%" empty-text="暂无膳食计划">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="计划标题" min-width="180">
            <template #default="{ row }">
              <el-link type="primary" @click="$router.push(`/doctor/diet-plan/${row.id}`)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="patientName" label="患者" width="120" />
          <el-table-column prop="durationDays" label="建议天数" width="100" align="center">
            <template #default="{ row }">
              {{ row.durationDays ? row.durationDays + '天' : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="viewCount" label="浏览" width="80" align="center" />
          <el-table-column prop="createTime" label="创建时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="$router.push(`/doctor/diet-plan/${row.id}`)">查看</el-button>
              <el-button type="warning" link size="small" @click="$router.push(`/doctor/diet-plan/${row.id}/edit`)">编辑</el-button>
              <el-popconfirm title="确定删除该膳食计划吗？" @confirm="handleDelete(row.id)">
                <template #reference>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-popconfirm>
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
import { getDietPlans, deleteDietPlan } from '@/api/doctor'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const planList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchPatient = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (searchPatient.value) {
      params.patientId = searchPatient.value
    }
    const res = await getDietPlans(params)
    if (res.code === 200) {
      planList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载膳食计划列表失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id) => {
  try {
    const res = await deleteDietPlan(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
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

.search-form {
  margin-bottom: 16px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>