<template>
  <div class="vb-wrap" :class="{ mine }" @click="togglePlay">
    <div class="vb-icon" :class="{ playing: isPlaying }">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
        <path d="M12 3v18M8 8v8M16 6v12M4 11v2M20 10v4"/>
      </svg>
    </div>
    <div class="vb-bars">
      <span v-for="i in barCount" :key="i" class="vb-bar" :class="{ active: isPlaying && i <= activeBar }" :style="{ animationDelay: (i * 0.15) + 's' }" />
    </div>
    <span class="vb-dur">{{ formatTime(duration) }}</span>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'

const props = defineProps({
  audioUrl: { type: String, required: true },
  duration: { type: Number, default: 0 },
  mine: { type: Boolean, default: false }
})

const audio = ref(null)
const isPlaying = ref(false)
const activeBar = ref(0)
let barTimer = null

const barCount = computed(() => {
  const d = Number(props.duration) || 0
  if (d <= 5) return 5
  if (d <= 15) return 8
  if (d <= 30) return 12
  return 15
})

const formatTime = (s) => {
  const dur = Math.floor(Number(s) || 0)
  const m = Math.floor(dur / 60)
  const sec = dur % 60
  return m > 0 ? `${m}'${String(sec).padStart(2, '0')}''` : `${sec}''`
}

const startBarAnim = () => {
  let i = 0
  barTimer = setInterval(() => {
    i = (i % barCount.value) + 1
    activeBar.value = i
  }, 300)
}

const stopBarAnim = () => {
  if (barTimer) {
    clearInterval(barTimer)
    barTimer = null
  }
  activeBar.value = 0
}

const togglePlay = () => {
  if (!audio.value) {
    audio.value = new Audio(props.audioUrl)
    audio.value.onended = () => {
      isPlaying.value = false
      stopBarAnim()
    }
    audio.value.onerror = () => {
      isPlaying.value = false
      stopBarAnim()
    }
  }

  if (isPlaying.value) {
    audio.value.pause()
    audio.value.currentTime = 0
    isPlaying.value = false
    stopBarAnim()
  } else {
    audio.value.currentTime = 0
    audio.value.play().catch(() => {
      isPlaying.value = false
      stopBarAnim()
    })
    isPlaying.value = true
    startBarAnim()
  }
}

onUnmounted(() => {
  stopBarAnim()
  if (audio.value) {
    audio.value.pause()
    audio.value = null
  }
})
</script>

<style scoped>
.vb-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  cursor: pointer;
  user-select: none;
  min-width: 90px;
  transition: background 0.15s;
}

.vb-wrap:hover {
  background: rgba(0, 0, 0, 0.08);
}

.vb-wrap.mine {
  background: rgba(255, 255, 255, 0.2);
}

.vb-wrap.mine:hover {
  background: rgba(255, 255, 255, 0.3);
}

.vb-icon {
  flex-shrink: 0;
  color: #07c160;
  transition: transform 0.15s;
}

.vb-icon.playing {
  animation: vb-icon-pulse 0.8s ease-in-out infinite;
}

@keyframes vb-icon-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.15); }
}

.vb-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 20px;
  flex: 1;
}

.vb-bar {
  width: 3px;
  background: #c0c4cc;
  border-radius: 2px;
  transition: background 0.15s;
}

.vb-bar:nth-child(1) { height: 40%; }
.vb-bar:nth-child(2) { height: 60%; }
.vb-bar:nth-child(3) { height: 35%; }
.vb-bar:nth-child(4) { height: 75%; }
.vb-bar:nth-child(5) { height: 50%; }
.vb-bar:nth-child(6) { height: 65%; }
.vb-bar:nth-child(7) { height: 45%; }
.vb-bar:nth-child(8) { height: 80%; }
.vb-bar:nth-child(9) { height: 55%; }
.vb-bar:nth-child(10) { height: 70%; }
.vb-bar:nth-child(11) { height: 40%; }
.vb-bar:nth-child(12) { height: 60%; }
.vb-bar:nth-child(13) { height: 50%; }
.vb-bar:nth-child(14) { height: 75%; }
.vb-bar:nth-child(15) { height: 45%; }

.vb-bar.active {
  background: #07c160;
}

.vb-wrap.mine .vb-bar {
  background: rgba(255, 255, 255, 0.5);
}

.vb-wrap.mine .vb-bar.active {
  background: #fff;
}

.vb-dur {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  flex-shrink: 0;
}

.vb-wrap.mine .vb-dur {
  color: rgba(255, 255, 255, 0.7);
}
</style>