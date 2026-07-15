import { defineStore } from 'pinia'
import { nextTick } from 'vue'
import { useRouterStore } from '@/store'
import { getSessionStorage, removeSessionStorage, setSessionStorage } from '@/utils/storage'

const TABS_KEY = `${import.meta.env.VITE_TENANT || 'default'}_tabs`
const CLOSED_TABS_KEY = `${import.meta.env.VITE_TENANT || 'default'}_closed_tabs`
const MAX_CLOSED_TABS = 20

function isSameTab(tab, keyOrPath) {
  return tab?.key === keyOrPath || tab?.path === keyOrPath
}

function sortPinnedTabs(tabs = []) {
  return [...tabs].sort((leftTab, rightTab) => Number(Boolean(rightTab.pinned)) - Number(Boolean(leftTab.pinned)))
}

function getTabCacheName(tab) {
  return tab?.path?.substring(1).replace(/\//g, '-').replace(/\?.*/, '') || ''
}

function waitFrame() {
  return new Promise(resolve => requestAnimationFrame(() => resolve()))
}

export const useTabStore = defineStore('tab', {
  state: () => ({
    tabs: sortPinnedTabs((getSessionStorage(TABS_KEY) || []).map(tab => ({ ...tab, dirty: false, dirtyMessage: '' }))),
    closedTabs: getSessionStorage(CLOSED_TABS_KEY) || [],
    activeTab: '',
    dirtyNavigationBypassPath: '',
    reloading: false, // 添加reloading状态用于页面刷新
    // 添加缓存视图列表
    cacheViews: [],
  }),
  getters: {
    activeTabPath() {
      return this.tabs.find(item => item.key === this.activeTab)?.path || '/'
    },
  },
  actions: {
    setTabs(tabs) {
      this.tabs = sortPinnedTabs(tabs)
      setSessionStorage(TABS_KEY, this.tabs)
    },
    removeTabCache(tab) {
      const cacheName = getTabCacheName(tab)
      if (!cacheName)
        return
      const cacheIndex = this.cacheViews.indexOf(cacheName)
      if (cacheIndex > -1) {
        this.cacheViews.splice(cacheIndex, 1)
      }
    },
    // 修改 addTab 方法，添加缓存视图处理
    addTab(tab) {
      if (this.tabs.some(item => item.key === tab.key))
        return
      this.setTabs([...this.tabs, tab])

      // 添加缓存视图
      if (tab.path) {
        // 转换路径为缓存名称格式，例如 /system/user -> system-user
        const cacheName = getTabCacheName(tab)
        if (!this.cacheViews.includes(cacheName)) {
          this.cacheViews.push(cacheName)
        }
      }
    },
    updateTabTitle(path, title) {
      if (!path || !title)
        return
      const tab = this.tabs.find(item => item.path === path || item.key === path)
      if (!tab || tab.title === title)
        return
      tab.title = title
      setSessionStorage(TABS_KEY, this.tabs)
    },
    updateTabMeta(path, patch = {}) {
      if (!path || !patch || !Object.keys(patch).length)
        return
      const tab = this.tabs.find(item => item.path === path || item.key === path)
      if (!tab)
        return
      Object.assign(tab, patch)
      setSessionStorage(TABS_KEY, this.tabs)
    },
    pinTab(keyOrPath) {
      const tab = this.tabs.find(item => isSameTab(item, keyOrPath))
      if (!tab || tab.pinned)
        return
      tab.pinned = true
      this.setTabs(this.tabs)
    },
    unpinTab(keyOrPath) {
      const tab = this.tabs.find(item => isSameTab(item, keyOrPath))
      if (!tab || !tab.pinned)
        return
      tab.pinned = false
      this.setTabs(this.tabs)
    },
    toggleTabPinned(keyOrPath) {
      const tab = this.tabs.find(item => isSameTab(item, keyOrPath))
      if (!tab)
        return
      if (tab.pinned)
        this.unpinTab(keyOrPath)
      else
        this.pinTab(keyOrPath)
    },
    pinAllTabs() {
      this.tabs.forEach((tab) => {
        tab.pinned = true
      })
      this.setTabs(this.tabs)
    },
    unpinAllTabs() {
      this.tabs.forEach((tab) => {
        tab.pinned = false
      })
      this.setTabs(this.tabs)
    },
    reorderTabs(tabs) {
      if (!Array.isArray(tabs) || tabs.length !== this.tabs.length)
        return
      const currentKeys = new Set(this.tabs.map(tab => tab.key || tab.path))
      if (tabs.some(tab => !currentKeys.has(tab.key || tab.path)))
        return
      this.setTabs(tabs)
    },
    moveTabToStart(keyOrPath) {
      const tabIndex = this.tabs.findIndex(item => isSameTab(item, keyOrPath))
      if (tabIndex < 0)
        return
      const tab = this.tabs[tabIndex]
      const nextTabs = this.tabs.filter((_, index) => index !== tabIndex)
      const targetIndex = tab.pinned ? 0 : nextTabs.findIndex(item => !item.pinned)
      nextTabs.splice(targetIndex < 0 ? nextTabs.length : targetIndex, 0, tab)
      this.setTabs(nextTabs)
    },
    moveTabToEnd(keyOrPath) {
      const tabIndex = this.tabs.findIndex(item => isSameTab(item, keyOrPath))
      if (tabIndex < 0)
        return
      const tab = this.tabs[tabIndex]
      const nextTabs = this.tabs.filter((_, index) => index !== tabIndex)
      if (tab.pinned) {
        const firstUnpinnedIndex = nextTabs.findIndex(item => !item.pinned)
        nextTabs.splice(firstUnpinnedIndex < 0 ? nextTabs.length : firstUnpinnedIndex, 0, tab)
      }
      else {
        nextTabs.push(tab)
      }
      this.setTabs(nextTabs)
    },
    setTabDirty(keyOrPath, dirty = true, message = '') {
      const tab = this.tabs.find(item => isSameTab(item, keyOrPath))
      if (!tab)
        return
      tab.dirty = Boolean(dirty)
      tab.dirtyMessage = dirty ? message || tab.dirtyMessage || '当前页面有未保存的更改' : ''
      setSessionStorage(TABS_KEY, this.tabs)
    },
    authorizeDirtyNavigation(keyOrPath) {
      this.dirtyNavigationBypassPath = keyOrPath || ''
      const authorizedPath = this.dirtyNavigationBypassPath
      window.setTimeout(() => {
        if (this.dirtyNavigationBypassPath === authorizedPath)
          this.dirtyNavigationBypassPath = ''
      }, 2000)
    },
    consumeDirtyNavigation(keyOrPath) {
      if (!keyOrPath || this.dirtyNavigationBypassPath !== keyOrPath)
        return false
      this.dirtyNavigationBypassPath = ''
      return true
    },
    recordClosedTabs(tabs) {
      const closedTabs = (Array.isArray(tabs) ? tabs : [tabs])
        .filter(Boolean)
        .map(tab => ({ ...tab, pinned: false, dirty: false, dirtyMessage: '' }))
      if (!closedTabs.length)
        return
      const closedKeys = new Set(closedTabs.map(tab => tab.key || tab.path))
      this.closedTabs = [
        ...this.closedTabs.filter(tab => !closedKeys.has(tab.key || tab.path)),
        ...closedTabs,
      ].slice(-MAX_CLOSED_TABS)
      setSessionStorage(CLOSED_TABS_KEY, this.closedTabs)
    },
    restoreLastClosedTab() {
      const tab = this.closedTabs.pop()
      if (!tab)
        return
      setSessionStorage(CLOSED_TABS_KEY, this.closedTabs)
      this.addTab(tab)
      this.setActiveTab(tab.key || tab.path)
      useRouterStore().router?.push(tab.path)
    },
    // 修改 removeTab 方法，删除对应的缓存视图
    removeTab(keyOrPath) {
      const index = this.tabs.findIndex(item => isSameTab(item, keyOrPath))
      if (index === -1)
        return
      const tab = this.tabs[index]
      if (tab.pinned)
        return
      const isLast = index === this.tabs.length - 1

      // 删除对应的缓存视图
      this.removeTabCache(tab)
      this.recordClosedTabs(tab)

      this.tabs.splice(index, 1)
      setSessionStorage(TABS_KEY, this.tabs)
      if (!isSameTab(tab, this.activeTab))
        return
      const newTab = this.tabs[index] || this.tabs[index - 1] || { path: '/' }
      useRouterStore().router?.push(newTab.path)
      this.setActiveTab(isLast ? (newTab.key || newTab.path) : this.tabs[index]?.key || newTab.key || newTab.path)
    },
    removeTabSilently(keyOrPath) {
      if (!keyOrPath)
        return
      const removedActive = this.tabs.some(item => (item.key === keyOrPath || item.path === keyOrPath) && item.key === this.activeTab)
      const nextTabs = []
      this.tabs.forEach((tab) => {
        if (tab.key === keyOrPath || tab.path === keyOrPath) {
          this.removeTabCache(tab)
          return
        }
        nextTabs.push(tab)
      })
      if (nextTabs.length === this.tabs.length)
        return
      this.setTabs(nextTabs)
      if (removedActive)
        this.activeTab = nextTabs[nextTabs.length - 1]?.key || ''
    },
    setActiveTab(key) {
      this.activeTab = key
    },
    removeOther(curPath) {
      // 删除其他标签时，也需要更新缓存视图列表
      const filterTabs = this.tabs.filter(item => item.pinned || isSameTab(item, curPath))
      this.recordClosedTabs(this.tabs.filter(item => !filterTabs.includes(item)))

      // 更新缓存视图列表
      const newCacheViews = []
      filterTabs.forEach((tab) => {
        if (tab.path) {
          const cacheName = tab.path.substring(1).replace(/\//g, '-').replace(/\?.*/, '')
          if (!newCacheViews.includes(cacheName)) {
            newCacheViews.push(cacheName)
          }
        }
      })
      this.cacheViews = newCacheViews

      this.setTabs(filterTabs)
      if (!filterTabs.find(item => item.path === this.activeTab)) {
        useRouterStore().router?.push(filterTabs[filterTabs.length - 1].path)
      }
    },
    removeLeft(curPath) {
      const curIndex = this.tabs.findIndex(item => isSameTab(item, curPath))
      if (curIndex === -1)
        return
      const filterTabs = this.tabs.filter((item, index) => item.pinned || index >= curIndex)
      this.recordClosedTabs(this.tabs.filter(item => !filterTabs.includes(item)))

      // 更新缓存视图列表
      const newCacheViews = []
      filterTabs.forEach((tab) => {
        if (tab.path) {
          const cacheName = tab.path.substring(1).replace(/\//g, '-').replace(/\?.*/, '')
          if (!newCacheViews.includes(cacheName)) {
            newCacheViews.push(cacheName)
          }
        }
      })
      this.cacheViews = newCacheViews

      this.setTabs(filterTabs)
      if (!filterTabs.find(item => item.path === this.activeTab)) {
        useRouterStore().router?.push(filterTabs[filterTabs.length - 1].path)
      }
    },
    removeRight(curPath) {
      const curIndex = this.tabs.findIndex(item => isSameTab(item, curPath))
      if (curIndex === -1)
        return
      const filterTabs = this.tabs.filter((item, index) => item.pinned || index <= curIndex)
      this.recordClosedTabs(this.tabs.filter(item => !filterTabs.includes(item)))

      // 更新缓存视图列表
      const newCacheViews = []
      filterTabs.forEach((tab) => {
        if (tab.path) {
          const cacheName = tab.path.substring(1).replace(/\//g, '-').replace(/\?.*/, '')
          if (!newCacheViews.includes(cacheName)) {
            newCacheViews.push(cacheName)
          }
        }
      })
      this.cacheViews = newCacheViews

      this.setTabs(filterTabs)
      if (!filterTabs.find(item => isSameTab(item, this.activeTab))) {
        useRouterStore().router?.push(filterTabs[filterTabs.length - 1].path)
      }
    },
    removeAll() {
      const pinnedTabs = this.tabs.filter(item => item.pinned)
      this.recordClosedTabs(this.tabs.filter(item => !item.pinned))
      this.setTabs(pinnedTabs)
      this.cacheViews = pinnedTabs
        .map(getTabCacheName)
        .filter(Boolean)
      if (!pinnedTabs.some(item => isSameTab(item, this.activeTab))) {
        const nextTab = pinnedTabs[pinnedTabs.length - 1]
        useRouterStore().router?.push(nextTab?.path || '/')
        this.setActiveTab(nextTab?.key || nextTab?.path || '')
      }
    },
    closeUnpinnedTabs() {
      const pinnedTabs = this.tabs.filter(item => item.pinned)
      const removedTabs = this.tabs.filter(item => !item.pinned)
      if (!removedTabs.length)
        return
      this.recordClosedTabs(removedTabs)
      removedTabs.forEach(tab => this.removeTabCache(tab))
      this.setTabs(pinnedTabs)
      if (!pinnedTabs.some(item => isSameTab(item, this.activeTab))) {
        const nextTab = pinnedTabs[pinnedTabs.length - 1]
        useRouterStore().router?.push(nextTab?.path || '/')
        this.setActiveTab(nextTab?.key || nextTab?.path || '')
      }
    },
    async refreshOtherTabs(curPath) {
      const shouldReloadActiveTab = !isSameTab(
        this.tabs.find(tab => isSameTab(tab, this.activeTab)),
        this.tabs.find(tab => isSameTab(tab, curPath)),
      )
      const otherCacheNames = new Set(
        this.tabs
          .filter(tab => !isSameTab(tab, curPath))
          .map(getTabCacheName)
          .filter(Boolean),
      )
      this.cacheViews = this.cacheViews.filter(name => !otherCacheNames.has(name))
      this.tabs.forEach((tab) => {
        if (!isSameTab(tab, curPath)) {
          tab.dirty = false
          tab.dirtyMessage = ''
        }
      })
      setSessionStorage(TABS_KEY, this.tabs)
      if (shouldReloadActiveTab) {
        this.reloading = true
        await nextTick()
        await waitFrame()
        this.reloading = false
      }
    },
    // 添加reloadTab方法
    async reloadTab(path, keepAlive) {
      // 设置reloading状态为true
      this.reloading = true

      // 如果是keepAlive页面，先移除再添加
      if (keepAlive) {
        const tab = this.tabs.find(item => item.path === path)
        if (tab) {
          // 临时移除keepAlive属性
          tab.keepAlive = false
          // 触发重新渲染
          await nextTick()
          // 恢复keepAlive属性
          tab.keepAlive = true
        }
      }

      // 触发重新渲染，至少等待一帧，保证 router-view 先卸载再重新挂载。
      await nextTick()
      await waitFrame()

      // 重置reloading状态
      this.reloading = false
      this.setTabDirty(path, false)
    },
    resetTabs() {
      removeSessionStorage(TABS_KEY)
      removeSessionStorage(CLOSED_TABS_KEY)
      this.tabs = []
      this.closedTabs = []
      this.activeTab = ''
      this.dirtyNavigationBypassPath = ''
      this.reloading = false
      this.cacheViews = []
    },
  },
  persist: {
    key: `${import.meta.env.VITE_TENANT || 'default'}_tab`,
    pick: ['tabs'],
    storage: sessionStorage,
  },
})
