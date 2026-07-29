<template>
  <view class="ai-tabs">
    <view class="ai-tabs-header">
      <view
        v-for="(tab, index) in tabs" 
        :key="index"
        class="ai-tabs-tab"
        :class="{ 'ai-tabs-tab--active': activeIndex === index }"
        @click="handleTabClick(index)"
      >
        <text class="ai-tabs-tab-text">{{tab.label || tab}}</text>
      </view>
    </view>
    <view class="ai-tabs-content">
      <slot></slot>
    </view>
  </view>
</template>

<script setup>
import { computed, provide } from 'vue'

const props = defineProps({
  tabs: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const activeIndex = computed(() => props.modelValue)

const handleTabClick = (index) => {
  emit('update:modelValue', index)
  emit('change', index)
}

provide('activeIndex', activeIndex)
</script>

<style lang="scss" scoped>
.ai-tabs {
  &-header {
    gap: 6rpx;
    padding: 6rpx;
    display: flex;
    border: 1rpx solid #e5e7eb;
    border-radius: 16rpx;
    background: #f5f7fa;
  }
  
  &-tab {
    flex: 1;
    display: flex;
    height: 62rpx;
    align-items: center;
    justify-content: center;
    border-radius: 11rpx;
    
    &--active {
      color: var(--primary-color, #2563eb);
      background: #fff;
      box-shadow: 0 1rpx 4rpx rgb(15 23 42 / 8%);
    }
  }
  
  &-tab-text {
    color: #64748b;
    font-size: 25rpx;
    
    .ai-tabs-tab--active & {
      color: var(--primary-color, #2563eb);
      font-weight: 650;
    }
  }
}
</style>
