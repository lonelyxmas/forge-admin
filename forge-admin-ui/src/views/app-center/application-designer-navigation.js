export const applicationDesignerSections = [
  { key: 'pages', label: '页面' },
  { key: 'data', label: '数据' },
  { key: 'automation', label: '自动化' },
  { key: 'flow', label: '流程' },
  { key: 'settings', label: '设置' },
]

const legacySectionMap = {
  'page': 'pages',
  'pages': 'pages',
  'events': 'pages',
  'actions': 'flow',
  'automation': 'automation',
  'automation-enhancements': 'automation',
  'business-flow': 'flow',
  'flow': 'flow',
  'data-model': 'data',
  'data': 'data',
  'settings': 'settings',
}

const objectDesignerSectionConfig = {
  'events': {
    initialPanel: 'form',
    initialFormPropertyTab: 'events',
    navPanels: ['form'],
  },
  'business-flow': {
    initialPanel: 'flow-app',
    navPanels: ['flow-app'],
  },
  'data-model': {
    initialPanel: 'fields',
    navPanels: ['fields', 'relations', 'flow-app', 'triggers'],
  },
  'page-form': {
    initialPanel: 'form',
    initialFormPropertyTab: 'basic',
    navPanels: ['form'],
  },
  'page-list': {
    initialPanel: 'list',
    navPanels: ['list'],
  },
  'data-object': {
    initialPanel: 'fields',
    navPanels: ['fields', 'relations'],
  },
  'data-fields': {
    initialPanel: 'fields',
    navPanels: ['fields'],
  },
  'data-relations': {
    initialPanel: 'relations',
    navPanels: ['relations'],
  },
  'automation-triggers': {
    initialPanel: 'triggers',
    navPanels: ['triggers'],
  },
  'flow-object': {
    initialPanel: 'flow-app',
    navPanels: ['flow-app'],
  },
}

export function normalizeApplicationDesignerSection(value) {
  return legacySectionMap[String(value || '').trim()] || 'pages'
}

export function buildApplicationDesignerResourceGroups(options = {}) {
  const objects = Array.isArray(options.objects) ? options.objects : []
  const designersByObjectId = options.designersByObjectId || {}
  const pages = (Array.isArray(options.pages) ? options.pages : []).filter(page => page?.type === 'page')
  const objectNodes = objects.map(object => normalizeObjectResource(object, designersByObjectId)).filter(Boolean)

  const groups = [
    {
      key: 'pages',
      label: '页面',
      nodes: [
        ...objectNodes.flatMap(item => [
          createObjectNode(item, 'page-form', `${item.objectName}（表单页）`, item.formConfigured),
          createObjectNode(item, 'page-list', `${item.objectName}（列表页）`, item.listConfigured),
        ]),
        ...pages.map(page => ({
          key: `page-custom:${String(page.id)}`,
          groupKey: 'pages',
          kind: 'page-custom',
          label: `${page.title || '工作台首页'}（自由编排）`,
          pageId: String(page.id),
          configured: true,
          // 自由编排页面来自导航节点，支持重命名/复制/排序/删除等结构编辑。
          editable: true,
        })),
      ],
    },
    {
      key: 'data',
      label: '数据',
      // 数据结构与关系拆成两个节点，避免内嵌设计器再多一层中间导航栏。
      nodes: objectNodes.flatMap(item => [
        createObjectNode(item, 'data-fields', `${item.objectName} · 数据结构`, true),
        createObjectNode(item, 'data-relations', `${item.objectName} · 关系与级联`, item.relationConfigured),
      ]),
    },
    {
      key: 'automation',
      label: '自动化',
      nodes: [
        {
          key: 'automation-enhancements',
          groupKey: 'automation',
          kind: 'automation-enhancements',
          label: '动作增强（JS / CSS / Java）',
          configured: hasItems(options.extensions),
        },
        ...objectNodes.map(item => createObjectNode(
          item,
          'automation-triggers',
          `${item.objectName} · 触发器`,
          item.triggerConfigured,
        )),
      ],
    },
    {
      key: 'flow',
      label: '流程',
      nodes: objectNodes.map(item => createObjectNode(item, 'flow-object', `${item.objectName}审批`, item.flowConfigured)),
    },
    {
      key: 'settings',
      label: '设置',
      nodes: [{
        key: 'settings',
        groupKey: 'settings',
        kind: 'settings',
        label: '应用设置',
        configured: true,
      }],
    },
  ]
  return groups.map(group => ({
    ...group,
    configuredCount: group.nodes.filter(node => node.configured !== false).length,
    totalCount: group.nodes.length,
  }))
}

export function findApplicationDesignerResource(groups = [], resourceKey = '', legacySection = '') {
  const nodes = (Array.isArray(groups) ? groups : []).flatMap(group => group.nodes || [])
  const requested = String(resourceKey || '').trim()
  if (requested) {
    const matched = nodes.find(node => node.key === requested)
    if (matched)
      return matched
    // 兼容拆分前的 data:<objectId> 旧链接，落到该对象的数据结构节点。
    if (requested.startsWith('data:')) {
      const legacyObjectId = requested.slice('data:'.length)
      const fallback = nodes.find(node => node.kind === 'data-fields' && String(node.objectId) === legacyObjectId)
      if (fallback)
        return fallback
    }
  }

  const legacy = String(legacySection || '').trim()
  if (legacy === 'events')
    return nodes.find(node => node.kind === 'page-form') || nodes[0] || null
  if (legacy === 'page')
    return nodes.find(node => node.kind === 'page-custom') || nodes.find(node => node.groupKey === 'pages') || nodes[0] || null
  if (legacy === 'actions')
    return nodes.find(node => node.kind === 'flow-object') || nodes[0] || null
  if (legacy === 'automation-enhancements')
    return nodes.find(node => node.kind === 'automation-enhancements') || nodes[0] || null
  if (legacy === 'business-flow')
    return nodes.find(node => node.kind === 'flow-object') || nodes[0] || null
  if (legacy === 'data-model')
    return nodes.find(node => node.kind === 'data-fields') || nodes.find(node => node.kind === 'data-object') || nodes[0] || null
  if (legacy === 'settings')
    return nodes.find(node => node.kind === 'settings') || nodes[0] || null

  const groupKey = normalizeApplicationDesignerSection(legacy)
  return nodes.find(node => node.groupKey === groupKey)
    || nodes.find(node => node.kind === 'page-form')
    || nodes[0]
    || null
}

export function resolveApplicationDesignerObject(objects = [], selectedObjectId = null) {
  if (!Array.isArray(objects) || !objects.length)
    return null
  const selected = objects.find(item => String(item?.objectId) === String(selectedObjectId))
  return selected || objects.find(item => item?.objectRole === 'PRIMARY') || objects[0]
}

export function resolveObjectDesignerSectionConfig(sectionOrResource) {
  const key = typeof sectionOrResource === 'object'
    ? sectionOrResource?.kind
    : String(sectionOrResource || '').trim()
  return objectDesignerSectionConfig[key] || null
}

function normalizeObjectResource(object = {}, designersByObjectId = {}) {
  const objectId = String(object.objectId ?? object.id ?? '')
  if (!objectId)
    return null
  const designer = designersByObjectId[objectId] || designersByObjectId[object.objectId] || {}
  const designerOptions = parseObject(designer.designerOptions)
  const documentConfig = parseObject(designer.documentConfig)
  const mainFlowSummary = parseObject(documentConfig.mainFlowSummary)
  return {
    objectId,
    objectCode: object.objectCode || '',
    objectName: object.objectName || object.objectCode || '未命名对象',
    formConfigured: hasFormConfiguration(designer.formDesignerSchema),
    listConfigured: hasListConfiguration(designer.viewSchema),
    triggerConfigured: designer.triggerConfigured === true || Number(designer.triggerCount || 0) > 0,
    actionConfigured: hasItems(designerOptions.actions),
    relationConfigured: hasRelationConfiguration(designer.relationSchema),
    flowConfigured: Boolean(
      mainFlowSummary.configured
      || mainFlowSummary.flowModelKey
      || documentConfig.defaultFlowKey,
    ),
  }
}

function createObjectNode(object, kind, label, configured) {
  return {
    key: `${kind.replace('-object', '')}:${object.objectId}`,
    groupKey: resolveNodeGroup(kind),
    kind,
    label,
    objectId: object.objectId,
    objectCode: object.objectCode,
    configured,
  }
}

function resolveNodeGroup(kind) {
  if (kind.startsWith('page-'))
    return 'pages'
  if (kind.startsWith('automation-'))
    return 'automation'
  if (kind.startsWith('flow-'))
    return 'flow'
  if (kind.startsWith('data-'))
    return 'data'
  return 'settings'
}

function hasRelationConfiguration(value) {
  const schema = parseObject(value)
  return hasItems(schema.relations)
    || hasItems(schema.linkages)
    || hasItems(schema.cascades)
}

function hasFormConfiguration(value) {
  const schema = parseObject(value)
  if (hasItems(schema.components) || hasItems(schema.pageSections))
    return true
  return (Array.isArray(schema.forms) ? schema.forms : []).some(form => hasItems(form?.schema?.components) || hasItems(form?.schema?.pageSections))
}

function hasListConfiguration(value) {
  const schema = parseObject(value)
  const list = schema.list || {}
  const tableZone = (Array.isArray(schema.zones) ? schema.zones : [])
    .find(zone => ['list', 'table'].includes(zone?.zoneKey))
  if (list.enabled === false || tableZone?.enabled === false)
    return false
  return hasItems(list.columns)
    || hasItems(list.fieldRefs)
    || Object.keys(list.settings || {}).length > 0
    || Boolean(tableZone)
}

function hasItems(value) {
  return Array.isArray(value) && value.length > 0
}

function parseObject(value) {
  if (value && typeof value === 'object')
    return value
  if (typeof value !== 'string' || !value.trim())
    return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  }
  catch {
    return {}
  }
}
