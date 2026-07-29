export function resolveApplicationCreateResult(data, fallbackApplicationCode = '') {
  const result = data && typeof data === 'object' ? data : { id: data }
  const id = result.id ?? null
  const applicationCode = String(result.applicationCode || fallbackApplicationCode || '').trim()
  if (id === null || id === undefined || id === '')
    throw new Error('应用创建成功，但未返回应用 ID')
  if (!applicationCode)
    throw new Error('应用创建成功，但未返回应用编码')
  return { id, applicationCode }
}
