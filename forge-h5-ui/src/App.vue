<script>
import { HOME_PAGE } from '@/utils/route'
import { setupDebugConsole } from '@/utils/debug-console'
import { isWeComBrowser, startWeComAutoLogin } from '@/utils/wecom'

function hideNativeTabBar() {
  if (typeof uni === 'undefined' || typeof uni.hideTabBar !== 'function') {
    return
  }
  uni.hideTabBar({
    animation: false,
    fail: () => {},
  })
}

// 企业微信客户端内自动免登：授权跳转或回调换票完成后进入首页
function bootstrapWeComAutoLogin() {
  if (!isWeComBrowser()) {
    return
  }
  startWeComAutoLogin().then((result) => {
    if (result?.status === 'logged-in') {
      uni.reLaunch({ url: HOME_PAGE, fail: () => {} })
    }
  })
}

export default {
  onLaunch: async function () {
    // 优先加载页内调试面板（?vdebug=1 开启），确保后续 console 可见
    await setupDebugConsole()
    hideNativeTabBar()
    bootstrapWeComAutoLogin()
    console.log('App Launch')
  },
  onShow: function () {
    hideNativeTabBar()
    console.log('App Show')
  },
  onHide: function () {
    console.log('App Hide')
  },
}
</script>

<style lang="scss">
@import "uview-plus/index.scss";
@import "@/styles/theme.css";
@import "@/styles/global.css";
@import "@/styles/dialog.css";
@import "@/styles/notify.css";

/*每个页面公共css */
</style>
