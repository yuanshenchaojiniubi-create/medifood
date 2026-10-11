<template>
  <div 
    class="health-assistant-trigger"
    @mouseenter="isHovered = true"
    @mouseleave="isHovered = false"
    @click="goToSmartQA"
  >
    <div :class="['assistant-container', { 'is-active': isHovered }]">
      <!-- 动态卡通小人 -->
      <div class="character-wrapper">
        <div class="cartoon-character">
          <!-- 这里使用一个可爱的机器人助手形象，带呼吸动画 -->
          <img 
            src="https://api.dicebear.com/7.x/bottts/svg?seed=medifood&backgroundColor=b6e3f4" 
            alt="Assistant"
            class="character-img"
          />
          <div class="status-dot"></div>
        </div>
      </div>

      <!-- 气泡提示 -->
      <transition name="fade-slide">
        <div class="assistant-popover" v-if="isHovered">
          <div class="popover-content">
            <p class="title">药食助手</p>
            <p class="desc">点我开启智能咨询 ✨</p>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const isHovered = ref(false)

const goToSmartQA = () => {
  router.push('/smart-qa')
}
</script>

<style scoped lang="scss">
.health-assistant-trigger {
  position: fixed;
  right: 15px;
  bottom: 120px;
  z-index: 2000;
  cursor: pointer;
}

.assistant-container {
  position: relative;
  display: flex;
  align-items: center;
  flex-direction: row-reverse; // 气泡在左，人物在右
}

.character-wrapper {
  position: relative;
  width: 70px;
  height: 70px;
  transition: transform 0.3s ease;
  
  &:hover {
    transform: scale(1.1);
  }
}

.cartoon-character {
  width: 100%;
  height: 100%;
  background: white;
  border-radius: 50%;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  border: 3px solid #409eff;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: float 3s ease-in-out infinite; // 呼吸浮动动画

  .character-img {
    width: 85%;
    height: 85%;
    object-fit: contain;
  }
}

.status-dot {
  position: absolute;
  right: 5px;
  bottom: 5px;
  width: 14px;
  height: 14px;
  background: #67c23a;
  border: 2px solid white;
  border-radius: 50%;
  box-shadow: 0 0 5px rgba(103, 194, 58, 0.5);
}

.assistant-popover {
  margin-right: 15px;
  background: white;
  padding: 10px 15px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border: 1px solid #ebeef5;
  position: relative;

  // 气泡小三角
  &::after {
    content: '';
    position: absolute;
    right: -6px;
    top: 50%;
    transform: translateY(-50%);
    border-top: 6px solid transparent;
    border-bottom: 6px solid transparent;
    border-left: 6px solid white;
  }

  .title {
    font-size: 14px;
    font-weight: bold;
    color: #303133;
    margin: 0;
  }

  .desc {
    font-size: 12px;
    color: #606266;
    margin: 4px 0 0 0;
    white-space: nowrap;
  }
}

// 浮动动画
@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-8px); }
  100% { transform: translateY(0px); }
}

// 气泡淡入淡出动画
.fade-slide-enter-active, .fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from, .fade-slide-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
