export function normalizeConditionDataType(type) {
  const raw = String(type || '').toLowerCase()
  if (['number', 'integer', 'decimal', 'inputnumber', 'slider', 'rate'].some(key => raw.includes(key)))
    return 'number'
  if (['boolean', 'switch', 'checkbox'].some(key => raw.includes(key)))
    return 'boolean'
  if (['date', 'time'].some(key => raw.includes(key)))
    return 'datetime'
  if (['select', 'radio', 'enum', 'cascader', 'tree'].some(key => raw.includes(key)))
    return 'enum'
  return 'string'
}

export function createConditionRule(field = '') {
  return {
    field,
    operator: 'eq',
    value: '',
    endValue: '',
  }
}

export function normalizeConditionRule(rule = {}) {
  return {
    field: String(rule.field || '').trim(),
    operator: normalizeRuleOperator(rule.operator),
    value: rule.value ?? '',
    endValue: rule.endValue ?? '',
  }
}

function normalizeRuleOperator(operator) {
  const normalized = String(operator || 'eq').trim().toLowerCase().replaceAll('-', '_')
  const aliases = {
    neq: 'ne',
    gte: 'ge',
    lte: 'le',
    notcontains: 'notContains',
    not_contains: 'notContains',
    is_null: 'empty',
    notempty: 'notEmpty',
    not_empty: 'notEmpty',
    not_null: 'notEmpty',
  }
  return aliases[normalized] || normalized
}

export function conditionNeedsValue(operator) {
  return !['empty', 'notEmpty'].includes(operator)
}

export function conditionIsBetween(operator) {
  return operator === 'between'
}

export function buildConditionExpression(rules, logic = 'all', fields = []) {
  const fieldMetaMap = createFieldMetaMap(fields)
  const expressions = (rules || [])
    .map(normalizeConditionRule)
    .map(rule => buildRuleExpression(rule, fieldMetaMap))
    .filter(Boolean)
  if (!expressions.length)
    return ''
  const joiner = logic === 'any' || logic === 'OR' ? ' || ' : ' && '
  return `\${${expressions.join(joiner)}}`
}

export function parseConditionExpression(condition, fields = []) {
  const fieldMetaMap = createFieldMetaMap(fields)
  const expression = unwrapExpression(condition)
  if (!expression || !fieldMetaMap.size)
    return { logic: 'all', rules: [] }

  const orParts = splitTopLevel(expression, '||')
  const logic = orParts.length > 1 ? 'any' : 'all'
  const parts = logic === 'any' ? orParts : splitTopLevel(expression, '&&')
  const rules = parts
    .map(part => parseRuleExpression(part, fieldMetaMap))
    .filter(Boolean)

  if (rules.length !== parts.length)
    return { logic: 'all', rules: [] }
  return { logic, rules }
}

function buildRuleExpression(rule, fieldMetaMap) {
  const field = rule.field
  const operator = rule.operator || 'eq'
  if (!field)
    return ''

  if (operator === 'empty')
    return `(${field} == null || ${field} == '')`
  if (operator === 'notEmpty')
    return `(${field} != null && ${field} != '')`

  const meta = fieldMetaMap.get(field) || {}
  const value = formatValue(rule.value, meta.dataType)
  if (!value)
    return ''

  if (operator === 'between') {
    const endValue = formatValue(rule.endValue, meta.dataType)
    if (!endValue)
      return ''
    return `(${field} >= ${value} && ${field} <= ${endValue})`
  }
  if (operator === 'contains')
    return `(${field} != null && ${field}.contains(${value}))`
  if (operator === 'notContains')
    return `(${field} == null || !${field}.contains(${value}))`

  const symbolMap = {
    eq: '==',
    ne: '!=',
    gt: '>',
    ge: '>=',
    lt: '<',
    le: '<=',
  }
  return `${field} ${symbolMap[operator] || '=='} ${value}`
}

function formatValue(value, dataType) {
  const raw = String(value ?? '').trim()
  if (!raw)
    return ''
  if (dataType === 'number' && Number.isFinite(Number(raw)))
    return raw
  if (dataType === 'boolean' && ['true', 'false'].includes(raw.toLowerCase()))
    return raw.toLowerCase()
  return `'${raw.replaceAll('\\', '\\\\').replaceAll('\'', '\\\'')}'`
}

function createFieldMetaMap(fields) {
  if (fields instanceof Map)
    return fields
  const result = new Map()
  for (const item of fields || []) {
    const field = item?.field || item?.fieldName || item?.fieldCode || item?.name || item?.key || item?.code
    if (!field || result.has(field))
      continue
    result.set(field, {
      ...item,
      field,
      dataType: normalizeConditionDataType(item?.dataType || item?.componentType || item?.type),
    })
  }
  return result
}

function unwrapExpression(condition) {
  const raw = String(condition || '').trim()
  if (!raw)
    return ''
  if (raw.startsWith('${') && raw.endsWith('}'))
    return raw.slice(2, -1).trim()
  return raw
}

function splitTopLevel(expression, operator) {
  const parts = []
  let start = 0
  let depth = 0
  let quote = ''
  for (let index = 0; index < expression.length; index += 1) {
    const char = expression[index]
    const previous = expression[index - 1]
    if (quote) {
      if (char === quote && previous !== '\\')
        quote = ''
      continue
    }
    if (char === '\'' || char === '"') {
      quote = char
      continue
    }
    if (char === '(') {
      depth += 1
      continue
    }
    if (char === ')') {
      depth = Math.max(0, depth - 1)
      continue
    }
    if (depth === 0 && expression.slice(index, index + operator.length) === operator) {
      parts.push(expression.slice(start, index).trim())
      start = index + operator.length
      index += operator.length - 1
    }
  }
  parts.push(expression.slice(start).trim())
  return parts.filter(Boolean)
}

function parseRuleExpression(expression, fieldMetaMap) {
  const raw = trimPairParentheses(expression)
  return parseEmptyRule(raw, fieldMetaMap)
    || parseContainsRule(raw, fieldMetaMap)
    || parseBetweenRule(raw, fieldMetaMap)
    || parseBinaryRule(raw, fieldMetaMap)
}

function trimPairParentheses(expression) {
  let raw = String(expression || '').trim()
  while (raw.startsWith('(') && raw.endsWith(')') && hasWrappingParentheses(raw))
    raw = raw.slice(1, -1).trim()
  return raw
}

function hasWrappingParentheses(expression) {
  let depth = 0
  let quote = ''
  for (let index = 0; index < expression.length; index += 1) {
    const char = expression[index]
    const previous = expression[index - 1]
    if (quote) {
      if (char === quote && previous !== '\\')
        quote = ''
      continue
    }
    if (char === '\'' || char === '"') {
      quote = char
      continue
    }
    if (char === '(')
      depth += 1
    if (char === ')')
      depth -= 1
    if (depth === 0 && index < expression.length - 1)
      return false
  }
  return depth === 0
}

function parseEmptyRule(expression, fieldMetaMap) {
  const emptyMatch = expression.match(/^([A-Za-z_$][\w$]*)\s*==\s*null\s*\|\|\s*\1\s*==\s*''$/)
  if (emptyMatch && fieldMetaMap.has(emptyMatch[1]))
    return { field: emptyMatch[1], operator: 'empty', value: '', endValue: '' }

  const notEmptyMatch = expression.match(/^([A-Za-z_$][\w$]*)\s*!=\s*null\s*&&\s*\1\s*!=\s*''$/)
  if (notEmptyMatch && fieldMetaMap.has(notEmptyMatch[1]))
    return { field: notEmptyMatch[1], operator: 'notEmpty', value: '', endValue: '' }

  return null
}

function parseBetweenRule(expression, fieldMetaMap) {
  const match = expression.match(/^([A-Z_$][\w$]*)\s*>=\s*/i)
  if (!match || !fieldMetaMap.has(match[1]))
    return null
  const field = match[1]
  const parts = splitTopLevel(expression.slice(match[0].length), '&&')
  if (parts.length !== 2)
    return null
  const endPrefix = `${field} <=`
  if (!parts[1].startsWith(endPrefix))
    return null
  return {
    field,
    operator: 'between',
    value: parseLiteralValue(parts[0]),
    endValue: parseLiteralValue(parts[1].slice(endPrefix.length)),
  }
}

function parseContainsRule(expression, fieldMetaMap) {
  const containsMatch = expression.match(/^([A-Za-z_$][\w$]*)\s*!=\s*null\s*&&\s*\1\.contains\(/)
  if (containsMatch && fieldMetaMap.has(containsMatch[1]) && expression.endsWith(')')) {
    return {
      field: containsMatch[1],
      operator: 'contains',
      value: parseLiteralValue(expression.slice(containsMatch[0].length, -1)),
      endValue: '',
    }
  }

  const notContainsMatch = expression.match(/^([A-Za-z_$][\w$]*)\s*==\s*null\s*\|\|\s*!\1\.contains\(/)
  if (notContainsMatch && fieldMetaMap.has(notContainsMatch[1]) && expression.endsWith(')')) {
    return {
      field: notContainsMatch[1],
      operator: 'notContains',
      value: parseLiteralValue(expression.slice(notContainsMatch[0].length, -1)),
      endValue: '',
    }
  }

  return null
}

function parseBinaryRule(expression, fieldMetaMap) {
  const match = expression.match(/^([A-Z_$][\w$]*)\s*(==|!=|>=|<=|>|<)\s*/i)
  if (!match || !fieldMetaMap.has(match[1]))
    return null
  const value = expression.slice(match[0].length).trim()
  if (!value)
    return null
  const operatorMap = {
    '==': 'eq',
    '!=': 'ne',
    '>': 'gt',
    '>=': 'ge',
    '<': 'lt',
    '<=': 'le',
  }
  return {
    field: match[1],
    operator: operatorMap[match[2]] || 'eq',
    value: parseLiteralValue(value),
    endValue: '',
  }
}

function parseLiteralValue(value) {
  const raw = String(value || '').trim()
  if ((raw.startsWith('\'') && raw.endsWith('\'')) || (raw.startsWith('"') && raw.endsWith('"')))
    return raw.slice(1, -1).replaceAll('\\\'', '\'').replaceAll('\\\\', '\\')
  return raw
}
