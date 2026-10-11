<template>
  <PageLayout :show-sidebar="true">
    <template #main>
      <el-card class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <h2>{{ isEdit ? '✏️ 编辑膳食计划' : '➕ 创建膳食计划' }}</h2>
            <el-button @click="$router.push('/doctor/diet-plans')">返回列表</el-button>
          </div>
        </template>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="plan-form">
          <el-form-item label="患者" prop="patientId">
            <el-select
              v-model="form.patientId"
              filterable
              remote
              reserve-keyword
              placeholder="请输入患者昵称搜索"
              :remote-method="searchPatients"
              :loading="searchLoading"
              style="width: 100%"
              :disabled="isEdit"
            >
              <el-option
                v-for="item in patientOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="计划标题" prop="title">
            <el-input v-model="form.title" placeholder="请输入膳食计划标题" maxlength="200" show-word-limit />
          </el-form-item>

          <el-form-item label="计划描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              placeholder="简要描述该膳食计划的目的和适用范围"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="详细内容" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="8"
              placeholder="请输入详细的膳食计划内容，包括每日饮食安排、食材用量、烹饪方法等"
            />
          </el-form-item>

          <el-form-item label="针对症状" prop="targetSymptoms">
            <el-input
              v-model="form.targetSymptoms"
              type="textarea"
              :rows="2"
              placeholder="如：脾胃虚弱、气血不足、失眠多梦等"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="建议天数" prop="durationDays">
            <el-input-number v-model="form.durationDays" :min="1" :max="365" placeholder="建议执行天数" style="width: 200px" />
            <span class="form-hint">天</span>
          </el-form-item>

          <el-form-item label="注意事项" prop="notes">
            <el-input
              v-model="form.notes"
              type="textarea"
              :rows="3"
              placeholder="如：禁忌人群、饮食禁忌、过敏提醒等"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleSubmit" :loading="submitting">
              {{ isEdit ? '保存修改' : '创建计划' }}
            </el-button>
            <el-button @click="$router.push('/doctor/diet-plans')">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import { createDietPlan, updateDietPlan, getDietPlanDetail } from '@/api/doctor'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const searchLoading = ref(false)
const formRef = ref(null)
const patientOptions = ref([])

const form = ref({
  patientId: null,
  title: '',
  description: '',
  content: '',
  targetSymptoms: '',
  durationDays: 7,
  notes: ''
})

const rules = {
  patientId: [{ required: true, message: '请选择患者', trigger: 'change' }],
  title: [{ required: true, message: '请输入计划标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入详细内容', trigger: 'blur' }]
}

// 搜索患者（简易版：使用患者ID输入，后续可接入用户搜索API）
const searchPatients = async (query) => {
  if (!query || query.length < 1) {
    patientOptions.value = []
    return
  }
  searchLoading.value = true
  try {
    // 简易实现：接受数字ID直接选择，或通过昵称搜索
    // 后续可接入 /api/user/search 接口
    if (/^\d+$/.test(query)) {
      patientOptions.value = [{ label: `用户ID: ${query}`, value: Number(query) }]
    } else {
      patientOptions.value = [{ label: `搜索: ${query}`, value: query }]
    }
  } finally {
    searchLoading.value = false
  }
}

// 加载编辑数据
const loadPlanData = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getDietPlanDetail(route.params.id)
    if (res.code === 200 && res.data) {
      const d = res.data
      form.value = {
        patientId: d.patientId,
        title: d.title,
        description: d.description || '',
        content: d.content || '',
        targetSymptoms: d.targetSymptoms || '',
        durationDays: d.durationDays || 7,
        notes: d.notes || ''
      }
      // 预填患者选项
      patientOptions.value = [{ label: d.patientName || `用户${d.patientId}`, value: d.patientId }]
    }
  } catch (error) {
    ElMessage.error('加载计划数据失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = { ...form.value }
    let res
    if (isEdit.value) {
      res = await updateDietPlan(route.params.id, data)
    } else {
      res = await createDietPlan(data)
    }
    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      router.push('/doctor/diet-plans')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadPlanData()
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

.plan-form {
  max-width: 800px;
}

.form-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 14px;
}
</style>