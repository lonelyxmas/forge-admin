const REQUIRED_API_EXCLUDE_PATHS = [
  '/auth/captcha',
  '/auth/captcha/**',
  '/auth/login',
  '/auth/loginConfig',
  '/crypto/config',
  '/crypto/public-key',
  '/crypto/exchange',
  '/api/file/upload',
  '/api/file/upload/**'
]

const REQUIRED_REPLAY_EXCLUDE_PATHS = [
  '/auth/captcha',
  '/auth/captcha/**',
  '/auth/loginConfig',
  '/crypto/config',
  '/crypto/public-key',
  '/crypto/exchange'
]

export const cryptoConfig = {
  enabled: true,
  enableApiCrypto: true,
  enableFieldCrypto: true,
  algorithm: 'SM4',
  secretKey: '',
  enableDynamicKey: true,
  enableReplay: false,
  replayIncludePaths: [] as string[],
  replayExcludePaths: [...REQUIRED_REPLAY_EXCLUDE_PATHS],
  includePaths: [] as string[],
  excludePaths: [...REQUIRED_API_EXCLUDE_PATHS]
}

let runtimeConfigRequest: Promise<ReturnType<typeof applyRuntimeCryptoConfig> | null> | null = null

function normalizeBoolean(value: unknown, fallback: boolean): boolean {
  if (typeof value === 'boolean') return value
  if (value === 'true' || value === 1 || value === '1') return true
  if (value === 'false' || value === 0 || value === '0') return false
  return fallback
}

function normalizePaths(value: unknown, fallback: string[], required: string[] = []): string[] {
  const source = Array.isArray(value) ? value : fallback
  return [...new Set([
    ...source.map(item => String(item || '').trim()).filter(Boolean),
    ...required
  ])]
}

export function normalizeRuntimeCryptoConfig(config: Record<string, unknown> = {}) {
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
      false
    ),
    replayIncludePaths: normalizePaths(source.replayIncludePaths, []),
    replayExcludePaths: normalizePaths(
      source.replayExcludePaths,
      REQUIRED_REPLAY_EXCLUDE_PATHS,
      REQUIRED_REPLAY_EXCLUDE_PATHS
    ),
    includePaths: normalizePaths(source.includePaths, []),
    excludePaths: normalizePaths(
      source.excludePaths,
      REQUIRED_API_EXCLUDE_PATHS,
      REQUIRED_API_EXCLUDE_PATHS
    )
  }
}

export function applyRuntimeCryptoConfig(config: Record<string, unknown>) {
  const normalized = normalizeRuntimeCryptoConfig(config)
  Object.assign(cryptoConfig, normalized)
  return normalized
}

/**
 * 从报表服务读取安全裁剪后的统一运行配置。
 */
export async function loadRuntimeCryptoConfig(fetchImpl: typeof fetch = globalThis.fetch) {
  if (runtimeConfigRequest) return runtimeConfigRequest
  if (typeof fetchImpl !== 'function') return null

  runtimeConfigRequest = fetchImpl('/forge-report-api/crypto/config', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' }
  })
    .then(async response => {
      if (!response.ok) throw new Error(`HTTP ${response.status || 'unknown'}`)
      const payload = await response.json()
      if (payload?.code !== 200 || !payload?.data) {
        throw new Error(payload?.message || payload?.msg || '运行配置响应无效')
      }
      return applyRuntimeCryptoConfig(payload.data)
    })
    .catch(error => {
      console.warn('[Crypto] 加解密运行配置加载失败，保持安全默认开启:', error)
      return null
    })
    .finally(() => {
      runtimeConfigRequest = null
    })

  return runtimeConfigRequest
}

export function matchPath(path: string, pattern: string): boolean {
  if (!pattern) return false
  const normalizedPath = path.replace(/^\/forge-report-api/, '')
  const regexPattern = pattern.replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*')
  const regex = new RegExp(`^${regexPattern}$`)
  return regex.test(path) || regex.test(normalizedPath)
}

export function shouldEncrypt(url = ''): boolean {
  if (!cryptoConfig.enabled || !cryptoConfig.enableApiCrypto) return false
  const path = url.split('?')[0]

  if (cryptoConfig.excludePaths.some(pattern => matchPath(path, pattern))) return false
  if (!cryptoConfig.includePaths.length) return true
  return cryptoConfig.includePaths.some(pattern => matchPath(path, pattern))
}

export function updateCryptoConfig(config: Partial<typeof cryptoConfig>) {
  Object.assign(cryptoConfig, config)
}
