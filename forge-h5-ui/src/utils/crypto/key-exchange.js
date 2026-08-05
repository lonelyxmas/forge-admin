import { cryptoConfig, updateCryptoConfig } from './crypto-config'
import { rsaEncrypt } from './rsa'

const STORAGE_KEYS = {
  SESSION_KEY: 'crypto_session_key',
  PUBLIC_KEY: 'crypto_public_key',
  EXCHANGED: 'crypto_exchanged',
  SESSION_ID: 'crypto_session_id',
  VERSION: 'crypto_key_version',
}

const CURRENT_KEY_VERSION = '2'

const keyExchangeState = {
  publicKey: null,
  sessionKey: null,
  sessionId: '',
  exchanged: false,
  exchanging: false,
}

function getStorageItem(key) {
  try {
    return uni.getStorageSync(key)
  }
  catch {
    return ''
  }
}

function setStorageItem(key, value) {
  try {
    uni.setStorageSync(key, value)
  }
  catch (error) {
    console.error('[Crypto] 保存密钥状态失败:', error)
  }
}

function removeStorageItem(key) {
  try {
    uni.removeStorageSync(key)
  }
  catch (error) {
    console.error('[Crypto] 清除密钥状态失败:', error)
  }
}

function restoreKeyState() {
  const version = getStorageItem(STORAGE_KEYS.VERSION)
  if (version !== CURRENT_KEY_VERSION) {
    clearStoredKeyState()
    setStorageItem(STORAGE_KEYS.VERSION, CURRENT_KEY_VERSION)
    return
  }

  const sessionKey = getStorageItem(STORAGE_KEYS.SESSION_KEY)
  const publicKey = getStorageItem(STORAGE_KEYS.PUBLIC_KEY)
  const sessionId = getStorageItem(STORAGE_KEYS.SESSION_ID)
  const exchanged = getStorageItem(STORAGE_KEYS.EXCHANGED) === 'true'

  if (sessionKey) {
    keyExchangeState.sessionKey = sessionKey
    keyExchangeState.sessionId = sessionId || ''
    keyExchangeState.exchanged = exchanged
    updateCryptoConfig({ secretKey: sessionKey })
  }

  if (publicKey) {
    keyExchangeState.publicKey = publicKey
  }
}

function saveKeyState() {
  if (keyExchangeState.sessionKey) {
    setStorageItem(STORAGE_KEYS.SESSION_KEY, keyExchangeState.sessionKey)
    setStorageItem(STORAGE_KEYS.EXCHANGED, String(keyExchangeState.exchanged))
  }
  setStorageItem(STORAGE_KEYS.SESSION_ID, keyExchangeState.sessionId || '')
  if (keyExchangeState.publicKey) {
    setStorageItem(STORAGE_KEYS.PUBLIC_KEY, keyExchangeState.publicKey)
  }
  setStorageItem(STORAGE_KEYS.VERSION, CURRENT_KEY_VERSION)
}

function clearStoredKeyState() {
  removeStorageItem(STORAGE_KEYS.SESSION_KEY)
  removeStorageItem(STORAGE_KEYS.PUBLIC_KEY)
  removeStorageItem(STORAGE_KEYS.EXCHANGED)
  removeStorageItem(STORAGE_KEYS.SESSION_ID)
  removeStorageItem(STORAGE_KEYS.VERSION)
}

function generateSessionKey(length = 16) {
  const array = new Uint8Array(length)
  globalThis.crypto.getRandomValues(array)
  return btoa(String.fromCharCode.apply(null, array))
}

restoreKeyState()

export async function fetchPublicKey(axios, forceRefresh = false) {
  if (keyExchangeState.publicKey && !forceRefresh && keyExchangeState.exchanged) {
    return keyExchangeState.publicKey
  }

  const res = await axios.get('/crypto/public-key', { encrypt: false, needToken: false })
  const publicKey = res?.data?.publicKey
  if (!publicKey) {
    throw new Error(res?.message || res?.msg || '获取公钥失败')
  }

  keyExchangeState.publicKey = publicKey
  saveKeyState()
  return publicKey
}

export async function exchangeKey(axios, sessionId) {
  const normalizedSessionId = String(sessionId || '').trim()
  if (keyExchangeState.exchanged && keyExchangeState.sessionId === normalizedSessionId) {
    return true
  }

  if (keyExchangeState.exchanged && keyExchangeState.sessionId !== normalizedSessionId) {
    keyExchangeState.sessionKey = null
    keyExchangeState.exchanged = false
    updateCryptoConfig({ secretKey: '' })
  }

  if (keyExchangeState.exchanging) {
    return new Promise((resolve) => {
      let count = 0
      const timer = setInterval(() => {
        count += 1
        if (!keyExchangeState.exchanging || count > 50) {
          clearInterval(timer)
          resolve(keyExchangeState.exchanged)
        }
      }, 100)
    })
  }

  keyExchangeState.exchanging = true
  try {
    const publicKey = await fetchPublicKey(axios)
    const sessionKey = generateSessionKey(16)
    const encryptedKey = rsaEncrypt(sessionKey, publicKey)
    const headers = normalizedSessionId ? { 'X-Session-Id': normalizedSessionId } : {}
    const res = await axios.post('/crypto/exchange', { encryptedKey }, { headers, encrypt: false })

    if (res?.code === 200 || res?.respCode === '0000') {
      keyExchangeState.sessionKey = sessionKey
      keyExchangeState.sessionId = normalizedSessionId
      keyExchangeState.exchanged = true
      updateCryptoConfig({ secretKey: sessionKey })
      saveKeyState()
      return true
    }

    throw new Error(res?.message || res?.msg || '密钥交换失败')
  }
  catch (error) {
    console.error('密钥交换失败:', error)
    keyExchangeState.publicKey = null
    keyExchangeState.sessionKey = null
    keyExchangeState.sessionId = ''
    keyExchangeState.exchanged = false
    clearStoredKeyState()
    updateCryptoConfig({ secretKey: '' })
    return false
  }
  finally {
    keyExchangeState.exchanging = false
  }
}

export function getSessionKey() {
  return keyExchangeState.sessionKey || cryptoConfig.secretKey
}

export function isKeyExchanged() {
  return keyExchangeState.exchanged
}

export function resetKeyExchange() {
  keyExchangeState.publicKey = null
  keyExchangeState.sessionKey = null
  keyExchangeState.sessionId = ''
  keyExchangeState.exchanged = false
  keyExchangeState.exchanging = false
  clearStoredKeyState()
  updateCryptoConfig({ secretKey: '' })
}

export async function initKeyExchange(axios, sessionId) {
  if (!cryptoConfig.enabled || !cryptoConfig.enableApiCrypto) {
    return true
  }
  if (!cryptoConfig.enableDynamicKey) {
    return true
  }

  return exchangeKey(axios, sessionId)
}
