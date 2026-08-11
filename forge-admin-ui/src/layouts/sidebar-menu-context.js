import { isSameMenuPath } from '@/utils/menu-utils'

function isSameMenuKey(left, right) {
  if (left === undefined || left === null || right === undefined || right === null)
    return false
  return String(left) === String(right)
}

function findMenuByKey(menus, key, parent = null) {
  for (const menu of menus || []) {
    if (isSameMenuKey(menu.key ?? menu.id, key)) {
      return { menu, parent }
    }
    const found = findMenuByKey(menu.children, key, menu)
    if (found)
      return found
  }
  return null
}

function collectPathMatches(menus, path, result = []) {
  for (const menu of menus || []) {
    if (isSameMenuPath(menu.path, path))
      result.push({ menu, parent: null })
    collectPathMatches(menu.children, path, result)
  }
  return result
}

function isRootParentKey(key) {
  return key === undefined || key === null || key === '' || String(key) === '0'
}

function isHomeRoute(path) {
  const normalizedPath = String(path || '').split('?')[0].split('#')[0].replace(/\/+$/, '') || '/'
  return normalizedPath === '/' || normalizedPath === '/home'
}

function resolveParentMenu(menus, match) {
  if (match.parent)
    return match.parent

  if (isRootParentKey(match.menu?.parentId))
    return null

  return findMenuByKey(menus, match.menu.parentId)?.menu || null
}

/**
 * Resolve whether the active route belongs to a nested menu. Hidden child pages
 * use meta.parentKey, which represents their logical menu parent.
 */
export function resolveSidebarMenuContext(menus, route = {}) {
  if (isHomeRoute(route.path))
    return null

  if (!Array.isArray(menus) || menus.length === 0)
    return null

  const routeMatches = collectPathMatches(menus, route.path)
  if (routeMatches.length > 0) {
    const rankedMatches = routeMatches
      .map((match) => {
        const indexedMatch = findMenuByKey(menus, match.menu.key ?? match.menu.id)
        const parent = resolveParentMenu(menus, indexedMatch || match)
        const exact = String(match.menu.path || '') === String(route.path || '')
        return { ...match, parent, hasParent: Boolean(parent), exact }
      })
      .sort((left, right) => {
        if (left.exact !== right.exact)
          return left.exact ? -1 : 1
        if (left.hasParent !== right.hasParent)
          return left.hasParent ? -1 : 1
        return 0
      })

    const match = rankedMatches[0]
    if (match) {
      const { exact: _exact, ...context } = match
      return context
    }
  }

  const logicalParentKey = route.meta?.parentKey
  if (logicalParentKey === undefined || logicalParentKey === null || logicalParentKey === '')
    return null

  const logicalParent = findMenuByKey(menus, logicalParentKey)
  if (!logicalParent)
    return null

  return {
    ...logicalParent,
    hasParent: true,
  }
}
