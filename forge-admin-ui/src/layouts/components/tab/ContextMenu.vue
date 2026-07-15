<template>
  <n-dropdown
    :show="show"
    :options="options"
    :x="x"
    :y="y"
    placement="bottom-start"
    @clickoutside="handleHideDropdown"
    @select="handleSelect"
  />
</template>

<script setup>
import { useTabStore } from '@/store'
import { confirmDirtyTabs, copyText, resolveTabUrl } from '@/utils/tab-interactions'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  currentPath: {
    type: String,
    default: '',
  },
  x: {
    type: Number,
    default: 0,
  },
  y: {
    type: Number,
    default: 0,
  },
})

const emit = defineEmits(['update:show'])

const tabStore = useTabStore()
const router = useRouter()
const currentTab = computed(() => tabStore.tabs.find(item => item.path === props.currentPath || item.key === props.currentPath))
const isCurrentTabPinned = computed(() => Boolean(currentTab.value?.pinned))
const currentTabIndex = computed(() => tabStore.tabs.findIndex(item => item.path === props.currentPath || item.key === props.currentPath))
const activeTab = computed(() => tabStore.tabs.find(item => item.path === tabStore.activeTab || item.key === tabStore.activeTab))
const canCloseCurrentTab = computed(() => {
  if (!currentTab.value || isCurrentTabPinned.value || currentTab.value.closable === false)
    return false
  return Boolean(currentTab.value.forceClosable || tabStore.tabs.length > 1)
})
const hasClosableOtherTabs = computed(() => tabStore.tabs.some((item, index) => index !== currentTabIndex.value && !item.pinned))
const hasClosableLeftTabs = computed(() => tabStore.tabs.some((item, index) => index < currentTabIndex.value && !item.pinned))
const hasClosableRightTabs = computed(() => tabStore.tabs.some((item, index) => index > currentTabIndex.value && !item.pinned))
const hasClosableTabs = computed(() => tabStore.tabs.some(item => !item.pinned))
const allTabsPinned = computed(() => tabStore.tabs.length > 0 && tabStore.tabs.every(item => item.pinned))
const noTabsPinned = computed(() => tabStore.tabs.every(item => !item.pinned))
const canMoveToStart = computed(() => {
  if (!currentTab.value)
    return false
  const sameGroupTabs = tabStore.tabs.filter(item => Boolean(item.pinned) === isCurrentTabPinned.value)
  return sameGroupTabs[0] !== currentTab.value
})
const canMoveToEnd = computed(() => {
  if (!currentTab.value)
    return false
  const sameGroupTabs = tabStore.tabs.filter(item => Boolean(item.pinned) === isCurrentTabPinned.value)
  return sameGroupTabs[sameGroupTabs.length - 1] !== currentTab.value
})

const options = computed(() => [
  {
    label: '重新加载',
    key: 'reload',
    disabled: props.currentPath !== tabStore.activeTab,
    icon: () => h('i', { class: 'ai-icon:refresh-ccw text-14' }),
  },
  {
    label: isCurrentTabPinned.value ? '取消固定' : '固定',
    key: 'toggle-pin',
    icon: () => h('i', { class: 'i-material-symbols:keep-rounded text-14' }),
  },
  {
    label: '恢复刚关闭的标签',
    key: 'restore-closed',
    disabled: tabStore.closedTabs.length === 0,
    icon: () => h('i', { class: 'i-material-symbols:history-rounded text-14' }),
  },
  { type: 'divider', key: 'divider-copy' },
  {
    label: '页面操作',
    key: 'page-actions',
    icon: () => h('i', { class: 'i-material-symbols:link-rounded text-14' }),
    children: [
      {
        label: '复制页面地址',
        key: 'copy-url',
        icon: () => h('i', { class: 'i-material-symbols:link-rounded text-14' }),
      },
      {
        label: '复制页面名称和地址',
        key: 'copy-title-url',
        icon: () => h('i', { class: 'i-material-symbols:content-copy-outline-rounded text-14' }),
      },
      {
        label: '在新窗口打开',
        key: 'open-new-window',
        icon: () => h('i', { class: 'i-material-symbols:open-in-new-rounded text-14' }),
      },
    ],
  },
  {
    label: '标签整理',
    key: 'tab-organize',
    icon: () => h('i', { class: 'i-material-symbols:tab-group-rounded text-14' }),
    children: [
      {
        label: '移动到最左侧',
        key: 'move-start',
        disabled: !canMoveToStart.value,
        icon: () => h('i', { class: 'i-material-symbols:keyboard-double-arrow-left-rounded text-14' }),
      },
      {
        label: '移动到最右侧',
        key: 'move-end',
        disabled: !canMoveToEnd.value,
        icon: () => h('i', { class: 'i-material-symbols:keyboard-double-arrow-right-rounded text-14' }),
      },
      {
        label: '固定全部',
        key: 'pin-all',
        disabled: allTabsPinned.value,
        icon: () => h('i', { class: 'i-material-symbols:keep-rounded text-14' }),
      },
      {
        label: '取消全部固定',
        key: 'unpin-all',
        disabled: noTabsPinned.value,
        icon: () => h('i', { class: 'i-material-symbols:keep-off-rounded text-14' }),
      },
      {
        label: '刷新其他标签',
        key: 'refresh-other',
        disabled: tabStore.tabs.length <= 1,
        icon: () => h('i', { class: 'i-material-symbols:refresh-rounded text-14' }),
      },
    ],
  },
  { type: 'divider', key: 'divider-close' },
  {
    label: '关闭',
    key: 'close',
    disabled: !canCloseCurrentTab.value,
    icon: () => h('i', { class: 'ai-icon:x-circle text-14' }),
  },
  {
    label: '批量关闭',
    key: 'batch-close',
    icon: () => h('i', { class: 'i-material-symbols:tab-close-rounded text-14' }),
    children: [
      {
        label: '关闭其他',
        key: 'close-other',
        disabled: !hasClosableOtherTabs.value,
        icon: () => h('i', { class: 'ai-icon:x-circle text-14' }),
      },
      {
        label: '关闭左侧',
        key: 'close-left',
        disabled: !hasClosableLeftTabs.value,
        icon: () => h('i', { class: 'ai-icon:arrow-left text-14' }),
      },
      {
        label: '关闭右侧',
        key: 'close-right',
        disabled: !hasClosableRightTabs.value,
        icon: () => h('i', { class: 'ai-icon:arrow-right text-14' }),
      },
      {
        label: '关闭未固定标签',
        key: 'close-unpinned',
        disabled: !hasClosableTabs.value,
        icon: () => h('i', { class: 'i-material-symbols:tab-close-rounded text-14' }),
      },
      {
        label: '关闭全部',
        key: 'close-all',
        disabled: !hasClosableTabs.value,
        icon: () => h('i', { class: 'ai-icon:x text-14' }),
      },
    ],
  },
])

const route = useRoute()
function isSameTab(leftTab, rightTab) {
  if (!leftTab || !rightTab)
    return false
  return leftTab.key === rightTab.key || leftTab.path === rightTab.path
}

function getCloseTargets(type) {
  const index = currentTabIndex.value
  if (type === 'close')
    return currentTab.value ? [currentTab.value] : []
  if (type === 'close-other')
    return tabStore.tabs.filter(tab => !tab.pinned && !isSameTab(tab, currentTab.value))
  if (type === 'close-left')
    return tabStore.tabs.filter((tab, tabIndex) => tabIndex < index && !tab.pinned)
  if (type === 'close-right')
    return tabStore.tabs.filter((tab, tabIndex) => tabIndex > index && !tab.pinned)
  return tabStore.tabs.filter(tab => !tab.pinned)
}

async function runWithDirtyConfirmation(tabs, actionLabel, action) {
  if (!await confirmDirtyTabs(tabs, actionLabel))
    return
  if (activeTab.value && tabs.some(tab => isSameTab(tab, activeTab.value)))
    tabStore.authorizeDirtyNavigation(activeTab.value.key || activeTab.value.path)
  await action()
}

const actionMap = new Map([
  [
    'reload',
    async () => {
      if (!await confirmDirtyTabs(currentTab.value, '重新加载'))
        return
      tabStore.reloadTab(route.fullPath, route.meta?.keepAlive)
    },
  ],
  [
    'toggle-pin',
    () => {
      tabStore.toggleTabPinned(props.currentPath)
    },
  ],
  [
    'restore-closed',
    async () => {
      if (!await confirmDirtyTabs(activeTab.value, '切换页面'))
        return
      if (activeTab.value?.dirty)
        tabStore.authorizeDirtyNavigation(activeTab.value.key || activeTab.value.path)
      tabStore.restoreLastClosedTab()
    },
  ],
  [
    'copy-url',
    () => copyText(resolveTabUrl(router, currentTab.value), '页面地址已复制'),
  ],
  [
    'copy-title-url',
    () => copyText(`${currentTab.value?.title || ''}\n${resolveTabUrl(router, currentTab.value)}`, '页面名称和地址已复制'),
  ],
  [
    'open-new-window',
    () => window.open(resolveTabUrl(router, currentTab.value), '_blank', 'noopener,noreferrer'),
  ],
  ['move-start', () => tabStore.moveTabToStart(props.currentPath)],
  ['move-end', () => tabStore.moveTabToEnd(props.currentPath)],
  ['pin-all', () => tabStore.pinAllTabs()],
  ['unpin-all', () => tabStore.unpinAllTabs()],
  [
    'refresh-other',
    () => runWithDirtyConfirmation(
      tabStore.tabs.filter(tab => !isSameTab(tab, currentTab.value)),
      '刷新其他标签',
      () => tabStore.refreshOtherTabs(props.currentPath),
    ),
  ],
  [
    'close',
    () => runWithDirtyConfirmation(getCloseTargets('close'), '关闭页面', () => tabStore.removeTab(props.currentPath)),
  ],
  [
    'close-other',
    () => runWithDirtyConfirmation(getCloseTargets('close-other'), '关闭其他页面', () => tabStore.removeOther(props.currentPath)),
  ],
  [
    'close-left',
    () => runWithDirtyConfirmation(getCloseTargets('close-left'), '关闭左侧页面', () => tabStore.removeLeft(props.currentPath)),
  ],
  [
    'close-right',
    () => runWithDirtyConfirmation(getCloseTargets('close-right'), '关闭右侧页面', () => tabStore.removeRight(props.currentPath)),
  ],
  [
    'close-unpinned',
    () => runWithDirtyConfirmation(getCloseTargets('close-unpinned'), '关闭未固定页面', () => tabStore.closeUnpinnedTabs()),
  ],
  [
    'close-all',
    () => runWithDirtyConfirmation(getCloseTargets('close-all'), '关闭全部页面', () => tabStore.removeAll()),
  ],
])

function handleHideDropdown() {
  emit('update:show', false)
}

async function handleSelect(key) {
  const actionFn = actionMap.get(key)
  if (typeof actionFn === 'function')
    await actionFn()
  handleHideDropdown()
}
</script>
