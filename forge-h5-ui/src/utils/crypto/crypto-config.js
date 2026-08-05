const REQUIRED_API_EXCLUDE_PATHS = [
  '/auth/captcha',
  '/auth/captcha/**',
  '/auth/login',
  '/auth/loginConfig',
  '/crypto/config',
  '/crypto/public-key',
  '/crypto/exchange',
]

const REQUIRED_REPLAY_EXCLUDE_PATHS = [
  '/auth/captcha',
  '/auth/captcha/**',
  '/auth/loginConfig',
  '/crypto/config',
  '/crypto/public-key',
  '/crypto/exchange',
]

/**
 * H5 加解密运行配置。服务端配置加载失败时保持安全默认开启。
 */
export const cryptoConfig = {
  enabled: true,
  enableApiCrypto: true,
  enableFieldCrypto: true,
  algorithm: 'SM4',
  secretKey: '',
  enableDynamicKey: true,
  enableReplay: false,
  replayIncludePaths: [],
  replayExcludePaths: [...REQUIRED_REPLAY_EXCLUDE_PATHS],
  includePaths: [],
  excludePaths: [...REQUIRED_API_EXCLUDE_PATHS],
}

let runtimeConfigRequest = null

function normalizeBoolean(value, fallback) {
  if (typeof value === 'boolean') return value
  if (value === 'true' || value === 1 || value === '1') return true
  if (value === 'false' || value === 0 || value === '0') return false
  return fallback
}

function normalizePaths(value, fallback, required = []) {
  const source = Array.isArray(value) ? value : fallback
  return [...new Set([
    ...source.map(item => String(item || '').trim()).filter(Boolean),
    ...required,
  ])]
}

/**
 * 只接收移动端需要的公开运行字段，忽略所有密钥材料。
 */
export function normalizeRuntimeCryptoConfig(config = {}) {
  const source = config && typeof config === 'object' ? config : {}
  const algorithm = String(source.algorithm || 'SM4').toUpperCase()
  return {
    enabled: normalizeBoolean(source.enabled, true),
    enableApiCrypto: normalizeBoolean(source.enableApiCrypto, true),
    enableFieldCrypto: normalizeBoolean(source.enableFieldCrypto, true),
    algorithm: ['SM4', 'AES'].includes(algorithm) ? algorithm : 'SM4',
    enableDynamicKey: normalizeBoolean(source.enableDynamicKey, true),
    enableReplay: normalizeBoolean(
      source.enableReplay ?? source.enableReplayProtection,
      false,
    ),
    replayIncludePaths: normalizePaths(source.replayIncludePaths, []),
    replayExcludePaths: normalizePaths(
      source.replayExcludePaths,
      REQUIRED_REPLAY_EXCLUDE_PATHS,
      REQUIRED_REPLAY_EXCLUDE_PATHS,
    ),
    includePaths: normalizePaths(source.includePaths, []),
    excludePaths: normalizePaths(
      source.excludePaths,
      REQUIRED_API_EXCLUDE_PATHS,
      REQUIRED_API_EXCLUDE_PATHS,
    ),
  }
}

export function applyRuntimeCryptoConfig(config) {
  const normalized = normalizeRuntimeCryptoConfig(config)
  Object.assign(cryptoConfig, normalized)
  return normalized
}

function requestRuntimeCryptoConfig() {
  if (typeof uni === 'undefined' || typeof uni.request !== 'function') {
    return Promise.reject(new Error('当前运行环境不支持读取加解密配置'))
  }

  const prefix = String(import.meta.env.VITE_REQUEST_PREFIX || '').replace(/\/+$/, '')
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${prefix}/crypto/config`,
      method: 'GET',
      header: { Accept: 'application/json' },
      success: (response) => {
        if (response?.statusCode < 200 || response?.statusCode >= 300) {
          reject(new Error(`HTTP ${response?.statusCode || 'unknown'}`))
          return
        }
        const payload = response?.data
        if (payload?.code !== 200 || !payload?.data) {
          reject(new Error(payload?.message || payload?.msg || '运行配置响应无效'))
          return
        }
        resolve(payload.data)
      },
      fail: reject,
    })
  })
}

/**
 * 从当前 H5 后端读取统一运行配置。
 */
export async function loadRuntimeCryptoConfig() {
  if (!runtimeConfigRequest) {
    runtimeConfigRequest = requestRuntimeCryptoConfig()
      .then(applyRuntimeCryptoConfig)
      .catch((error) => {
        console.warn('[Crypto] 加解密运行配置加载失败，保持安全默认开启:', error)
        return null
      })
      .finally(() => {
        runtimeConfigRequest = null
      })
  }
  return runtimeConfigRequest
}

export function matchPath(path, pattern) {
  if (!pattern) return false

  const regexPattern = pattern
    .replace(/\*\*/g, '.*')
    .replace(/\*/g, '[^/]*')
  return new RegExp(`^${regexPattern}$`).test(path)
}

export function shouldEncrypt(url = '') {
  if (!cryptoConfig.enabled || !cryptoConfig.enableApiCrypto) return false

  const path = url.split('?')[0]
  if (cryptoConfig.excludePaths.some(pattern => matchPath(path, pattern))) return false
  if (!cryptoConfig.includePaths.length) return true
  return cryptoConfig.includePaths.some(pattern => matchPath(path, pattern))
}

export function updateCryptoConfig(config) {
  Object.assign(cryptoConfig, config)
}
