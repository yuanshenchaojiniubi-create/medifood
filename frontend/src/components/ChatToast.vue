<template>
  <Teleport to="body">
    <div class="ct-container">
      <TransitionGroup name="ct-slide">
        <div
          v-for="t in chatStore.toasts"
          :key="t.id"
          class="ct-toast"
          :class="{ 'ct-unread-tip': t.isUnreadTip }"
          @click="onClick(t)"
        >
          <div v-if="!t.isUnreadTip" class="ct-avatar">
            <el-avatar :size="40">{{ t.senderName?.[0] || 'U' }}</el-avatar>
          </div>
          <div v-else class="ct-icon">
            <el-icon :size="20"><ChatDotRound /></el-icon>
          </div>
          <div class="ct-body">
            <div class="ct-name">{{ t.senderName }}</div>
            <div class="ct-preview">{{ chatStore.getMessagePreview(t) }}</div>
          </div>
          <div class="ct-close" @click.stop="chatStore.dismissToast(t.id)">
            <el-icon :size="14"><Close /></el-icon>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useChatStore } from '@/store/chat'
import { ChatDotRound, Close } from '@element-plus/icons-vue'

const router = useRouter()
const chatStore = useChatStore()

const onClick = (t) => {
  if (t.isUnreadTip) {
    router.push('/messages')
  } else if (t.senderId) {
    router.push(`/messages/chat/${t.senderId}`)
  }
  chatStore.dismissToast(t.id)
}
</script>

<style scoped>
.ct-container {
  position: fixed;
  top: 70px;
  right: 16px;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 320px;
}

.ct-toast {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  min-width: 260px;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.2s;
}

.ct-toast:hover {
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16);
}

.ct-unread-tip {
  border-left: 3px solid #409eff;
}

.ct-avatar {
  flex-shrink: 0;
}

.ct-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
}

.ct-body {
  flex: 1;
  min-width: 0;
}

.ct-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 2px;
}

.ct-preview {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ct-close {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #c0c4cc;
  transition: all 0.2s;
}

.ct-close:hover {
  background: #f5f5f5;
  color: #909399;
}

.ct-slide-enter-active {
  transition: all 0.35s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.ct-slide-leave-active {
  transition: all 0.25s ease-in;
}

.ct-slide-enter-from {
  opacity: 0;
  transform: translateX(80px);
}

.ct-slide-leave-to {
  opacity: 0;
  transform: translateX(80px);
}
</style>