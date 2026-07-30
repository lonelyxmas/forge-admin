import api from '@/api'
import { useAuthStore } from '@/store'

// 企业微信工作台免登：仅在 H5 且运行于企业微信客户端内生效。
// 流程：无 code → 取授权地址跳转企微 OAuth2；带 code&state 回跳 → 换票据静默登录。

let autoLoginPromise = null

function getConnectionCode() {
  return import.meta.env.VITE_WECOM_CONNECTION_CODE || ''
}

function getUserClient() {
  return import.meta.env.VITE_USER_CLIENT || 'app'
}

/**
 * 是否运行在企业微信客户端内（UA 含 wxwork）
 */
export function isWeComBrowser() {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false
  }
  return /wxwork/i.test(navigator.userAgent || '')
}

function readSearchParam(name) {
  if (typeof window === 'undefined') {
    return ''
  }
  const search = window.location.search || ''
  const matched = search.replace(/^\?/, '').split('&').find(item => item.startsWith(`${name}=`))
  return matched ? decodeURIComponent(matched.slice(name.length + 1)) : ''
}

/**
 * 回跳 URL 是否携带企微免登回调参数（code + state）
 */
export function hasWeComCallbackParams() {
  return !!(readSearchParam('code') && readSearchParam('state'))
}

// 回跳目标域名需在企微应用可信域名内；去掉 query/hash，保留 origin+pathname 作为授权回跳地址
function buildRedirectUri() {
  const { origin, pathname } = window.location
  return `${origin}${pathname}`
}

// 换票登录成功后清理 URL 上的 code&state，避免刷新时用已失效 code 重复回调
function clearCallbackParams() {
  if (typeof window === 'undefined' || typeof window.history?.replaceState !== 'function') {
    return
  }
  const { origin, pathname, hash } = window.location
  window.history.replaceState(null, '', `${origin}${pathname}${hash || ''}`)
}

async function runAutoLogin() {
  const inWeCom = isWeComBrowser()
  const connectionCode = getConnectionCode()
  // 联调诊断：打开控制台即可确认免登是否触发及跳过原因
  console.warn(`[wecom] 免登检测: inWeCom=${inWeCom}, hasConnectionCode=${!!connectionCode}, ua=${typeof navigator !== 'undefined' ? navigator.userAgent : ''}`)
  if (!inWeCom) {
    return { status: 'skip', reason: 'not-wecom' }
  }
  if (!connectionCode) {
    console.warn('[wecom] 未配置 VITE_WECOM_CONNECTION_CODE，跳过企微免登')
    return { status: 'skip', reason: 'no-connection-code' }
  }

  const authStore = useAuthStore()
  const code = readSearchParam('code')
  const state = readSearchParam('state')

  // 回调阶段：企微携带 code&state 回跳，换票据完成静默登录
  if (code && state) {
    clearCallbackParams()
    if (authStore.isLogin) {
      return { status: 'logged-in', reason: 'already' }
    }
    const res = await api.wecomLoginCallback({ code, state, userClient: getUserClient() })
    const ticket = res?.data || {}
    if (!ticket.socialTicket) {
      throw new Error('企微免登换票失败')
    }
    await authStore.oauthLogin({
      socialTicket: ticket.socialTicket,
      connectionCode: ticket.connectionCode || connectionCode,
      tenantId: ticket.tenantId,
    })
    return { status: 'logged-in' }
  }

  // 已登录无需再次授权
  if (authStore.isLogin) {
    return { status: 'skip', reason: 'already-login' }
  }

  // 授权阶段：取授权地址并跳转企微 OAuth2
  const res = await api.getWecomAuthorize({
    connectionCode,
    redirectUri: buildRedirectUri(),
    userClient: getUserClient(),
  })
  const authUrl = res?.data?.authUrl
  if (!authUrl) {
    throw new Error('获取企微授权地址失败')
  }
  window.location.href = authUrl
  return { status: 'redirecting' }
}

/**
 * 触发企微免登（幂等）：同一次会话内只执行一次，返回可复用的 Promise
 */
export function startWeComAutoLogin() {
  if (!autoLoginPromise) {
    autoLoginPromise = runAutoLogin().catch((error) => {
      console.error('[wecom] 企微免登失败:', error)
      return { status: 'error', error }
    })
  }
  return autoLoginPromise
}

/**
 * 当前是否处于企微免登流程中（授权跳转或回调换票期间）
 */
export function isWeComAutoLoginPending() {
  return isWeComBrowser() && !!getConnectionCode() && (hasWeComCallbackParams() || !!autoLoginPromise)
}

/**
 * 获取正在进行的免登 Promise（无则返回 null）
 */
export function getWeComAutoLoginPromise() {
  return autoLoginPromise
}
