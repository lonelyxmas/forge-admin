import { useAuthStore } from '@/store'
import { showRequestErrorDialog } from './error-dialog'

let isConfirming = false

export function isAuthErrorCode(code) {
  return code === '-8' || code === 401 || code === '401' || code === 11007 || code === 11008
}

export function shouldSilenceAuthError() {
  const authStore = useAuthStore()
  return authStore.isLoggingOut || window.location.pathname === '/login'
}

export function isSilentAuthError(error) {
  return Boolean(error?.silentAuthError || (isAuthErrorCode(error?.code) && shouldSilenceAuthError()))
}

function resolveErrorTitle(code) {
  switch (code) {
    case 400:
      return '请求参数错误'
    case 403:
      return '请求被拒绝'
    case 404:
      return '请求资源不存在'
    case 500:
      return '服务端异常'
    case 'NETWORK_ERROR':
      return '网络连接失败'
    default:
      return '请求失败'
  }
}

function showCommonErrorTip(code, message, detail) {
  if (window.$dialog?.error) {
    showRequestErrorDialog({
      title: resolveErrorTitle(code),
      code,
      message,
      method: detail?.method,
      url: detail?.url,
      traceId: detail?.traceId,
      detail,
    })
    return
  }
  window.$message?.error(message)
}

export function resolveResError(code, message, needTip = true, detail = null) {
  // 检查是否在登录页面
  const isLoginPage = window.location.pathname === '/login'
  const authStore = useAuthStore()

  switch (code) {
    case '-8': // 令牌无效
    case 401:
    case 11007:
    case 11008:
      // 退出流程或登录页上的鉴权失效属于预期状态，静默处理。
      if (authStore.isLoggingOut || isLoginPage) {
        authStore.resetToken()
        return false
      }
      if (isConfirming || !needTip) {
        return
      }
      isConfirming = true
      // 凭证已失效，无需用户确认，直接退出并跳转登录页
      window.$message?.error(message || '登录已过期，请重新登录')
      authStore.logout()
      window.setTimeout(() => {
        isConfirming = false
      }, 1200)
      return false
    case 403:
      message = message || '请求被拒绝'
      break
    case 404:
      message = message || '请求资源或接口不存在'
      break
    case 500:
      message = message || '服务器发生异常'
      break
    default:
      message = message ?? `【${code}】: 未知异常!`
      break
  }
  if (needTip) {
    showCommonErrorTip(code, message, detail)
  }
  return message
}
