<template>
  <div class="app-root">
    <Header v-if="showHeader" class="app-header" />
    <div class="app-body">
      <router-view />
    </div>
    <HealthAssistant />
    <ChatToast />
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import Header from '@/components/Header.vue'
import HealthAssistant from '@/components/HealthAssistant.vue'
import ChatToast from '@/components/ChatToast.vue'

const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const showHeader = computed(() => !route.meta.hideHeader)

watch(() => userStore.token, (val) => {
  if (val) {
    chatStore.connect()
  } else {
    chatStore.disconnect()
  }
}, { immediate: true })
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
    'Noto Sans', sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol',
    'Noto Color Emoji';
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: #f5f6f8;
  color: #333;
  line-height: 1.6;
}

#app {
  min-height: 100vh;
}

.app-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  flex-shrink: 0;
}

.app-body {
  flex: 1;
  min-height: 0;
}

/* 全局 Element Plus 样式覆盖 */
.el-card {
  border-radius: 12px !important;
  border: 1px solid #f0f0f0 !important;
  transition: box-shadow 0.2s ease;
}

.el-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.el-button {
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.2s ease;
}

.el-button--primary:not(.is-plain) {
  background: #409eff;
  border-color: #409eff;
}

.el-input__wrapper {
  border-radius: 8px !important;
  transition: border-color 0.2s;
}

.el-tag {
  border-radius: 6px;
  font-weight: 500;
}

/* 统一的滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}

/* 基础过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 链接样式 */
a {
  color: #409eff;
  text-decoration: none;
}

a:hover {
  color: #66b1ff;
}
</style>
