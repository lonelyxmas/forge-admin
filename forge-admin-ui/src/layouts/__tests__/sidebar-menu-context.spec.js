import { describe, expect, it } from 'vitest'
import { resolveSidebarMenuContext } from '../sidebar-menu-context'

const menus = [
  { key: 'home', path: '/home', type: 'menu' },
  {
    key: 'system',
    path: '/system',
    type: 'module',
    children: [
      { key: 'user', path: '/system/user', type: 'menu' },
      { key: 'client', path: '/system/client', type: 'menu' },
    ],
  },
  { key: 'profile', path: '/profile', type: 'menu' },
]

describe('resolveSidebarMenuContext', () => {
  it('hides the sidebar for home and top-level direct menus', () => {
    expect(resolveSidebarMenuContext(menus, { path: '/' })).toBeNull()
    expect(resolveSidebarMenuContext(menus, { path: '/home' })).toBeNull()
    expect(resolveSidebarMenuContext(menus, { path: '/profile' })).toMatchObject({
      menu: menus[2],
      parent: null,
      hasParent: false,
    })
  })

  it('shows the sidebar for a nested menu', () => {
    expect(resolveSidebarMenuContext(menus, { path: '/system/user' })).toMatchObject({
      menu: menus[1].children[0],
      parent: menus[1],
      hasParent: true,
    })
  })

  it('resolves a parent from a flattened resource index', () => {
    const flattenedMenus = [
      { key: 'app-center', path: '', parentId: 0 },
      { key: 'app-overview', path: '/app-center', parentId: 'app-center' },
    ]

    expect(resolveSidebarMenuContext(flattenedMenus, { path: '/app-center' })).toMatchObject({
      menu: flattenedMenus[1],
      parent: flattenedMenus[0],
      hasParent: true,
    })
  })

  it('prefers the nested menu when a directory and child share a path', () => {
    const duplicatePathMenus = [
      { key: 'app-center-module', path: '/app-center', parentId: 0 },
      { key: 'app-center-overview', path: '/app-center', parentId: 'app-center-module' },
    ]

    expect(resolveSidebarMenuContext(duplicatePathMenus, { path: '/app-center' })).toMatchObject({
      menu: duplicatePathMenus[1],
      parent: duplicatePathMenus[0],
      hasParent: true,
    })
  })

  it('keeps the sidebar for a hidden child page with a logical parent', () => {
    expect(resolveSidebarMenuContext(menus, {
      path: '/system/user/42',
      meta: { parentKey: 'user' },
    })).toMatchObject({
      menu: menus[1].children[0],
      parent: menus[1],
      hasParent: true,
    })
  })

  it('accepts a numeric logical parent key', () => {
    const numericMenus = [{
      key: 0,
      children: [{ key: 1, path: '/system/user' }],
    }]

    expect(resolveSidebarMenuContext(numericMenus, {
      path: '/system/user/create',
      meta: { parentKey: 0 },
    })).toMatchObject({
      menu: numericMenus[0],
      hasParent: true,
    })
  })

  it('does not retain a previous menu when the current route is unknown', () => {
    expect(resolveSidebarMenuContext(menus, { path: '/unknown' })).toBeNull()
  })
})
