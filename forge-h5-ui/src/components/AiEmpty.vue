<template>
  <view class="ai-empty">
    <image class="ai-empty__image" :src="emptyImage" mode="aspectFit" />
    <text class="ai-empty__title">{{ title }}</text>
    <text v-if="description" class="ai-empty__desc">{{ description }}</text>
    <view v-if="$slots.default" class="ai-empty__action">
      <slot />
    </view>
  </view>
</template>

<script setup>
import emptyImage from '@/static/images/no-data.png'

defineProps({
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: ''
  },
  icon: {
    type: String,
    default: 'inbox'
  },
})
</script>

<style lang="scss" scoped>
.ai-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 420rpx;
  padding: 36rpx 32rpx;
  box-sizing: border-box;
  text-align: center;
}

.ai-empty__image {
  width: 260rpx;
  height: 260rpx;
}

.ai-empty__title {
  display: block;
  margin-top: 8rpx;
  color: var(--text-strong);
  font-size: 28rpx;
  font-weight: 700;
}

.ai-empty__desc {
  display: block;
  max-width: 520rpx;
  margin-top: 8rpx;
  color: var(--text-muted);
  font-size: 23rpx;
  font-weight: 500;
  line-height: 1.45;
}

.ai-empty__action {
  margin-top: 18rpx;
}
</style>
