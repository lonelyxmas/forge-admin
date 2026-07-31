import api from '@/api'
import { useAuthStore } from '@/store'

// 企业微信工作台免登：仅在 H5 且运行于企业微信客户端内生效。
// 流程：无 code → 取授权地址跳转企微 OAuth2；带 code&state 回跳 → 换票据静默登录。
// 免登开关与 connectionCode 由后端连接配置下发（sys_social_config.sso_workbench_enabled），不再前端写死。

let autoLoginPromise = null

// 企微 OAuth 回跳地址不带 hash，授权前暂存深链目标页，登录后恢复（卡片消息跳待办详情等场景）
const LOGIN_REDIRECT_KEY = 'wecom_login_redirect'

function saveLoginRedirect() {
  if (typeof window === 'undefined' || typeof sessionStorage === 'undefined') {
    return
  }
  // hash 形如 #/pages/todo-detail?taskId=xxx，去掉 # 后作为 uni 路由地址
  const target = (window.location.hash || '').replace(/^#/, '')
  if (target && target.startsWith('/pages/')) {
    try {
      sessionStorage.setItem(LOGIN_REDIRECT_KEY, target)
    } catch (e) { /* 存储不可用时降级回首页 */ }
  }
}

/**
 * 取出并清除免登前暂存的深链目标页（仅限 /pages/ 开头的站内路由）
 */
export function consumeWeComLoginRedirect() {
  if (typeof window === 'undefined' || typeof sessionStorage === 'undefined') {
    return ''
  }
  try {
    const target = sessionStorage.getItem(LOGIN_REDIRECT_KEY) || ''
    sessionStorage.removeItem(LOGIN_REDIRECT_KEY)
    return target.startsWith('/pages/') ? target : ''
  } catch (e) {
    return ''
  }
}

async function getConnectionCode() {
  try {
    const res = await api.getWecomSsoConnection({ platform: 'WECHAT_ENTERPRISE' })
    if (res?.data?.enabled) {
      return res.data.connectionCode || ''
    }
  }
  catch (error) {
    console.warn('[wecom] 获取免登连接配置失败:', error)
  }
  return ''
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
  if (!inWeCom) {
    return { status: 'skip', reason: 'not-wecom' }
  }
  const connectionCode = await getConnectionCode()
  // 联调诊断：打开控制台即可确认免登是否触发及跳过原因
  console.warn(`[wecom] 免登检测: inWeCom=${inWeCom}, hasConnectionCode=${!!connectionCode}, ua=${typeof navigator !== 'undefined' ? navigator.userAgent : ''}`)
  if (!connectionCode) {
    console.warn('[wecom] 未在连接配置中开启工作台免登，跳过企微免登')
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

  // 授权阶段：暂存深链目标后取授权地址并跳转企微 OAuth2（回跳不带 hash，需登录后恢复）
  saveLoginRedirect()
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
  return isWeComBrowser() && (hasWeComCallbackParams() || !!autoLoginPromise)
}

/**
 * 获取正在进行的免登 Promise（无则返回 null）
 */
export function getWeComAutoLoginPromise() {
  return autoLoginPromise
}
