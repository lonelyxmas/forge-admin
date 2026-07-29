<template>
  <view class="ai-tabbar-host">
    <view class="ai-tabbar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="ai-tabbar__item"
        :class="{ 'is-active': currentKey === tab.key }"
        @click="handleTabClick(tab)"
      >
        <view v-if="currentKey === tab.key" class="ai-tabbar__active" />
        <view class="ai-tabbar__icon" :style="iconMask(tab.icon, currentKey === tab.key ? '#2563eb' : '#94a3b8')" />
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { resolveStaticUrl } from '@/utils/assets'

const props = defineProps({
  active: {
    type: String,
    default: '',
  },
})

const tabs = [
  {
    key: 'home',
    label: '首页',
    path: '/pages/index/index',
    icon: '/static/icons/ai-icon/home.svg',
  },
  {
    key: 'todo',
    label: '待办',
    path: '/pages/todo',
    icon: '/static/icons/ai-icon/check-square.svg',
  },
  {
    key: 'mine',
    label: '我的',
    path: '/pages/mine/index',
    icon: '/static/icons/ai-icon/user.svg',
  },
]

const currentKey = computed(() => {
  if (props.active) {
    return props.active
  }
  const pages = getCurrentPages()
  const route = pages[pages.length - 1]?.route || ''
  const matched = tabs.find(tab => route === tab.path.replace(/^\//, ''))
  return matched?.key || 'home'
})

onMounted(() => {
  hideNativeTabBar()
})

onShow(() => {
  hideNativeTabBar()
})

function hideNativeTabBar() {
  if (typeof uni === 'undefined' || typeof uni.hideTabBar !== 'function') {
    return
  }
  uni.hideTabBar({
    animation: false,
    fail: () => {},
  })
}

function iconMask(icon, color) {
  const url = resolveStaticUrl(icon)
  return {
    backgroundColor: color,
    WebkitMask: `url(${url}) center / contain no-repeat`,
    mask: `url(${url}) center / contain no-repeat`,
  }
}

function handleTabClick(tab) {
  if (tab.key === currentKey.value) {
    return
  }
  uni.switchTab({ url: tab.path })
}
</script>

<style lang="scss" scoped>
.ai-tabbar-host {
  position: fixed;
  right: 0;
  bottom: calc(28rpx + env(safe-area-inset-bottom));
  left: 0;
  z-index: 80;
  display: flex;
  justify-content: center;
  padding: 20rpx 48rpx 0;
  pointer-events: none;
  animation: tabbarEnter 0.42s ease both;
}

.ai-tabbar {
  position: relative;
  display: flex;
  width: 560rpx;
  max-width: 78vw;
  padding: 12rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.9);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow:
    0 24rpx 54rpx rgba(15, 23, 42, 0.1),
    inset 0 0 0 1rpx rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(30rpx);
  pointer-events: auto;
}

.ai-tabbar__item {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 999rpx;
  background: transparent;
  line-height: 1;
  transition: transform 0.24s ease;
}

.ai-tabbar__item::after {
  display: none;
}

.ai-tabbar__item:active {
  transform: scale(0.96);
}

.ai-tabbar__active {
  position: absolute;
  inset: 0;
  border: 1rpx solid rgba(226, 232, 240, 0.72);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.94);
  box-shadow:
    0 8rpx 24rpx rgba(15, 23, 42, 0.06),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.9);
  animation: activeTabIn 0.28s ease both;
}

.ai-tabbar__icon {
  position: relative;
  z-index: 1;
  width: 44rpx;
  height: 44rpx;
  transition: background-color 0.24s ease;
}

.ai-tabbar__item.is-active .ai-tabbar__icon {
  transform: none;
}

@keyframes tabbarEnter {
  from {
    opacity: 0;
    transform: translateY(44rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes activeTabIn {
  from {
    opacity: 0;
    transform: scale(0.92);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

</style>
