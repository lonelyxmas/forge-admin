import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { usePermissionStore } from '@/store'
import { resolveSidebarMenuContext } from '../sidebar-menu-context'

export function useSidebarVisibility() {
  const route = useRoute()
  const permissionStore = usePermissionStore()

  const sidebarMenuContext = computed(() => {
    if (!permissionStore.menuDataLoaded)
      return null
    const menus = permissionStore.allMenus?.length ? permissionStore.allMenus : permissionStore.menus
    return resolveSidebarMenuContext(menus, route)
  })

  const showSidebar = computed(() => Boolean(sidebarMenuContext.value?.hasParent))

  return {
    showSidebar,
    sidebarMenuContext,
  }
}
