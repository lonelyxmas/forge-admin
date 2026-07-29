<template>
  <view class="ai-select" :class="{ 'ai-select--compact': compact }" @click="openPicker">
    <view class="ai-select-input">
      <text class="ai-select-text" :class="{ 'ai-select-placeholder': !selectedLabel }">
        {{ selectedLabel || placeholder }}
      </text>
    </view>
    
    <AiPopupSheet v-model="showPicker" :title="title" :description="description" :z-index="10020">
      <view class="ai-select-options">
        <button
          v-for="option in options"
          :key="String(option.value)"
          class="ai-select-option"
          :class="{ 'ai-select-option--active': isSelected(option) }"
          @click.stop="selectOption(option)"
        >
          <text>{{ option.label }}</text>
          <text v-if="isSelected(option)" class="ai-select-option__check">✓</text>
        </button>
      </view>
    </AiPopupSheet>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AiPopupSheet from './AiPopupSheet.vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  options: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  title: {
    type: String,
    default: '请选择'
  },
  description: {
    type: String,
    default: ''
  },
  compact: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const showPicker = ref(false)

const selectedLabel = computed(() => {
  const selected = props.options.find(o => o.value === props.modelValue)
  return selected ? selected.label : ''
})

function openPicker() {
  if (props.options.length) showPicker.value = true
}

function isSelected(option) {
  return String(option?.value) === String(props.modelValue)
}

function selectOption(option) {
  emit('update:modelValue', option.value)
  emit('change', option.value)
  showPicker.value = false
}

watch(() => props.options, () => {
  if (!props.options.length) showPicker.value = false
}, { deep: true })
</script>

<style lang="scss" scoped>
.ai-select {
  min-width: 160rpx;
  padding: 0 20rpx;
  background: #fff;
  border: 1rpx solid var(--border-color, #e5e7eb);
  border-radius: 12rpx;
  box-sizing: border-box;

  &--compact {
    min-width: 172rpx;
    height: 64rpx;
    padding: 0 18rpx;
    border-radius: 14rpx;
  }
}

.ai-select-input {
  display: flex;
  min-height: 76rpx;
  align-items: center;
  justify-content: space-between;

  .ai-select--compact & {
    min-height: 62rpx;
  }

  &::after {
    width: 12rpx;
    height: 12rpx;
    margin-left: 18rpx;
    border-right: 2rpx solid #94a3b8;
    border-bottom: 2rpx solid #94a3b8;
    content: '';
    transform: translateY(-3rpx) rotate(45deg);
  }
}

.ai-select-text {
  overflow: hidden;
  color: var(--text-strong, #1f2937);
  font-size: 25rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
  
  &.ai-select-placeholder {
    color: #999;
  }
}

.ai-select-options {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.ai-select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 78rpx;
  margin: 0;
  padding: 0 18rpx;
  border: 1rpx solid #edf0f3;
  border-radius: 12rpx;
  color: #475569;
  font-size: 27rpx;
  text-align: left;
  background: #fff;

  &::after { border: 0; }

  &--active {
    border-color: #bfdbfe;
    color: var(--primary-color, #2563eb);
    font-weight: 650;
    background: #eff6ff;
  }
}

.ai-select-option__check {
  color: var(--primary-color, #2563eb);
  font-size: 30rpx;
}
</style>
