// 页内调试面板（vConsole）：企微/微信内置浏览器无法打开 DevTools 时，用于查看 console 与 network。
// 开启：地址后加 ?vdebug=1（写入 localStorage 持久化，企微 OAuth 回跳后仍生效）；关闭：?vdebug=0。
// 仅在显式开启时按需从 CDN 加载，不影响正常访问。

const STORAGE_KEY = '__forge_vdebug__'
const VCONSOLE_CDN = 'https://cdn.jsdelivr.net/npm/vconsole@latest/dist/vconsole.min.js'

export function setupDebugConsole() {
  return new Promise((resolve) => {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      resolve()
      return
    }
    const search = window.location.search || ''
    if (/[?&]vdebug=1/.test(search)) {
      localStorage.setItem(STORAGE_KEY, '1')
    }
    else if (/[?&]vdebug=0/.test(search)) {
      localStorage.removeItem(STORAGE_KEY)
    }
    if (localStorage.getItem(STORAGE_KEY) !== '1' || window.VConsole) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = VCONSOLE_CDN
    script.onload = () => {
      try {
        if (window.VConsole) {
          // eslint-disable-next-line no-new
          new window.VConsole()
        }
      }
      catch (error) {
        console.warn('[debug] vConsole 初始化失败:', error)
      }
      resolve()
    }
    script.onerror = () => {
      console.warn('[debug] vConsole 加载失败（企微可能拦截 CDN），请改用远程调试')
      resolve()
    }
    document.head.appendChild(script)
  })
}
