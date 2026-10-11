<template>
  <div class="page-layout">
    <div class="page-container">
      <div class="page-content" :class="{ 'has-sidebar': showSidebar }">
        <slot name="main" />
      </div>
      <aside v-if="showSidebar" class="page-sidebar">
        <slot name="sidebar">
          <Sidebar />
        </slot>
      </aside>
    </div>
  </div>
</template>

<script setup>
import Sidebar from '@/components/Sidebar.vue'

defineProps({
  /** 是否显示右侧边栏 */
  showSidebar: {
    type: Boolean,
    default: true
  }
})
</script>

<style scoped>
.page-layout {
  min-height: calc(100vh - 60px);
  background-color: #f5f6f8;
  padding: 20px 0;
}

.page-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.page-content {
  flex: 1;
  min-width: 0;
}

.page-content.has-sidebar {
  flex: 1;
  max-width: calc(100% - 320px);
}

.page-sidebar {
  width: 300px;
  flex-shrink: 0;
  position: sticky;
  top: 80px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

/* 响应式：小屏幕隐藏侧边栏 */
@media (max-width: 1024px) {
  .page-sidebar {
    display: none;
  }

  .page-content.has-sidebar {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .page-layout {
    padding: 12px 0;
  }

  .page-container {
    padding: 0 12px;
  }
}
</style>