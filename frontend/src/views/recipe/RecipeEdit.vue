<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="edit-card" v-loading="loading">
          <template #header>
            <div class="page-header">
              <div>
                <h2>编辑食谱</h2>
                <p>保存后会自动更新 ES 文档索引和向量索引</p>
              </div>
              <div class="header-actions">
                <el-button @click="goBack">取消</el-button>
                <el-button type="primary" :loading="submitting" @click="handleSubmit">保存修改</el-button>
              </div>
            </div>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="edit-form">
            <el-form-item label="食谱名称" prop="title">
              <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="请输入食谱名称" />
            </el-form-item>

            <el-form-item label="食谱描述">
              <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入食谱描述" />
            </el-form-item>

            <el-form-item label="封面图">
              <el-input v-model="form.coverImage" placeholder="请输入封面图 URL" />
            </el-form-item>

            <el-form-item label="难度">
              <el-rate v-model="form.difficulty" :max="5" show-score />
            </el-form-item>

            <el-form-item label="制作时间">
              <el-input-number v-model="form.cookingTime" :min="1" :max="600" />
              <span class="helper-text">分钟</span>
            </el-form-item>

            <el-form-item label="份量">
              <el-input-number v-model="form.servings" :min="1" :max="20" />
              <span class="helper-text">人份</span>
            </el-form-item>

            <el-form-item label="适用人群">
              <el-input v-model="form.suitableFor" placeholder="例如：脾胃虚弱人群" />
            </el-form-item>

            <el-form-item label="功效说明">
              <el-input v-model="form.effects" type="textarea" :rows="3" placeholder="例如：健脾养胃、补气安神" />
            </el-form-item>

            <el-form-item label="食材组成" prop="ingredients">
              <div class="dynamic-list">
                <div v-for="(ingredient, index) in form.ingredients" :key="index" class="dynamic-row">
                  <el-input v-model="ingredient.name" placeholder="食材名称" />
                  <el-input v-model="ingredient.amount" placeholder="用量" />
                  <el-button @click="removeIngredient(index)">删除</el-button>
                </div>
                <el-button @click="addIngredient">添加食材</el-button>
              </div>
            </el-form-item>

            <el-form-item label="食谱标签">
              <el-select
                v-model="form.tagIds"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入标签"
                style="width: 100%;"
                :reserve-keyword="false"
                @create="handleCreateTag"
              >
                <el-option
                  v-for="tag in allTags"
                  :key="tag.id"
                  :label="tag.tagName"
                  :value="tag.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="制作步骤" prop="steps">
              <div class="dynamic-list">
                <div v-for="(step, index) in form.steps" :key="index" class="step-row">
                  <div class="step-index">步骤 {{ index + 1 }}</div>
                  <el-input
                    v-model="step.description"
                    type="textarea"
                    :rows="3"
                    placeholder="请输入步骤内容"
                  />
                  <el-input v-model="step.image" placeholder="步骤配图 URL（可选）" />
                  <el-button @click="removeStep(index)">删除步骤</el-button>
                </div>
                <el-button @click="addStep">添加步骤</el-button>
              </div>
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
import { getRecipeDetail, updateRecipe, getRecipeTags, createRecipeTag } from '@/api/recipe'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const allTags = ref([])

const form = reactive({
  title: '',
  description: '',
  ingredients: [{ name: '', amount: '' }],
  steps: [{ description: '', image: '' }],
  suitableFor: '',
  effects: '',
  coverImage: '',
  images: [],
  difficulty: 3,
  cookingTime: 30,
  servings: 2,
  tagIds: []
})

const rules = {
  title: [{ required: true, message: '请输入食谱名称', trigger: 'blur' }],
  ingredients: [{ validator: validateIngredients, trigger: 'change' }],
  steps: [{ validator: validateSteps, trigger: 'change' }]
}

function validateIngredients(rule, value, callback) {
  const valid = Array.isArray(value) && value.some(item => (item.name || '').trim() && (item.amount || '').trim())
  if (!valid) {
    callback(new Error('请至少填写一个完整食材'))
    return
  }
  callback()
}

function validateSteps(rule, value, callback) {
  const valid = Array.isArray(value) && value.some(item => (item.description || '').trim())
  if (!valid) {
    callback(new Error('请至少填写一个步骤'))
    return
  }
  callback()
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getRecipeDetail(route.params.id)
    const detail = res.data
    if (String(detail.userId) !== String(userStore.userInfo?.id)) {
      ElMessage.error('只能编辑自己发布的食谱')
      router.replace(`/recipe/${route.params.id}`)
      return
    }

    form.title = detail.title || ''
    form.description = detail.description || ''
    form.ingredients = Array.isArray(detail.ingredients) && detail.ingredients.length
      ? detail.ingredients.map(item => ({
          name: item.name || '',
          amount: item.amount || ''
        }))
      : [{ name: '', amount: '' }]
    form.steps = Array.isArray(detail.steps) && detail.steps.length
      ? detail.steps.map(item => ({
          description: item.description || '',
          image: item.image || ''
        }))
      : [{ description: '', image: '' }]
    form.suitableFor = detail.suitableFor || ''
    form.effects = detail.effects || ''
    form.coverImage = detail.coverImage || ''
    form.images = Array.isArray(detail.images) ? [...detail.images] : []
    form.difficulty = detail.difficulty || 3
    form.cookingTime = detail.cookingTime || 30
    form.servings = detail.servings || 2
    form.tagIds = []
  } catch (error) {
    ElMessage.error('加载食谱详情失败')
    router.replace('/recipe')
  } finally {
    loading.value = false
  }
}

const addIngredient = () => {
  form.ingredients.push({ name: '', amount: '' })
}

const removeIngredient = (index) => {
  if (form.ingredients.length === 1) {
    form.ingredients[0] = { name: '', amount: '' }
    return
  }
  form.ingredients.splice(index, 1)
}

const addStep = () => {
  form.steps.push({ description: '', image: '' })
}

const removeStep = (index) => {
  if (form.steps.length === 1) {
    form.steps[0] = { description: '', image: '' }
    return
  }
  form.steps.splice(index, 1)
}

const normalizeIngredients = () => form.ingredients
  .map(item => ({
    name: (item.name || '').trim(),
    amount: (item.amount || '').trim()
  }))
  .filter(item => item.name && item.amount)

const normalizeSteps = () => form.steps
  .map(item => ({
    description: (item.description || '').trim(),
    image: (item.image || '').trim()
  }))
  .filter(item => item.description)

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await updateRecipe(route.params.id, {
        title: form.title,
        description: form.description,
        ingredients: normalizeIngredients(),
        steps: normalizeSteps(),
        suitableFor: form.suitableFor,
        effects: form.effects,
        coverImage: form.coverImage,
        images: form.images,
        difficulty: form.difficulty,
        cookingTime: form.cookingTime,
        servings: form.servings,
        tagIds: form.tagIds
      })
      ElMessage.success('食谱更新成功')
      router.push(`/recipe/${route.params.id}`)
    } catch (error) {
      ElMessage.error('食谱更新失败')
    } finally {
      submitting.value = false
    }
  })
}

const goBack = () => {
  router.back()
}

const loadTags = async () => {
  try {
    const res = await getRecipeTags()
    if (res.code === 200) {
      allTags.value = res.data || []
    }
  } catch (error) {
    // 标签加载失败不影响主流程
  }
}

const handleCreateTag = async (tagName) => {
  try {
    const res = await createRecipeTag(tagName)
    if (res.code === 200 && res.data) {
      const newTag = res.data
      allTags.value.push(newTag)
      form.tagIds.push(newTag.id)
      ElMessage.success(`标签"${tagName}"创建成功`)
    }
  } catch (error) {
    ElMessage.error('创建标签失败')
  }
}

onMounted(() => {
  loadDetail()
  loadTags()
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
  max-width: 960px;
}

.helper-text {
  margin-left: 8px;
  color: #909399;
}

.dynamic-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dynamic-row {
  display: grid;
  grid-template-columns: 1fr 180px 80px;
  gap: 12px;
}

.step-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.step-index {
  font-weight: 600;
  color: #409eff;
}
</style>
