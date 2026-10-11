<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="edit-card" v-loading="loading">
          <template #header>
            <div class="page-header">
              <div>
                <h2>编辑知识</h2>
                <p>修改后会自动同步到文档库和向量库</p>
              </div>
              <div class="header-actions">
                <el-button @click="goBack">取消</el-button>
                <el-button type="primary" :loading="submitting" @click="handleSubmit">保存修改</el-button>
              </div>
            </div>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="edit-form">
            <el-form-item label="标题" prop="title">
              <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="请输入知识标题" />
            </el-form-item>

            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 220px;">
                <el-option label="食疗养生" :value="1" />
                <el-option label="药膳搭配" :value="2" />
                <el-option label="养生方法" :value="3" />
              </el-select>
            </el-form-item>

            <el-form-item label="封面图">
              <el-input v-model="form.coverImage" placeholder="请输入封面图 URL" />
            </el-form-item>

            <el-form-item label="食材标签">
              <div class="tag-editor">
                <div class="tag-list">
                  <el-tag
                    v-for="(ingredient, index) in form.ingredients"
                    :key="`${ingredient}-${index}`"
                    closable
                    @close="removeIngredient(index)"
                  >
                    {{ ingredient }}
                  </el-tag>
                </div>
                <div class="tag-input-row">
                  <el-input
                    v-model="ingredientInput"
                    placeholder="输入后回车添加，例如：山药"
                    @keyup.enter="addIngredient"
                  />
                  <el-button @click="addIngredient">添加</el-button>
                </div>
              </div>
            </el-form-item>

            <el-form-item label="内容" prop="content">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="16"
                placeholder="请输入知识内容，支持 Markdown 风格标题和段落"
              />
            </el-form-item>
          </el-form>
        </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import { ElMessage } from 'element-plus'
import { getKnowledgeDetail, updateKnowledge } from '@/api/knowledge'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const ingredientInput = ref('')

const form = reactive({
  title: '',
  content: '',
  categoryId: null,
  coverImage: '',
  images: [],
  ingredients: []
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeDetail(route.params.id)
    const detail = res.data
    if (String(detail.userId) !== String(userStore.userInfo?.id)) {
      ElMessage.error('只能编辑自己发布的知识')
      router.replace(`/knowledge/${route.params.id}`)
      return
    }

    form.title = detail.title || ''
    form.content = detail.content || ''
    form.categoryId = detail.categoryId ?? null
    form.coverImage = detail.coverImage || ''
    form.images = Array.isArray(detail.images) ? [...detail.images] : []
    form.ingredients = Array.isArray(detail.ingredients) ? [...detail.ingredients] : []
  } catch (error) {
    ElMessage.error('加载知识详情失败')
    router.replace('/knowledge')
  } finally {
    loading.value = false
  }
}

const addIngredient = () => {
  const value = ingredientInput.value.trim()
  if (!value) return
  if (!form.ingredients.includes(value)) {
    form.ingredients.push(value)
  }
  ingredientInput.value = ''
}

const removeIngredient = (index) => {
  form.ingredients.splice(index, 1)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await updateKnowledge(route.params.id, {
        title: form.title,
        content: form.content,
        categoryId: form.categoryId,
        coverImage: form.coverImage,
        images: form.images,
        ingredients: form.ingredients
      })
      ElMessage.success('知识更新成功')
      router.push(`/knowledge/${route.params.id}`)
    } catch (error) {
      ElMessage.error('知识更新失败')
    } finally {
      submitting.value = false
    }
  })
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.edit-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  max-width: 900px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.page-header h2 {
  margin: 0 0 6px;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.edit-form {
  max-width: 900px;
}

.tag-editor {
  width: 100%;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.tag-input-row {
  display: flex;
  gap: 12px;
}
</style>
