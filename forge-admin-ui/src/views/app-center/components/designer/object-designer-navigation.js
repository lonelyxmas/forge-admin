export const standaloneObjectDesignerSections = [
  { key: 'basic', label: '基本信息' },
  { key: 'fields', label: '字段设计' },
  { key: 'data-model', label: '数据模型' },
  { key: 'default-view', label: '默认视图' },
  { key: 'triggers', label: '触发器' },
]

const DATA_MODEL_PANELS = new Set(['data-model', 'relations', 'flow-app', 'permission'])
const DEFAULT_VIEW_PANELS = new Set(['default-view', 'list', 'detail', 'form', 'actions'])

export function resolveStandaloneObjectDesignerSection(value) {
  const panel = String(value || '').trim()
  if (DATA_MODEL_PANELS.has(panel))
    return 'data-model'
  if (DEFAULT_VIEW_PANELS.has(panel))
    return 'default-view'
  if (standaloneObjectDesignerSections.some(item => item.key === panel))
    return panel
  return 'fields'
}

export function resolveDataModelTab(value) {
  const panel = String(value || '').trim()
  return ['relations', 'flow-app', 'permission'].includes(panel) ? panel : 'relations'
}

export function resolveDefaultViewTab(value) {
  return String(value || '').trim() === 'detail' ? 'detail' : 'list'
}
