/**
 * RSA 加密工具
 * 用于登录时对密码进行 RSA 加密
 */
import JSEncrypt from 'jsencrypt-ext'
import axiosInstance from '@/api/axios'

// 缓存公钥
let cachedPublicKey: string | null = null

/**
 * 将裸 Base64 公钥包装为 PEM 格式（jsencrypt 需要 PEM）
 */
function wrapPublicKeyPem(base64Key: string): string {
  // 如果已经是 PEM 格式，直接返回
  if (base64Key.includes('-----BEGIN')) return base64Key
  // 每 64 字符换行
  const lines = base64Key.match(/.{1,64}/g)?.join('\n') || base64Key
  return `-----BEGIN PUBLIC KEY-----\n${lines}\n-----END PUBLIC KEY-----`
}

/**
 * 从后端获取 RSA 公钥
 */
export async function fetchPublicKey(forceRefresh = false): Promise<string> {
  if (cachedPublicKey && !forceRefresh) {
    return cachedPublicKey
  }
  try {
    const res: any = await axiosInstance.get('/forge-report-api/crypto/public-key')
    const rawKey = res?.data?.publicKey || res?.publicKey
    if (!rawKey) throw new Error('获取公钥失败')
    // 包装为 PEM 格式
    cachedPublicKey = wrapPublicKeyPem(rawKey)
    return cachedPublicKey
  } catch (error) {
    console.error('[RSA] 获取公钥失败:', error)
    throw error
  }
}

/**
 * RSA 加密（jsencrypt 返回 Base64 密文）
 */
export function rsaEncrypt(data: string, publicKey: string): string {
  const encrypt = new JSEncrypt()
  encrypt.setPublicKey(wrapPublicKeyPem(publicKey))
  const result = encrypt.encrypt(data)
  if (!result) throw new Error('RSA 加密失败')
  return result as string
}

/**
 * 按登录配置处理密码。启用时先获取公钥并返回 Base64 密文。
 */
export async function encryptPassword(password: string, enabled = true): Promise<string> {
  if (!enabled) return password

  try {
    const publicKey = await fetchPublicKey()
    return rsaEncrypt(password, publicKey)
  } catch (error) {
    console.error('[RSA] 密码加密失败，已阻止明文降级:', error)
    throw new Error('密码加密服务暂不可用，请刷新后重试')
  }
}

/**
 * 清除缓存的公钥（登出时调用）
 */
export function clearPublicKeyCache(): void {
  cachedPublicKey = null
}
