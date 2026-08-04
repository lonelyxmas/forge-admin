const credentials = new Map()

export function rememberCapabilityCredential(payload) {
  const clientId = payload?.clientId
  if (clientId == null)
    return
  const previous = credentials.get(String(clientId)) || {}
  credentials.set(String(clientId), {
    ...previous,
    ...(payload.clientSecret ? { clientSecret: payload.clientSecret } : {}),
    ...(payload.signingKey ? { signingKey: payload.signingKey } : {}),
    ...(payload.privateKeyPem ? { privateKeyPem: payload.privateKeyPem } : {}),
    updatedAt: Date.now(),
  })
}

export function getCapabilityCredential(clientId) {
  return clientId == null ? null : credentials.get(String(clientId)) || null
}

export function forgetCapabilityCredential(clientId) {
  if (clientId != null)
    credentials.delete(String(clientId))
}
