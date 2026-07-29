<template>
  <view class="ai-file-upload">
    <view v-if="fileItems.length" class="ai-file-upload__list">
      <view v-for="item in fileItems" :key="item.id" class="ai-file-upload__item">
        <AiIcon icon="/static/icons/ai-icon/file-text.svg" color="#2563eb" size="sm" />
        <text class="ai-file-upload__name">{{ item.name }}</text>
        <button v-if="!readonly" class="ai-file-upload__remove" @click="removeFile(item.id)">移除</button>
      </view>
    </view>
    <button v-if="!readonly" class="ai-file-upload__trigger" :disabled="uploading" @click="chooseFile">
      <AiIcon icon="/static/icons/ai-icon/upload.svg" color="#2563eb" size="sm" />
      <text>{{ uploading ? '上传中…' : '上传附件' }}</text>
    </button>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import AiIcon from '@/components/AiIcon.vue'
import { useAuthStore } from '@/store'
import { toast } from '@/utils/notify'

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  readonly: { type: Boolean, default: false },
  businessType: { type: String, default: 'flow_attachment' },
  maxCount: { type: Number, default: 9 },
})
const emit = defineEmits(['update:modelValue', 'success'])
const authStore = useAuthStore()
const uploading = ref(false)

const fileItems = computed(() => normalizeFiles(props.modelValue))

async function chooseFile() {
  if (uploading.value || fileItems.value.length >= props.maxCount) return
  try {
    const picked = await pickFile()
    if (!picked?.file) return
    uploading.value = true
    const uploaded = await uploadFile(picked.file, picked.name)
    const next = [...fileItems.value, uploaded]
    emitValue(next)
    emit('success', uploaded)
  }
  catch (error) {
    console.error('上传附件失败:', error)
    toast(error?.message || '附件上传失败', { type: 'error' })
  }
  finally { uploading.value = false }
}

function pickFile() {
  return new Promise((resolve) => {
    // #ifdef H5
    const input = document.createElement('input')
    input.type = 'file'
    input.onchange = () => {
      const file = input.files?.[0]
      resolve(file ? { file, name: file.name } : null)
    }
    input.click()
    // #endif
    // #ifndef H5
    uni.chooseMessageFile({
      count: 1,
      type: 'file',
      success: (res) => {
        const item = res.tempFiles?.[0]
        resolve(item ? { file: item, name: item.name || item.path?.split('/').pop() } : null)
      },
      fail: () => resolve(null),
    })
    // #endif
  })
}

async function uploadFile(file, name) {
  const formData = new FormData()
  formData.append('file', file, name || file.name || `attachment-${Date.now()}`)
  formData.append('businessType', props.businessType)
  formData.append('isPrivate', 'true')
  const response = await fetch(`${import.meta.env.VITE_REQUEST_PREFIX || ''}/api/file/upload`, {
    method: 'POST',
    headers: { Authorization: `${authStore.tokenType || 'Bearer'} ${authStore.accessToken}` },
    body: formData,
  })
  const result = await response.json()
  if (!response.ok || !(result?.code === 200 || result?.respCode === '0000')) throw new Error(result?.message || result?.msg || '附件上传失败')
  const data = result?.data || {}
  const id = data.fileId || data.id || data.filePath
  if (!id) throw new Error('附件服务未返回文件标识')
  return { id: String(id), name: data.originalName || data.fileName || name || String(id) }
}

function removeFile(id) { emitValue(fileItems.value.filter(item => item.id !== id)) }
function emitValue(items) { emit('update:modelValue', items.map(item => item.id).join(',')) }
function normalizeFiles(value) {
  if (!value) return []
  if (Array.isArray(value)) return value.map(item => typeof item === 'object' ? { id: String(item.id || item.fileId || item.value || ''), name: item.name || item.fileName || item.originalName || String(item.id || item.fileId || '') } : { id: String(item), name: String(item) }).filter(item => item.id)
  return String(value).split(',').map(value => value.trim()).filter(Boolean).map(id => ({ id, name: id }))
}
</script>

<style lang="scss" scoped>
.ai-file-upload { display: flex; flex-direction: column; gap: 12rpx; }
.ai-file-upload__list { display: flex; flex-direction: column; gap: 10rpx; }
.ai-file-upload__item { display: flex; min-width: 0; height: 64rpx; align-items: center; gap: 12rpx; padding: 0 14rpx; border: 1rpx solid #e4eaf1; border-radius: 10rpx; background: #f8fbff; }
.ai-file-upload__name { overflow: hidden; flex: 1; color: #475569; font-size: 23rpx; text-overflow: ellipsis; white-space: nowrap; }
.ai-file-upload__remove { margin: 0; padding: 0; border: 0; color: #64748b; font-size: 22rpx; line-height: 1; background: transparent; }
.ai-file-upload__remove::after, .ai-file-upload__trigger::after { border: 0; }
.ai-file-upload__trigger { display: inline-flex; width: fit-content; height: 70rpx; align-items: center; gap: 10rpx; margin: 0; padding: 0 18rpx; border: 1rpx dashed #9fc5fb; border-radius: 10rpx; color: #2563eb; font-size: 24rpx; background: #f8fbff; }
.ai-file-upload__trigger[disabled] { opacity: .6; }
</style>
