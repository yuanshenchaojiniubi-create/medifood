<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="admin-card" shadow="never">
      <template #header>
        <div class="card-head">
          <span>轮播图管理</span>
          <div>
            <el-button type="primary" size="small" @click="openAdd">新增轮播图</el-button>
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无轮播图">
        <el-table-column label="预览" width="180">
          <template #default="{ row }">
            <el-image :src="row.imageUrl" fit="cover" style="width:150px;height:56px;border-radius:4px" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" />
        <el-table-column prop="linkUrl" label="跳转链接" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑轮播图' : '新增轮播图'" width="520px" @close="resetForm">
      <el-form :model="form" label-width="80px">
        <el-form-item label="轮播图片" required>
          <single-upload v-model="form.imageUrl" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="选填，如'春季养生'" />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.linkUrl" placeholder="选填，如 /knowledge/1" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/PageLayout.vue'
import { listBanners, createBanner, updateBanner, deleteBanner } from '@/api/banner'
import SingleUpload from '@/components/upload/singleUpload.vue'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)

const emptyForm = () => ({
  imageUrl: '',
  title: '',
  linkUrl: '',
  sortOrder: 0,
  status: 1
})

const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const res = await listBanners()
    if (res.code === 200) {
      list.value = res.data || []
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const openAdd = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    imageUrl: row.imageUrl,
    title: row.title || '',
    linkUrl: row.linkUrl || '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(form, emptyForm())
}

const handleSave = async () => {
  if (!form.imageUrl) {
    ElMessage.warning('请上传轮播图片')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (isEdit.value) {
      await updateBanner(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createBanner(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该轮播图吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteBanner(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
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
  justify-content: space-between;
  align-items: center;
}
</style>