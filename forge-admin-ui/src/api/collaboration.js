import { request } from '@/utils'

/**
 * 企业协同集成 API（连接/应用/能力绑定/同步/映射/投递/回调）
 * 后端接口类级 @ApiDecrypt/@ApiEncrypt，加解密由全局拦截器透明处理
 */

const BASE = '/system/collaboration'

// ==================== 连接与应用 ====================

export function getConnectionDetail(id) {
  return request.get(`${BASE}/connections/${id}`)
}

export function deleteConnection(id) {
  return request.delete(`${BASE}/connections/${id}`)
}

export function listConnectionApps(id) {
  return request.get(`${BASE}/connections/${id}/apps`)
}

export function createConnectionApp(id, data) {
  return request.post(`${BASE}/connections/${id}/apps`, data)
}

export function updateConnectionApp(id, data) {
  return request.put(`${BASE}/connections/${id}/apps`, data)
}

export function deleteConnectionApp(id, appId) {
  return request.delete(`${BASE}/connections/${id}/apps/${appId}`)
}

export function bindConnectionCapability(id, data) {
  return request.post(`${BASE}/connections/${id}/bindings`, data)
}

export function unbindConnectionCapability(id, capability) {
  return request.delete(`${BASE}/connections/${id}/bindings/${capability}`)
}

export function testConnection(id, capability) {
  return request.post(`${BASE}/connections/${id}/test`, null, {
    params: { capability },
  })
}

export function triggerConnectionSync(id, data) {
  return request.post(`${BASE}/connections/${id}/sync`, data)
}

// ==================== 运维查询 ====================

export function resolveSyncIssue(id, data) {
  return request.post(`${BASE}/sync-issues/${id}/resolve`, data)
}

export function listMappings(type, connectionId) {
  return request.get(`${BASE}/mappings/${type}`, {
    params: { connectionId },
  })
}

export function retryDelivery(id) {
  return request.post(`${BASE}/deliveries/${id}/retry`)
}

/**
 * 加载连接下拉选项（运维页筛选共用）
 */
export async function fetchConnectionOptions() {
  const res = await request.get(`${BASE}/connections/page`, {
    params: { pageNum: 1, pageSize: 100 },
  })
  if (res.code !== 200)
    return []
  return (res.data?.records || []).map(item => ({
    label: `${item.connectionName || item.connectionCode}（${item.platform}）`,
    value: item.id,
  }))
}
