<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="admin-card" shadow="never">
        <template #header>
          <div class="card-head">
            <span>内容审核台</span>
            <div class="head-actions">
              <el-tag v-if="stats.pending > 0" type="danger" size="large" effect="dark">
                {{ stats.pending }} 条待审
              </el-tag>
              <el-button type="primary" size="small" :loading="loading" @click="load">刷新</el-button>
            </div>
          </div>
        </template>

        <!-- 筛选栏 -->
        <div class="filter-bar">
          <el-select v-model="query.contentType" placeholder="内容类型（全部）" clearable style="width: 160px" @change="load">
            <el-option v-for="(label, value) in contentTypeMap" :key="value" :label="label" :value="Number(value)" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态（全部）" clearable style="width: 160px" @change="load">
            <el-option label="待人工审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
            <el-option label="已拒绝并警告" :value="3" />
          </el-select>
        </div>

        <el-table v-loading="loading" :data="list" stripe empty-text="暂无审核记录">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTagColor(row.contentType)">{{ row.contentTypeDesc }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布者" width="140">
            <template #default="{ row }">
              {{ row.nickname || row.username || ('用户#' + row.userId) }}
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="260">
            <template #default="{ row }">
              <div class="content-cell">
                <div v-if="row.title" class="content-title">{{ row.title }}</div>
                <div class="content-body">{{ shortText(row.contentSnapshot) }}</div>
                <div v-if="row.imageUrls && row.imageUrls.length" class="content-images">
                  <el-image
                    v-for="(img, i) in row.imageUrls.slice(0, 3)"
                    :key="i"
                    :src="img"
                    :preview-src-list="row.imageUrls"
                    :initial-index="i"
                    fit="cover"
                    class="thumb"
                    preview-teleported
                  />
                  <span v-if="row.imageUrls.length > 3" class="more-img">+{{ row.imageUrls.length - 3 }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="AI 判定" width="200">
            <template #default="{ row }">
              <div>
                <el-tag size="small" type="warning">{{ row.aiCategory || '疑似' }}</el-tag>
              </div>
              <div class="ai-reason">{{ row.aiReason }}</div>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.auditMode === 2 ? 'danger' : 'info'" effect="plain">
                {{ row.auditMode === 2 ? '先审后发' : '先发后审' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" width="170" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button type="success" size="small" @click="openApprove(row)">通过</el-button>
                <el-button type="danger" size="small" @click="openReject(row, false)">删除</el-button>
                <el-button type="warning" size="small" @click="openReject(row, true)">删+警告</el-button>
              </template>
              <span v-else class="done-text">已处理</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="load"
            @size-change="load"
          />
        </div>
      </el-card>

      <!-- 处理备注弹窗 -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="440px" @close="dialogRemark = ''">
        <div class="dialog-tip">{{ dialogTip }}</div>
        <el-input v-model="dialogRemark" type="textarea" :rows="3" placeholder="选填备注（仅记录在审核台账，不展示给用户）" />
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button :type="dialogType" :loading="submitting" @click="confirm">确认</el-button>
        </template>
      </el-dialog>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/PageLayout.vue'
import { listAuditRecords, auditStats, approveAudit, rejectAudit } from '@/api/audit'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({ pending: 0, approved: 0, rejected: 0 })

const query = reactive({
  contentType: null,
  status: 0,
  page: 1,
  size: 10
})

const contentTypeMap = {
  1: '社区动态',
  2: '食谱',
  3: '知识文章',
  4: '社区评论',
  5: '食谱评论',
  6: '知识评论',
  7: '提问',
  8: '回答'
}

// 处理弹窗状态
const dialogVisible = ref(false)
const dialogRemark = ref('')
const dialogTitle = ref('')
const dialogTip = ref('')
const dialogType = ref('success')
const pendingRow = ref(null)
const pendingWarn = ref(false)

const typeTagColor = (t) => {
  const map = { 2: 'success', 3: 'success', 1: '', 4: 'info', 5: 'info', 6: 'info', 7: 'warning', 8: 'warning' }
  return map[t] || 'info'
}

const statusText = (s) => ({ 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '拒绝+警告' }[s] || '未知')
const statusTagType = (s) => ({ 0: 'danger', 1: 'success', 2: 'info', 3: 'warning' }[s] || 'info')

const shortText = (t) => {
  if (!t) return '（无文字内容）'
  return t.length > 80 ? t.slice(0, 80) + '…' : t
}

const load = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.contentType != null) params.contentType = query.contentType
    if (query.status != null) params.status = query.status
    const res = await listAuditRecords(params)
    if (res.code === 200 && res.data) {
      list.value = res.data.records || []
      total.value = Number(res.data.total || 0)
    }
    const s = await auditStats()
    if (s.code === 200 && s.data) stats.value = s.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const openApprove = (row) => {
  pendingRow.value = row
  pendingWarn.value = false
  dialogTitle.value = '审核通过'
  dialogTip.value = row.auditMode === 2
    ? '该内容当前处于"审核中"状态（对外不可见），通过后将自动公开并同步到搜索。'
    : '该内容当前在线展示中，通过后仅结案，不做其他操作。'
  dialogType.value = 'success'
  dialogRemark.value = ''
  dialogVisible.value = true
}

const openReject = (row, warn) => {
  pendingRow.value = row
  pendingWarn.value = warn
  dialogTitle.value = warn ? '删除并警告' : '删除内容'
  dialogTip.value = warn
    ? '将删除该内容，并向发布者发送违规删除通知和警告通知。'
    : '将删除该内容，并向发布者发送违规删除通知。'
  dialogType.value = 'danger'
  dialogRemark.value = ''
  dialogVisible.value = true
}

const confirm = async () => {
  if (!pendingRow.value) return
  submitting.value = true
  try {
    let res
    if (dialogType.value === 'success') {
      res = await approveAudit(pendingRow.value.id, dialogRemark.value || undefined)
    } else {
      res = await rejectAudit(pendingRow.value.id, pendingWarn.value, dialogRemark.value || undefined)
    }
    if (res.code === 200) {
      ElMessage.success(dialogType.value === 'success' ? '已通过' : (pendingWarn.value ? '已删除并警告' : '已删除'))
      dialogVisible.value = false
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
  min-height: 100%;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.content-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.content-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.content-body {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}

.content-images {
  display: flex;
  align-items: center;
  gap: 6px;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  cursor: pointer;
}

.more-img {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ai-reason {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.4;
}

.done-text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dialog-tip {
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}
</style>
