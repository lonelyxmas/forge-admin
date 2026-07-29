<template>
  <view v-if="modelValue" class="ai-popup-sheet" :style="{ zIndex }">
    <view v-if="mask" class="ai-popup-sheet__mask" @click="handleMaskClick" />
    <view
      class="ai-popup-sheet__panel"
      :class="[`ai-popup-sheet__panel--${placement}`, { 'ai-popup-sheet__panel--round': round }]"
      :style="{ maxHeight }"
    >
      <view v-if="showHandle" class="ai-popup-sheet__handle" />

      <slot name="header">
        <view class="ai-popup-sheet__head">
          <view class="ai-popup-sheet__title-block">
            <text v-if="title" class="ai-popup-sheet__title">{{ title }}</text>
            <text v-if="description" class="ai-popup-sheet__desc">{{ description }}</text>
          </view>
          <button v-if="showClose" class="ai-popup-sheet__close" @click="close">
            <text>×</text>
          </button>
        </view>
      </slot>

      <scroll-view
        v-if="scroll"
        class="ai-popup-sheet__body"
        scroll-y
        :show-scrollbar="false"
        :style="{ maxHeight: bodyMaxHeight }"
      >
        <view class="ai-popup-sheet__content">
          <slot />
        </view>
      </scroll-view>
      <view v-else class="ai-popup-sheet__content">
        <slot />
      </view>

      <view v-if="$slots.footer" class="ai-popup-sheet__footer">
        <slot name="footer" />
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  placement: {
    type: String,
    default: 'bottom',
    validator: value => ['bottom'].includes(value)
  },
  maxHeight: {
    type: String,
    default: '78vh'
  },
  bodyMaxHeight: {
    type: String,
    default: 'calc(78vh - 172rpx - env(safe-area-inset-bottom))'
  },
  zIndex: {
    type: [Number, String],
    default: 9990
  },
  mask: {
    type: Boolean,
    default: true
  },
  closeOnMask: {
    type: Boolean,
    default: true
  },
  showClose: {
    type: Boolean,
    default: true
  },
  showHandle: {
    type: Boolean,
    default: true
  },
  scroll: {
    type: Boolean,
    default: true
  },
  round: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'close', 'maskClick'])

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function handleMaskClick() {
  emit('maskClick')
  if (props.closeOnMask) {
    close()
  }
}
</script>

<style lang="scss" scoped>
.ai-popup-sheet {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.ai-popup-sheet__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  animation: ai-popup-sheet-fade 0.18s ease-out both;
}

.ai-popup-sheet__panel {
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 16rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
  border: 0;
  background: #fff;
  box-shadow: 0 -8rpx 24rpx rgba(31, 35, 41, 0.12);
  box-sizing: border-box;
  animation: ai-popup-sheet-up 0.22s cubic-bezier(0.2, 0.9, 0.2, 1) both;
}

.ai-popup-sheet__panel--bottom.ai-popup-sheet__panel--round {
  border-radius: 24rpx 24rpx 0 0;
}

.ai-popup-sheet__handle {
  width: 74rpx;
  height: 8rpx;
  margin: 0 auto 22rpx;
  border-radius: 8rpx;
  background: #c9cdd4;
}

.ai-popup-sheet__head {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.ai-popup-sheet__title-block {
  min-width: 0;
  flex: 1;
}

.ai-popup-sheet__title,
.ai-popup-sheet__desc {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-popup-sheet__title {
  color: #1e293b;
  font-size: 34rpx;
  font-weight: 950;
  line-height: 1.18;
  white-space: nowrap;
}

.ai-popup-sheet__desc {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.4;
  white-space: normal;
}

.ai-popup-sheet__close {
  display: flex;
  width: 68rpx;
  height: 68rpx;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: 1rpx solid rgba(226, 232, 240, 0.9);
  border-radius: 999rpx;
  color: #475569;
  font-size: 42rpx;
  font-weight: 500;
  line-height: 1;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8rpx 22rpx rgba(15, 23, 42, 0.06);
}

.ai-popup-sheet__close::after {
  border: 0;
}

.ai-popup-sheet__body {
  min-height: 0;
}

.ai-popup-sheet__content {
  padding-bottom: 8rpx;
}

.ai-popup-sheet__footer {
  padding-top: 22rpx;
}

@keyframes ai-popup-sheet-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes ai-popup-sheet-up {
  from {
    opacity: 0;
    transform: translateY(32rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
