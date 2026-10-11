<template>
  <PageLayout :show-sidebar="false">
    <template #main>
      <el-card class="settings-card" v-loading="loading">
          <template #header>
            <h2>个人设置</h2>
          </template>
          <el-form
            :model="settingsForm"
            :rules="settingsRules"
            ref="settingsFormRef"
            label-width="100px"
            style="max-width: 600px;"
          >
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="settingsForm.nickname" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="性别">
              <el-radio-group v-model="settingsForm.gender">
                <el-radio :label="0">未知</el-radio>
                <el-radio :label="1">男</el-radio>
                <el-radio :label="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="生日">
              <el-date-picker
                v-model="settingsForm.birthday"
                type="date"
                placeholder="选择生日"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="settingsForm.bio" type="textarea" :rows="4" placeholder="请输入个人简介" />
            </el-form-item>
            <el-form-item label="头像">
              <el-input v-model="settingsForm.avatar" placeholder="请输入头像URL" />
              <div v-if="settingsForm.avatar" class="avatar-preview">
                <el-avatar :size="100" :src="settingsForm.avatar" />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
              <el-button @click="handleCancel">取消</el-button>
            </el-form-item>
          </el-form>

          <el-divider />

          <div class="password-section">
            <h3>修改密码</h3>
            <el-form
              :model="passwordForm"
              :rules="passwordRules"
              ref="passwordFormRef"
              label-width="100px"
              style="max-width: 600px;"
            >
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" @click="handleChangePassword" :loading="passwordLoading">修改密码</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-divider />

          <div class="cert-section" v-loading="certLoading">
            <div class="cert-header">
              <div>
                <h3>医师资格认证</h3>
                <p class="cert-tip">上传执业证书或职业资格证明，由管理员审核通过后可展示「认证医师」身份，并发布养生知识与食谱。</p>
              </div>
              <el-tag :type="certStatusType" effect="light">{{ certStatusText }}</el-tag>
            </div>

            <el-alert
              :title="certStatusText"
              :description="certStatusDesc"
              :type="certStatusType"
              show-icon
              class="cert-alert"
            />

            <el-form label-width="120px" class="cert-form">
              <el-form-item label="执业医院">
                <el-input
                  v-model="certForm.hospital"
                  placeholder="请输入执业医院名称"
                  :disabled="isApproved"
                />
              </el-form-item>
              <el-form-item label="科室">
                <el-input
                  v-model="certForm.department"
                  placeholder="如：中医内科、营养科"
                  :disabled="isApproved"
                />
              </el-form-item>
              <el-form-item label="职称">
                <el-select
                  v-model="certForm.title"
                  placeholder="请选择职称"
                  :disabled="isApproved"
                  style="width: 100%"
                >
                  <el-option label="主任医师" value="主任医师" />
                  <el-option label="副主任医师" value="副主任医师" />
                  <el-option label="主治医师" value="主治医师" />
                  <el-option label="营养师" value="营养师" />
                  <el-option label="健康管理师" value="健康管理师" />
                  <el-option label="其他" value="其他" />
                </el-select>
              </el-form-item>
              <el-form-item label="擅长领域">
                <el-input
                  v-model="certForm.specialties"
                  placeholder="如：糖尿病饮食调理、高血压食疗、小儿积食"
                  :disabled="isApproved"
                />
              </el-form-item>
              <el-form-item label="从业年限">
                <el-input-number
                  v-model="certForm.practiceYears"
                  :min="0"
                  :max="60"
                  placeholder="从事本专业年限"
                  :disabled="isApproved"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="执业说明">
                <el-input
                  v-model="certForm.description"
                  type="textarea"
                  :rows="3"
                  placeholder="请填写执业说明、擅长领域等信息"
                  :disabled="isApproved"
                />
              </el-form-item>
              <el-form-item label="证明材料">
                <el-upload
                  class="cert-upload"
                  drag
                  :file-list="fileList"
                  :auto-upload="false"
                  :limit="1"
                  accept=".jpg,.jpeg,.png,.pdf"
                  :on-change="handleFileChange"
                  :on-remove="handleFileRemove"
                  :disabled="isApproved || certStatus === 'PENDING' || certSubmitting"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
                  <template #tip>
                    <div class="el-upload__tip">支持 JPG/PNG/PDF，文件大小 ≤ 5MB</div>
                  </template>
                </el-upload>
                <div v-if="certInfo?.certificateUrl" class="cert-file">
                  <el-link type="primary" :underline="false" :href="certInfo.certificateUrl" target="_blank">
                    查看已提交的认证文件
                  </el-link>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="success"
                  :disabled="isApproved || certStatus === 'PENDING'"
                  :loading="certSubmitting"
                  @click="submitCertification"
                >
                  {{ certStatus === 'REJECTED' ? '重新提交' : '提交认证申请' }}
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
    </template>
  </PageLayout>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import PageLayout from '@/components/PageLayout.vue'
import { updateUserInfo, applyDoctorCertification, getDoctorCertificationStatus, changePassword } from '@/api/user'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const certLoading = ref(false)
const certSubmitting = ref(false)
const passwordLoading = ref(false)
const settingsFormRef = ref(null)
const passwordFormRef = ref(null)
const fileList = ref([])

const settingsForm = reactive({
  nickname: '',
  gender: 0,
  birthday: null,
  bio: '',
  avatar: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else if (value && value.length < 6) {
    callback(new Error('密码长度不能少于6位'))
  } else {
    callback()
  }
}

const certForm = reactive({
  hospital: '',
  department: '',
  title: '',
  specialties: '',
  practiceYears: null,
  description: ''
})

const settingsRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const certStatus = ref('UNSUBMITTED')
const certInfo = ref({})

const statusTextMap = {
  UNSUBMITTED: '未认证',
  PENDING: '审核中',
  APPROVED: '已认证',
  REJECTED: '认证被拒'
}

const statusDescMap = {
  UNSUBMITTED: '提交执业证明材料，管理员审核通过后即可成为认证医师，并可在「养生知识」「健康食谱」中发布内容。',
  PENDING: '您的认证资料已提交，请等待管理员审核。',
  APPROVED: '您已是认证医师，可在养生知识、健康食谱模块发布内容，身份标签将在平台展示。',
  REJECTED: '认证被拒绝，请根据审核说明修改资料后重新提交。'
}

const statusTypeMap = {
  UNSUBMITTED: 'info',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger'
}

const loadUserInfo = async () => {
  loading.value = true
  try {
    await userStore.fetchUserInfo()
    if (userStore.userInfo) {
      Object.assign(settingsForm, {
        nickname: userStore.userInfo.nickname || '',
        gender: userStore.userInfo.gender || 0,
        birthday: userStore.userInfo.birthday || null,
        bio: userStore.userInfo.bio || '',
        avatar: userStore.userInfo.avatar || ''
      })
    }
  } catch (error) {
    ElMessage.error('加载用户信息失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!settingsFormRef.value) return
  await settingsFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const res = await updateUserInfo(settingsForm)
        if (res.code === 200) {
          ElMessage.success('保存成功')
          await userStore.fetchUserInfo()
        }
      } catch (error) {
        ElMessage.error('保存失败')
      } finally {
        saving.value = false
      }
    }
  })
}

const handleCancel = () => {
  router.back()
}

const loadCertificationStatus = async () => {
  certLoading.value = true
  try {
    const res = await getDoctorCertificationStatus()
    if (res.code === 200) {
      certStatus.value = res.data?.status || 'UNSUBMITTED'
      certInfo.value = res.data || {}
      if (certStatus.value === 'APPROVED' && res.data?.description) {
        certForm.description = res.data.description
      }
      // 回显专业信息
      if (res.data?.hospital) certForm.hospital = res.data.hospital
      if (res.data?.department) certForm.department = res.data.department
      if (res.data?.title) certForm.title = res.data.title
      if (res.data?.specialties) certForm.specialties = res.data.specialties
      if (res.data?.practiceYears) certForm.practiceYears = res.data.practiceYears
    }
  } catch (error) {
    ElMessage.error('获取认证状态失败')
  } finally {
    certLoading.value = false
  }
}

const handleFileChange = (_file, uploadFiles) => {
  fileList.value = uploadFiles.slice(-1)
}

const handleFileRemove = () => {
  fileList.value = []
}

const submitCertification = async () => {
  if (!fileList.value.length || !fileList.value[0].raw) {
    ElMessage.warning('请先上传认证材料')
    return
  }
  certSubmitting.value = true
  const formData = new FormData()
  formData.append('file', fileList.value[0].raw)
  // 将认证信息序列化为 JSON 通过 FormData 发送
  const applyInfo = {
    description: certForm.description,
    hospital: certForm.hospital,
    department: certForm.department,
    title: certForm.title,
    specialties: certForm.specialties,
    practiceYears: certForm.practiceYears
  }
  formData.append('applyInfo', new Blob([JSON.stringify(applyInfo)], { type: 'application/json' }))
  try {
    const res = await applyDoctorCertification(formData)
    if (res.code === 200) {
      ElMessage.success('认证申请已提交')
      fileList.value = []
      await Promise.all([loadCertificationStatus(), userStore.fetchUserInfo()])
    }
  } catch (error) {
    ElMessage.error(error.message || '提交失败，请稍后再试')
  } finally {
    certSubmitting.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      passwordLoading.value = true
      try {
        const res = await changePassword({
          oldPassword: passwordForm.oldPassword,
          newPassword: passwordForm.newPassword,
          confirmPassword: passwordForm.confirmPassword
        })
        if (res.code === 200) {
          ElMessage.success('密码修改成功，请重新登录')
          passwordForm.oldPassword = ''
          passwordForm.newPassword = ''
          passwordForm.confirmPassword = ''
        }
      } catch (error) {
        ElMessage.error(error.message || '密码修改失败')
      } finally {
        passwordLoading.value = false
      }
    }
  })
}

const certStatusText = computed(() => statusTextMap[certStatus.value] || '未认证')
const certStatusType = computed(() => statusTypeMap[certStatus.value] || 'info')
const certStatusDesc = computed(() => {
  if (certStatus.value === 'REJECTED' && certInfo.value?.reason) {
    return `认证被拒：${certInfo.value.reason}`
  }
  return statusDescMap[certStatus.value] || statusDescMap.UNSUBMITTED
})
const isApproved = computed(() => certStatus.value === 'APPROVED')

onMounted(() => {
  loadUserInfo()
  loadCertificationStatus()
})
</script>

<style scoped>
.settings-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  max-width: 700px;
}

.avatar-preview {
  margin-top: 10px;
}

.cert-section {
  margin-top: 20px;
}

.cert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.cert-header h3 {
  margin: 0;
}

.cert-tip {
  margin: 4px 0 0;
  color: #888;
  font-size: 14px;
}

.cert-alert {
  margin: 20px 0;
}

.cert-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.cert-upload {
  width: 100%;
}

.cert-file {
  margin-top: 10px;
}

.password-section {
  margin-top: 20px;
}

.password-section h3 {
  margin: 0 0 16px;
}
</style>

