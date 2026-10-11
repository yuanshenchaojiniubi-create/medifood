<template>
  <div class="vr-overlay" v-if="visible" @mousedown.prevent @touchstart.prevent>
    <div class="vr-recording" :class="{ cancelling: isCancelling }">
      <div class="vr-waves">
        <span v-for="i in 7" :key="i" class="vr-wave" :style="{ animationDelay: (i * 0.08) + 's', height: waveHeight(i) + 'px' }" />
      </div>
      <div class="vr-time">{{ formatTime(recordingSeconds) }}</div>
      <div class="vr-hint" v-if="isCancelling">松开取消发送</div>
      <div class="vr-hint" v-else>松开发送，上滑取消</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadVoice } from '@/api/message'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue', 'send'])

const visible = ref(false)
const recordingSeconds = ref(0)
const isCancelling = ref(false)
const uploading = ref(false)

let mediaRecorder = null
let chunks = []
let timer = null
let audioBlob = null
let startY = 0
let cancelThreshold = 80

const micSupported = ref(!!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia))

watch(() => props.modelValue, (v) => {
  if (v) {
    startRecord()
  }
})

const formatTime = (s) => {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}:${String(sec).padStart(2, '0')}`
}

const waveHeight = (i) => {
  if (!isCancelling.value) {
    const base = [12, 20, 14, 24, 16, 20, 12]
    return base[i - 1] || 14
  }
  return 8
}

const startRecord = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : 'audio/webm'

    mediaRecorder = new MediaRecorder(stream, { mimeType })
    chunks = []

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data)
    }

    mediaRecorder.onstop = async () => {
      stream.getTracks().forEach(t => t.stop())
      if (isCancelling.value || recordingSeconds.value < 1) {
        cleanup()
        return
      }
      const blob = new Blob(chunks, { type: mimeType })
      audioBlob.value = blob
      await uploadAndSend(blob)
    }

    mediaRecorder.start(100)
    visible.value = true
    recordingSeconds.value = 0
    isCancelling.value = false

    timer = setInterval(() => {
      recordingSeconds.value++
      if (recordingSeconds.value >= 60) {
        stopRecord()
      }
    }, 1000)

    document.addEventListener('mouseup', onRelease)
    document.addEventListener('touchend', onRelease)
    document.addEventListener('mousemove', onMove)
    document.addEventListener('touchmove', onMove, { passive: false })
  } catch {
    ElMessage.error('无法访问麦克风，请检查权限设置')
    emit('update:modelValue', false)
  }
}

const onMove = (e) => {
  if (!visible.value) return
  const y = e.touches ? e.touches[0].clientY : e.clientY
  if (startY === 0) {
    startY = y
  }
  isCancelling.value = (startY - y) > cancelThreshold
}

const onRelease = () => {
  document.removeEventListener('mouseup', onRelease)
  document.removeEventListener('touchend', onRelease)
  document.removeEventListener('mousemove', onMove)
  document.removeEventListener('touchmove', onMove)
  startY = 0
  stopRecord()
}

const stopRecord = () => {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

const uploadAndSend = async (blob) => {
  uploading.value = true
  try {
    const res = await uploadVoice(blob)
    if (res.code === 200 && res.data?.url) {
      emit('send', {
        audioUrl: res.data.url,
        duration: recordingSeconds.value,
        transcription: ''
      })
    } else {
      ElMessage.error('语音上传失败')
    }
  } catch {
    ElMessage.error('语音上传失败，请重试')
  } finally {
    uploading.value = false
    cleanup()
  }
}

const cleanup = () => {
  visible.value = false
  recordingSeconds.value = 0
  isCancelling.value = false
  audioBlob.value = null
  emit('update:modelValue', false)
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('mouseup', onRelease)
  document.removeEventListener('touchend', onRelease)
  document.removeEventListener('mousemove', onMove)
  document.removeEventListener('touchmove', onMove)
})
</script>

<style scoped>
.vr-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

.vr-recording {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 32px 48px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  transition: all 0.2s;
}

.vr-recording.cancelling {
  background: #fef0f0;
  box-shadow: 0 8px 32px rgba(245, 108, 108, 0.2);
}

.vr-waves {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 32px;
}

.vr-wave {
  width: 4px;
  background: #07c160;
  border-radius: 2px;
  animation: vr-wave 0.8s ease-in-out infinite alternate;
}

.vr-recording.cancelling .vr-wave {
  background: #f56c6c;
  animation: none;
}

@keyframes vr-wave {
  0% { transform: scaleY(0.5); }
  100% { transform: scaleY(1); }
}

.vr-time {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}

.vr-recording.cancelling .vr-time {
  color: #f56c6c;
}

.vr-hint {
  font-size: 13px;
  color: #909399;
}

.vr-recording.cancelling .vr-hint {
  color: #f56c6c;
}
</style>