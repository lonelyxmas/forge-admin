/**
 * 加密工具
 */

/**
 * 简易 MD5 实现（浏览器环境）
 * 如需完整功能可引入 md5 库
 */
function md5(string) {
  // 简易实现，生产环境建议引入 md5 库
  let hash = 0
  for (let i = 0; i < string.length; i++) {
    const char = string.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return Math.abs(hash).toString(16)
}

export function md5Encrypt(data) {
  if (!data) {
    console.warn('md5Encrypt: 加密数据为空')
    return ''
  }
  return md5(String(data))
}

export function base64Encode(data) {
  if (!data) return ''
  try {
    // #ifdef H5
    return btoa(unescape(encodeURIComponent(String(data))))
    // #endif
    // #ifndef H5
    return String(data)
    // #endif
  } catch (error) {
    console.error('base64Encode error:', error)
    return ''
  }
}

export function base64Decode(data) {
  if (!data) return ''
  try {
    // #ifdef H5
    return decodeURIComponent(escape(atob(String(data))))
    // #endif
    // #ifndef H5
    return String(data)
    // #endif
  } catch (error) {
    console.error('base64Decode error:', error)
    return ''
  }
}

export function randomString(length = 16) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

export const crypto = {
  md5: md5Encrypt,
  base64Encode,
  base64Decode,
  randomString
}

export {
  applyRuntimeCryptoConfig,
  cryptoConfig,
  loadRuntimeCryptoConfig,
  matchPath,
  normalizeRuntimeCryptoConfig,
  shouldEncrypt,
  updateCryptoConfig,
} from './crypto/crypto-config'
export { decrypt, decryptResponse, encrypt, encryptRequest } from './crypto/crypto-interceptor'
export {
  exchangeKey,
  fetchPublicKey,
  getSessionKey,
  initKeyExchange,
  isKeyExchanged,
  resetKeyExchange,
} from './crypto/key-exchange'

export default crypto
