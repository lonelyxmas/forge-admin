export function confirmDirtyTabs(tabs, actionLabel = '继续操作') {
  const dirtyTabs = (Array.isArray(tabs) ? tabs : [tabs]).filter(tab => tab?.dirty)
  if (!dirtyTabs.length)
    return Promise.resolve(true)

  const tabNames = dirtyTabs.slice(0, 3).map(tab => `“${tab.title || tab.path}”`).join('、')
  const moreText = dirtyTabs.length > 3 ? `等 ${dirtyTabs.length} 个页面` : ''
  const content = `${tabNames}${moreText}存在未保存的更改，${actionLabel}可能会丢失这些更改。`

  if (!window.$dialog) {
    const nativeConfirm = Reflect.get(window, 'confirm')
    return Promise.resolve(typeof nativeConfirm === 'function' && nativeConfirm(`${content}\n是否继续？`))
  }

  return new Promise((resolve) => {
    window.$dialog.warning({
      title: '未保存变更',
      content,
      positiveText: actionLabel,
      negativeText: '取消',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
    })
  })
}

export function resolveTabUrl(router, tab) {
  const href = router.resolve(tab?.path || '/').href
  return new URL(href, window.location.origin).href
}

export async function copyText(text, successMessage = '复制成功') {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    }
    else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      textarea.remove()
    }
    window.$message?.success(successMessage)
  }
  catch {
    window.$message?.error('复制失败，请手动复制')
  }
}
