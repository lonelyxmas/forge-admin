<!--
  表格工具栏操作组件
  提供刷新、密度调整、列设置、搜索显示/隐藏等功能
  参考 toolbarAction 设计

  @author AI Form Team
  @version 1.0.0
-->

<template>
  <div class="ai-toolbar-action">
    <!-- 刷新按钮 -->
    <n-tooltip v-if="showRefresh">
      <template #trigger>
        <n-button text @click="handleRefresh">
          <template #icon>
            <n-icon size="18">
              <RefreshOutline />
            </n-icon>
          </template>
        </n-button>
      </template>
      刷新
    </n-tooltip>

    <!-- 密度调整 -->
    <n-dropdown
      v-if="showDensity"
      trigger="click"
      placement="bottom-end"
      :options="densityOptions"
      @select="handleDensityChange"
    >
      <n-button text>
        <template #icon>
          <n-icon size="18">
            <ResizeOutline />
          </n-icon>
        </template>
      </n-button>
    </n-dropdown>

    <!-- 列设置 -->
    <AiTableFilter
      v-if="showColumnFilter"
      :columns="columns"
      :filter-max-height="filterMaxHeight"
      :default-checked="defaultCheckedColumns"
      @filter-change="handleFilterChange"
    />

    <!-- 搜索显示/隐藏 -->
    <n-tooltip v-if="showSearchToggle">
      <template #trigger>
        <n-button
          text
          circle
          class="search-toggle-button"
          :class="{ 'is-active': searchVisible }"
          :aria-label="searchVisible ? '隐藏搜索' : '显示搜索'"
          @click="handleSearchToggle"
        >
          <template #icon>
            <n-icon size="18">
              <SearchOutline />
            </n-icon>
          </template>
        </n-button>
      </template>
      {{ searchVisible ? '隐藏搜索' : '显示搜索' }}
    </n-tooltip>

    <!-- 渲染模式切换 -->
    <div v-if="showRenderModeSwitch" class="render-mode-switch">
      <n-tooltip>
        <template #trigger>
          <button
            type="button"
            class="render-mode-button"
            :class="{ active: renderMode === 'table' }"
            @click="handleRenderModeChange('table')"
          >
            <n-icon size="16">
              <ListOutline />
            </n-icon>
          </button>
        </template>
        列表
      </n-tooltip>
      <n-tooltip>
        <template #trigger>
          <button
            type="button"
            class="render-mode-button"
            :class="{ active: renderMode === 'card' }"
            @click="handleRenderModeChange('card')"
          >
            <n-icon size="16">
              <GridOutline />
            </n-icon>
          </button>
        </template>
        卡片
      </n-tooltip>
    </div>

    <!-- 全屏按钮 -->
    <n-tooltip v-if="showFullscreen">
      <template #trigger>
        <n-button text @click="handleFullscreen">
          <template #icon>
            <n-icon size="18">
              <component :is="isFullscreen ? ContractOutline : ExpandOutline" />
            </n-icon>
          </template>
        </n-button>
      </template>
      {{ isFullscreen ? '退出全屏' : '全屏' }}
    </n-tooltip>

    <!-- 自定义操作插槽 -->
    <slot name="extra" />
  </div>
</template>

<script setup>
/* eslint-disable vue/custom-event-name-casing */
import {
  ContractOutline,
  ExpandOutline,
  GridOutline,
  ListOutline,
  RefreshOutline,
  ResizeOutline,
  SearchOutline,
} from '@vicons/ionicons5'
import { computed, ref } from 'vue'
import AiTableFilter from './AiTableFilter.vue'

/**
 * Props 定义
 */
const props = defineProps({
  /**
   * 表格列配置
   * @type {Array<object>}
   */
  columns: {
    type: Array,
    default: () => [],
  },

  /**
   * 列筛选下拉菜单最大高度
   * @type {string}
   */
  filterMaxHeight: {
    type: String,
    default: '400px',
  },

  /**
   * 默认选中的列
   * @type {Array<string>}
   */
  defaultCheckedColumns: {
    type: Array,
    default: () => [],
  },

  /**
   * 是否显示刷新按钮
   * @type {boolean}
   */
  showRefresh: {
    type: Boolean,
    default: true,
  },

  /**
   * 是否显示密度调整
   * @type {boolean}
   */
  showDensity: {
    type: Boolean,
    default: true,
  },

  /** 当前表格密度，仅用于在下拉菜单标记当前项。 */
  density: {
    type: String,
    default: 'medium',
    validator: value => ['small', 'medium', 'large'].includes(value),
  },

  /**
   * 是否显示列设置
   * @type {boolean}
   */
  showColumnFilter: {
    type: Boolean,
    default: true,
  },

  /**
   * 是否显示搜索切换
   * @type {boolean}
   */
  showSearchToggle: {
    type: Boolean,
    default: true,
  },

  /**
   * 是否显示全屏按钮
   * @type {boolean}
   */
  showFullscreen: {
    type: Boolean,
    default: false,
  },

  /**
   * 搜索表单是否可见
   * @type {boolean}
   */
  searchVisible: {
    type: Boolean,
    default: true,
  },

  /**
   * 当前渲染模式
   * @type {'table'|'card'}
   */
  renderMode: {
    type: String,
    default: 'table',
    validator: value => ['table', 'card'].includes(value),
  },

  /**
   * 是否显示列表/卡片切换
   * @type {boolean}
   */
  showRenderModeSwitch: {
    type: Boolean,
    default: true,
  },
})

/**
 * Emits 定义
 */
const emit = defineEmits([
  'refresh', // 刷新
  'density-change', // 密度变化
  'filter-change', // 列筛选变化
  'search-toggle', // 搜索显示/隐藏切换
  'fullscreen-change', // 全屏状态变化
  'render-mode-change', // 渲染模式变化
])

/**
 * 密度选项
 */
const densityOptions = computed(() => [
  {
    label: props.density === 'small' ? '紧凑（当前）' : '紧凑',
    key: 'small',
  },
  {
    label: props.density === 'medium' ? '默认（当前）' : '默认',
    key: 'medium',
  },
  {
    label: props.density === 'large' ? '宽松（当前）' : '宽松',
    key: 'large',
  },
])

/**
 * 是否全屏
 */
const isFullscreen = ref(false)

/**
 * 刷新
 */
function handleRefresh() {
  emit('refresh')
}

/**
 * 密度变化
 */
function handleDensityChange(key) {
  emit('density-change', key)
}

/**
 * 列筛选变化
 */
function handleFilterChange(columns) {
  emit('filter-change', columns)
}

/**
 * 搜索显示/隐藏切换
 */
function handleSearchToggle() {
  emit('search-toggle', !props.searchVisible)
}

/**
 * 渲染模式切换
 */
function handleRenderModeChange(mode) {
  if (mode === props.renderMode)
    return
  emit('render-mode-change', mode)
}

/**
 * 全屏切换
 */
function handleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  emit('fullscreen-change', isFullscreen.value)
}

/**
 * 暴露方法
 */
defineExpose({
  /**
   * 切换全屏
   */
  toggleFullscreen: handleFullscreen,

  /**
   * 获取全屏状态
   */
  getFullscreenStatus: () => isFullscreen.value,
})
</script>

<style scoped>
.ai-toolbar-action {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-base);
}

.search-toggle-button {
  width: 28px;
  height: 28px;
  color: var(--text-tertiary);
  transition:
    color 0.16s ease,
    background 0.16s ease;
}

.search-toggle-button:hover {
  color: var(--text-primary);
  background: rgb(15 23 42 / 5%);
}

.search-toggle-button.is-active {
  color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 10%, transparent);
}

.search-toggle-button.is-active:hover {
  color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 14%, transparent);
}

.render-mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 28px;
  padding: 2px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-primary);
}

.render-mode-button {
  width: 26px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: var(--text-tertiary);
  background: transparent;
  cursor: pointer;
  transition:
    color 0.16s ease,
    background 0.16s ease,
    box-shadow 0.16s ease;
}

.render-mode-button:hover {
  color: var(--text-primary);
  background: rgb(15 23 42 / 5%);
}

.render-mode-button.active {
  color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 10%, transparent);
  box-shadow: none;
}

.render-mode-button:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--primary-color) 42%, transparent);
  outline-offset: 2px;
}
</style>
