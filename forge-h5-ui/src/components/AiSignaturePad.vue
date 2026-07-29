<template>
  <view class="ai-signature-pad">
    <canvas
      ref="canvasRef"
      class="ai-signature-pad__canvas"
      :style="{ height: `${height}rpx` }"
      @touchstart.stop.prevent="startStroke"
      @touchmove.stop.prevent="moveStroke"
      @touchend.stop.prevent="endStroke"
    />
    <text v-if="!hasInk && !modelValue" class="ai-signature-pad__placeholder">请在此区域手写签名</text>
    <button class="ai-signature-pad__clear" :disabled="!hasSignature()" @click="clear">清空</button>
  </view>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useAuthStore } from '@/store'

const props = defineProps({ modelValue: { type: String, default: '' }, height: { type: Number, default: 260 } })
const emit = defineEmits(['update:modelValue'])
const authStore = useAuthStore()
const canvasRef = ref(null)
const hasInk = ref(false)
let context
let drawing = false
let lastPoint

onMounted(() => nextTick(() => {
  context = canvasRef.value?.getContext?.('2d')
  if (!context) return
  context.lineWidth = 3
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.strokeStyle = '#1f2937'
}))

function point(event) {
  const touch = event.touches?.[0] || event.changedTouches?.[0]
  const rect = canvasRef.value?.getBoundingClientRect?.()
  if (!touch || !rect) return null
  return { x: touch.clientX - rect.left, y: touch.clientY - rect.top }
}
function startStroke(event) {
  lastPoint = point(event)
  drawing = Boolean(lastPoint)
  if (!drawing || !context) return
  hasInk.value = true
  if (props.modelValue) emit('update:modelValue', '')
  context.beginPath()
  context.arc(lastPoint.x, lastPoint.y, 1.5, 0, Math.PI * 2)
  context.fill()
}
function moveStroke(event) {
  if (!drawing || !context) return
  const current = point(event)
  if (!current || !lastPoint) return
  context.beginPath()
  context.moveTo(lastPoint.x, lastPoint.y)
  context.lineTo(current.x, current.y)
  context.stroke()
  lastPoint = current
}
function endStroke() { drawing = false; lastPoint = null }
function clear() {
  const rect = canvasRef.value?.getBoundingClientRect?.()
  if (context && rect) context.clearRect(0, 0, rect.width, rect.height)
  hasInk.value = false
  emit('update:modelValue', '')
}
function hasSignature() { return Boolean(props.modelValue) || hasInk.value }
async function upload() {
  if (props.modelValue && !hasInk.value) return props.modelValue
  if (!hasInk.value) throw new Error('请完成手写签名')
  const dataUrl = canvasRef.value?.toDataURL?.('image/png')
  if (!dataUrl) throw new Error('签名画布未初始化')
  const blob = await (await fetch(dataUrl)).blob()
  const formData = new FormData()
  formData.append('file', blob, `signature-${Date.now()}.png`)
  formData.append('businessType', 'flow_signature')
  formData.append('isPrivate', 'true')
  const response = await fetch(`${import.meta.env.VITE_REQUEST_PREFIX || ''}/api/file/upload`, { method: 'POST', headers: { Authorization: `${authStore.tokenType || 'Bearer'} ${authStore.accessToken}` }, body: formData })
  const result = await response.json()
  const fileId = result?.data?.fileId || result?.data?.id
  if (!response.ok || !(result?.code === 200 || result?.respCode === '0000') || !fileId) throw new Error(result?.message || '签名图片保存失败')
  emit('update:modelValue', String(fileId))
  hasInk.value = false
  return String(fileId)
}
defineExpose({ hasSignature, upload, clear })
</script>

<style lang="scss" scoped>
.ai-signature-pad { position: relative; overflow: hidden; border: 1rpx dashed #9fc5fb; border-radius: 12rpx; background: #fbfdff; }
.ai-signature-pad__canvas { display: block; width: 100%; touch-action: none; }
.ai-signature-pad__placeholder { position: absolute; top: 50%; left: 50%; color: #94a3b8; font-size: 23rpx; transform: translate(-50%, -50%); pointer-events: none; }
.ai-signature-pad__clear { position: absolute; top: 12rpx; right: 12rpx; height: 46rpx; margin: 0; padding: 0 12rpx; border: 0; border-radius: 8rpx; color: #64748b; font-size: 21rpx; background: #eef4fb; }
.ai-signature-pad__clear::after { border: 0; }
</style>
