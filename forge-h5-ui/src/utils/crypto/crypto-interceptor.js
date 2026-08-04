import { aesDecrypt, aesEncrypt } from './aes'
import { cryptoConfig, shouldEncrypt } from './crypto-config'
import { decodeKeyToHex, sm4Decrypt, sm4Encrypt } from './sm4'

function isKeyValid() {
  return !!cryptoConfig.secretKey
}

function encrypt(data, algorithm = cryptoConfig.algorithm) {
  const key = cryptoConfig.secretKey
  if (!key) {
    throw new Error('加密密钥未设置')
  }

  if (algorithm === 'SM4') {
    return sm4Encrypt(data, decodeKeyToHex(key))
  }
  if (algorithm === 'AES') {
    return aesEncrypt(data, key)
  }

  throw new Error(`不支持的加密算法: ${algorithm}`)
}

function decrypt(data, algorithm = cryptoConfig.algorithm) {
  const key = cryptoConfig.secretKey
  if (!key) {
    throw new Error('解密密钥未设置')
  }

  if (algorithm === 'SM4') {
    return sm4Decrypt(data, decodeKeyToHex(key))
  }
  if (algorithm === 'AES') {
    return aesDecrypt(data, key)
  }

  throw new Error(`不支持的加密算法: ${algorithm}`)
}

export function encryptRequest(config) {
  config.headers = config.headers || {}

  if (config.encrypt !== true || !shouldEncrypt(config.url)) {
    return config
  }

  if (!isKeyValid()) {
    const error = new Error('安全通道未建立，已阻止明文请求')
    error.code = 'ENCRYPT_REQUEST_FAILED'
    throw error
  }

  if (config.data && typeof config.data === 'object') {
    const jsonData = JSON.stringify(config.data)
    config.data = {
      data: encrypt(jsonData),
      algorithm: cryptoConfig.algorithm,
    }
  }

  return config
}

export function decryptResponse(response) {
  if (!response?.data?.data || !response?.data?.algorithm) {
    return response
  }

  if (!isKeyValid()) {
    console.warn('解密密钥未设置，跳过响应解密')
    return response
  }

  try {
    response.data = JSON.parse(decrypt(response.data.data, response.data.algorithm))
  }
  catch (error) {
    const decryptError = new Error('DECRYPT_ERROR')
    decryptError.originalError = error
    throw decryptError
  }

  return response
}

export { decrypt, encrypt }
