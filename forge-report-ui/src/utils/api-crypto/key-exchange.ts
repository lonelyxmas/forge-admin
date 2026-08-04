import type { AxiosInstance } from 'axios'
import { getLocalStorage } from '@/utils/storage'
import { StorageEnum } from '@/enums/storageEnum'
import { rsaEncrypt } from '@/utils/rsa'
import { cryptoConfig, updateCryptoConfig } from './crypto-config'

const STORAGE_KEYS = {
  SESSION_KEY: 'report_crypto_session_key',
  PUBLIC_KEY: 'report_crypto_public_key',
  EXCHANGED: 'report_crypto_exchanged',
  SESSION_ID: 'report_crypto_session_id',
  ANONYMOUS_SESSION_ID: 'report_crypto_anonymous_session_id'
}

const keyExchangeState = {
  publicKey: null as string | null,
  sessionKey: null as string | null,
  sessionId: null as string | null,
  exchanged: false,
  exchanging: false
}

function createAnonymousSessionId(): string {
  const id = typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `forge-report-anonymous-${id}`
}

function getAnonymousSessionId(): string {
  const storedSessionId = localStorage.getItem(STORAGE_KEYS.ANONYMOUS_SESSION_ID)
  if (storedSessionId) {
    return storedSessionId
  }

  const sessionId = createAnonymousSessionId()
  localStorage.setItem(STORAGE_KEYS.ANONYMOUS_SESSION_ID, sessionId)
  return sessionId
}

function currentSessionId(): string {
  return getLocalStorage(StorageEnum.GO_ACCESS_TOKEN_STORE) || getAnonymousSessionId()
}

export function getCurrentCryptoSessionId(): string {
  return currentSessionId()
}

function restoreKeyState() {
  try {
    const sessionId = localStorage.getItem(STORAGE_KEYS.SESSION_ID)
    const currentId = currentSessionId()
    if (sessionId && sessionId !== currentId) {
      clearStoredKeyState()
      return
    }

    const sessionKey = localStorage.getItem(STORAGE_KEYS.SESSION_KEY)
    const publicKey = localStorage.getItem(STORAGE_KEYS.PUBLIC_KEY)
    const exchanged = localStorage.getItem(STORAGE_KEYS.EXCHANGED) === 'true'

    if (sessionKey) {
      keyExchangeState.sessionKey = sessionKey
      keyExchangeState.sessionId = sessionId
      keyExchangeState.exchanged = exchanged
      updateCryptoConfig({ secretKey: sessionKey })
    }

    if (publicKey) {
      keyExchangeState.publicKey = publicKey
    }
  } catch (error) {
    console.error('[Crypto] 恢复密钥状态失败:', error)
  }
}

function saveKeyState() {
  try {
    if (keyExchangeState.sessionKey) {
      localStorage.setItem(STORAGE_KEYS.SESSION_KEY, keyExchangeState.sessionKey)
      localStorage.setItem(STORAGE_KEYS.EXCHANGED, String(keyExchangeState.exchanged))
      localStorage.setItem(STORAGE_KEYS.SESSION_ID, keyExchangeState.sessionId || currentSessionId())
    }
    if (keyExchangeState.publicKey) {
      localStorage.setItem(STORAGE_KEYS.PUBLIC_KEY, keyExchangeState.publicKey)
    }
  } catch (error) {
    console.error('[Crypto] 保存密钥状态失败:', error)
  }
}

function clearStoredKeyState() {
  localStorage.removeItem(STORAGE_KEYS.SESSION_KEY)
  localStorage.removeItem(STORAGE_KEYS.PUBLIC_KEY)
  localStorage.removeItem(STORAGE_KEYS.EXCHANGED)
  localStorage.removeItem(STORAGE_KEYS.SESSION_ID)
}

function clearRuntimeKeyState() {
  keyExchangeState.publicKey = null
  keyExchangeState.sessionKey = null
  keyExchangeState.sessionId = null
  keyExchangeState.exchanged = false
  clearStoredKeyState()
  updateCryptoConfig({ secretKey: '' })
}

restoreKeyState()

function generateSessionKey(length = 16): string {
  const array = new Uint8Array(length)
  crypto.getRandomValues(array)
  return btoa(String.fromCharCode.apply(null, Array.from(array)))
}

export async function fetchPublicKey(axios: AxiosInstance, forceRefresh = false): Promise<string> {
  if (keyExchangeState.publicKey && !forceRefresh) {
    return keyExchangeState.publicKey
  }

  const res: any = await axios.get('/forge-report-api/crypto/public-key')
  const publicKey = res?.data?.publicKey || res?.publicKey
  if (!publicKey) throw new Error(res?.message || res?.msg || '获取公钥失败')

  keyExchangeState.publicKey = publicKey
  saveKeyState()
  return publicKey
}

export async function exchangeKey(axios: AxiosInstance): Promise<boolean> {
  const sessionId = currentSessionId()
  if (keyExchangeState.exchanged && keyExchangeState.sessionId === sessionId) {
    return true
  }

  if (keyExchangeState.exchanging) {
    return new Promise(resolve => {
      let count = 0
      const timer = window.setInterval(() => {
        count++
        if (!keyExchangeState.exchanging || count > 50) {
          window.clearInterval(timer)
          resolve(keyExchangeState.exchanged)
        }
      }, 100)
    })
  }

  keyExchangeState.exchanging = true
  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      if (attempt > 0) {
        clearRuntimeKeyState()
      }

      try {
        const publicKey = await fetchPublicKey(axios, attempt > 0)
        const sessionKey = generateSessionKey(16)
        const encryptedKey = rsaEncrypt(sessionKey, publicKey)
        const headers: Record<string, string> = {}

        const token = getLocalStorage(StorageEnum.GO_ACCESS_TOKEN_STORE)
        if (token) {
          headers.Authorization = `Bearer ${token}`
        } else {
          headers['X-Session-Id'] = sessionId
        }

        const res: any = await axios.post('/forge-report-api/crypto/exchange', { encryptedKey }, { headers, encrypt: false } as any)
        if (res?.code !== 200) {
          throw new Error(res?.message || res?.msg || '密钥交换失败')
        }

        keyExchangeState.sessionKey = sessionKey
        keyExchangeState.sessionId = sessionId
        keyExchangeState.exchanged = true
        updateCryptoConfig({ secretKey: sessionKey })
        saveKeyState()
        return true
      } catch (error) {
        if (attempt === 0) {
          console.warn('[Crypto] 密钥交换失败，刷新公钥后重试:', error)
          continue
        }
        throw error
      }
    }
  } catch (error) {
    console.error('[Crypto] 密钥交换失败:', error)
    return false
  } finally {
    keyExchangeState.exchanging = false
  }
}

export async function ensureKeyExchanged(axios: AxiosInstance): Promise<boolean> {
  if (!cryptoConfig.enabled || !cryptoConfig.enableApiCrypto) {
    return true
  }
  if (!cryptoConfig.enableDynamicKey) {
    return true
  }
  restoreKeyState()
  return exchangeKey(axios)
}

export function resetKeyExchange() {
  clearRuntimeKeyState()
  keyExchangeState.exchanged = false
  keyExchangeState.exchanging = false
}
