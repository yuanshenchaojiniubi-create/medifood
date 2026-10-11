<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="admin-card" shadow="never">
      <template #header>
        <div class="card-head">
          <span>医师认证审核</span>
          <el-button type="primary" size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无待审核申请">
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="doctorCertDescription" label="申请说明" min-width="160" show-overflow-tooltip />
        <el-table-column prop="doctorCertAppliedAt" label="申请时间" width="180" />
        <el-table-column label="材料" width="100">
          <template #default="{ row }">
            <el-link v-if="row.doctorCertFile" type="primary" :href="certUrl(row.doctorCertFile)" target="_blank">查看</el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="openApprove(row)">通过</el-button>
            <el-button type="danger" size="small" @click="openReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="rejectVisible" title="驳回原因" width="400px" @close="rejectReason = ''">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="选填，将展示给申请人" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageLayout from '@/components/PageLayout.vue'
import { listPendingDoctorCerts, reviewDoctorCert } from '@/api/admin'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const rejectVisible = ref(false)
const rejectReason = ref('')
const pendingUserId = ref(null)

const certUrl = (path) => {
  if (!path) return '#'
  if (path.startsWith('http')) return path
  return path
}

const load = async () => {
  loading.value = true
  try {
    const res = await listPendingDoctorCerts()
    if (res.code === 200) {
      list.value = res.data || []
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const openApprove = async (row) => {
  submitting.value = true
  try {
    const res = await reviewDoctorCert({ userId: row.userId, approved: true })
    if (res.code === 200) {
      ElMessage.success('已通过')
      await load()
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

const openReject = (row) => {
  pendingUserId.value = row.userId
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!pendingUserId.value) return
  submitting.value = true
  try {
    const res = await reviewDoctorCert({
      userId: pendingUserId.value,
      approved: false,
      reason: rejectReason.value || undefined
    })
    if (res.code === 200) {
      ElMessage.success('已驳回')
      rejectVisible.value = false
      await load()
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.admin-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
</style>
