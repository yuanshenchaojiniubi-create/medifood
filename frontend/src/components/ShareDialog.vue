<template>
  <el-dialog v-model="visible" title="分享给互关好友" width="480px" @close="handleClose">
    <div v-if="loading" class="share-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
    <div v-else-if="friends.length === 0" class="share-empty">
      暂无互关好友，快去关注别人吧！
    </div>
    <div v-else class="share-list">
      <div
        v-for="f in friends"
        :key="f.userId"
        class="share-item"
        @click="doShare(f)"
      >
        <el-avatar :size="40" :src="f.avatar">
          {{ f.nickname?.[0] || 'U' }}
        </el-avatar>
        <div class="share-info">
          <span class="share-name">{{ f.nickname || f.username }}</span>
          <span class="share-username">@{{ f.username }}</span>
        </div>
        <el-button
          v-if="sharedIds.has(f.userId)"
          type="success"
          size="small"
          disabled
        >
          已分享
        </el-button>
        <el-button v-else type="primary" size="small">分享</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, reactive } from 'vue'
import { getMutualFollows } from '@/api/community'
import { shareMessage } from '@/api/message'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

const userStore = useUserStore()

const props = defineProps({
  modelValue: Boolean,
  shareType: { type: String, required: true },
  shareId: { type: Number, required: true },
  shareTitle: { type: String, default: '' },
  shareImage: { type: String, default: '' },
  shareUrl: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'done'])

const visible = ref(props.modelValue)
const loading = ref(false)
const friends = ref([])
const sharedIds = reactive(new Set())

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) loadFriends()
})

const loadFriends = async () => {
  loading.value = true
  sharedIds.clear()
  try {
    const uid = userStore.userInfo?.id
    if (!uid) {
      ElMessage.warning('请先登录')
      return
    }
    const res = await getMutualFollows(uid, 1, 200)
    if (res?.data?.records) {
      friends.value = res.data.records
    }
  } catch {
    ElMessage.error('加载互关好友失败')
  } finally {
    loading.value = false
  }
}

const doShare = async (friend) => {
  try {
    await shareMessage({
      receiverId: friend.userId,
      shareType: props.shareType,
      shareId: props.shareId,
      shareTitle: props.shareTitle,
      shareImage: props.shareImage,
      shareUrl: props.shareUrl
    })
    sharedIds.add(friend.userId)
    ElMessage.success(`已分享给 ${friend.nickname || friend.username}`)
    emit('done')
    setTimeout(() => handleClose(), 800)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '分享失败')
  }
}

const handleClose = () => {
  visible.value = false
  emit('update:modelValue', false)
}
</script>

<style scoped>
.share-loading,
.share-empty {
  text-align: center;
  padding: 40px 0;
  color: #909399;
}

.share-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 360px;
  overflow-y: auto;
}

.share-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.share-item:hover {
  background: #f5f7fa;
}

.share-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.share-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.share-username {
  font-size: 12px;
  color: #909399;
}
</style>