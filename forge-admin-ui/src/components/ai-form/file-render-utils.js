const IMAGE_EXTENSIONS = new Set([
  'avif',
  'bmp',
  'gif',
  'ico',
  'jpeg',
  'jpg',
  'png',
  'svg',
  'webp',
])

export function splitFileValues(value) {
  const values = Array.isArray(value) ? value : [value]
  return values
    .flatMap(item => String(item ?? '').split(/[、,，]/))
    .map(item => item.trim())
    .filter(Boolean)
}

export function isImageFileName(fileName) {
  if (!fileName)
    return false
  const normalized = String(fileName).split(/[?#]/, 1)[0]
  const match = normalized.match(/\.([a-z0-9]+)$/i)
  return Boolean(match && IMAGE_EXTENSIONS.has(match[1].toLowerCase()))
}

export function resolveFileRenderItems(value, displayName) {
  const values = splitFileValues(value)
  const names = splitFileValues(displayName)
  return values.map((fileValue, index) => ({
    value: fileValue,
    name: names[index] || (values.length === 1 ? names[0] : '') || fileValue,
  }))
}
