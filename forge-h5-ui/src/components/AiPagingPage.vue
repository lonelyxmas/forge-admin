<template>
  <AiPage 
    :title="title" 
    :isNavBar="isNavBar"
    :pullRefresh="true"
    :isPage="true"
    :isRouterBack="isRouterBack"
    :leftArrow="leftArrow"
    :scrollHeight="scrollHeight"
    :marginTop="marginTop"
    @refresh="onRefresh"
    @load="onLoad"
  >
    <template #top v-if="$slots.top">
      <slot name="top"></slot>
    </template>
    
    <view class="list-content">
      <view v-for="(item, index) in dataSource" :key="index">
        <slot :item="item" :index="index"></slot>
      </view>
      
      <AiListSkeleton v-if="refreshing" :rows="dataSource.length ? 2 : 5" :compact="dataSource.length > 0" />
      <view class="loading-text" v-if="finished && dataSource.length > 0">没有更多了</view>
      <view class="empty-tip" v-if="finished && dataSource.length === 0">暂无数据</view>
    </view>
    
    <template #footer v-if="$slots.footer">
      <slot name="footer"></slot>
    </template>
  </AiPage>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AiPage from './AiPage.vue'
import AiListSkeleton from './AiListSkeleton.vue'
import { usePaging } from '@/composables'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  api: {
    type: String,
    required: true
  },
  isNavBar: {
    type: Boolean,
    default: true
  },
  isRouterBack: {
    type: Boolean,
    default: false
  },
  leftArrow: {
    type: Boolean,
    default: true
  },
  scrollHeight: {
    type: String,
    default: ''
  },
  marginTop: {
    type: String,
    default: ''
  },
  initialParams: {
    type: Object,
    default: () => ({})
  },
  loadingText: {
    type: String,
    default: '加载中...'
  },
  beforeRenderList: {
    type: Function,
    default: null
  }
})

const {
  dataSource,
  pageNum,
  pageSize,
  total,
  finished,
  refreshing,
  queryFormData,
  refresh,
  load,
  searchPage
} = usePaging(props.api, props.beforeRenderList)

const onRefresh = () => {
  refresh()
}

const onLoad = () => {
  load()
}

onMounted(() => {
  if (props.initialParams && Object.keys(props.initialParams).length > 0) {
    searchPage(props.initialParams)
  } else {
    refresh()
  }
})

defineExpose({
  refresh,
  searchPage,
  dataSource,
  total
})
</script>

<style lang="scss" scoped>
.list-content {
  padding: 0;
}

.loading-text {
  text-align: center;
  padding: 20rpx;
  color: #999;
  font-size: 28rpx;
}

.empty-tip {
  text-align: center;
  padding: 100rpx 0;
  color: #999;
  font-size: 28rpx;
}
</style>
