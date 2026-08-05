// 页内调试面板（vConsole）：企微/微信内置浏览器无法打开 DevTools 时，用于查看 console 与 network。
// 开启方式（任一即可）：
//   1) 地址后加 ?vdebug=1（写入 localStorage 持久化，企微 OAuth 回跳后仍生效）；关闭：?vdebug=0。
//   2) 构建期设 VITE_DEBUG_CONSOLE=1（移动端点固定「应用主页地址」无法追加 query 时用它，调试完记得移除）。
// 仅在显式开启时按需从 CDN 加载，不影响正常访问。

const STORAGE_KEY = '__forge_vdebug__'
const VCONSOLE_CDN = 'https://cdn.jsdelivr.net/npm/vconsole@latest/dist/vconsole.min.js'

export function setupDebugConsole() {
  return new Promise((resolve) => {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      resolve()
      return
    }
    // 构建期强制开启：移动端 WebView 无法在固定主页地址后追加 query 时使用
    const forced = import.meta.env.VITE_DEBUG_CONSOLE === '1'
    // 兼容 hash 路由：?vdebug 可能落在 # 之后，location.search 读不到，统一扫描完整 href
    const href = window.location.href || ''
    if (/[?&]vdebug=1/.test(href)) {
      localStorage.setItem(STORAGE_KEY, '1')
    }
    else if (/[?&]vdebug=0/.test(href)) {
      localStorage.removeItem(STORAGE_KEY)
    }
    if ((!forced && localStorage.getItem(STORAGE_KEY) !== '1') || window.VConsole) {
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
