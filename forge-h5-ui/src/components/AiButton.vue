<template>
  <button
    class="ai-button"
    :class="[
      `ai-button--${variant}`,
      `ai-button--${size}`,
      {
        'ai-button--block': block,
        'ai-button--loading': loading,
        'ai-button--disabled': disabled || loading
      }
    ]"
    :disabled="disabled || loading"
    :hover-class="disabled || loading ? 'none' : 'ai-button--hover'"
    @click="handleClick"
  >
    <view class="ai-button__content">
      <view v-if="loading" class="ai-button__spinner" />
      <view v-else-if="$slots.leftIcon" class="ai-button__icon ai-button__icon--left">
        <slot name="leftIcon" />
      </view>
      <text class="ai-button__text"><slot /></text>
      <view v-if="!loading && $slots.rightIcon" class="ai-button__icon ai-button__icon--right">
        <slot name="rightIcon" />
      </view>
    </view>
  </button>
</template>

<script setup>
const props = defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: value => ['primary', 'secondary', 'outline', 'ghost', 'danger'].includes(value)
  },
  size: {
    type: String,
    default: 'md',
    validator: value => ['sm', 'md', 'lg'].includes(value)
  },
  loading: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  block: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

const handleClick = (event) => {
  if (props.disabled || props.loading) return
  emit('click', event)
}
</script>

<style lang="scss" scoped>
.ai-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-width: 0;
  margin: 0;
  padding: 0 36rpx;
  border: 1px solid transparent;
  overflow: hidden;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  transition: transform 0.2s ease, box-shadow 0.25s ease, background 0.25s ease, opacity 0.2s ease;
  transform: translateZ(0);
  
  &::after {
    border: 0;
  }

  &--hover {
    transform: scale(0.97);
  }

  &--block {
    width: 100%;
  }

  &--disabled {
    opacity: 0.6;
  }

  &--sm {
    height: 72rpx;
    padding: 0 32rpx;
    border-radius: var(--radius-control);
    font-size: 26rpx;
  }

  &--md {
    height: 96rpx;
    border-radius: var(--radius-control);
    font-size: 30rpx;
  }

  &--lg {
    height: 112rpx;
    padding: 0 64rpx;
    border-radius: 16rpx;
    font-size: 32rpx;
  }

  &--primary {
    color: #ffffff;
    background: var(--primary-color);
    box-shadow: none;
  }

  &--secondary {
    color: #334155;
    background: #fff;
    border-color: var(--border-color);
    box-shadow: none;
  }

  &--outline {
    color: #334155;
    background: #fff;
    border-color: var(--border-color);
  }

  &--ghost {
    color: #475569;
    background: transparent;
  }

  &--danger {
    color: #ef4444;
    background: #fff;
    border-color: #ffccc7;
  }
}

.ai-button__content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  min-width: 0;
}

.ai-button__text {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-button__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-button__spinner {
  width: 34rpx;
  height: 34rpx;
  border: 4rpx solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: ai-button-spin 0.8s linear infinite;
}


@keyframes ai-button-spin {
  to {
    transform: rotate(360deg);
  }
}

</style>
