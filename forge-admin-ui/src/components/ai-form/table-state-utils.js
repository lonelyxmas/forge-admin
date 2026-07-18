export const AI_TABLE_CHECKED_ROW_CLASS = 'ai-table-row--checked'

/**
 * 合并页面自定义行类名与 AiTable 受控选中态类名。
 *
 * @param {string | Function | undefined} rowClassName 页面传入的行类名或解析函数
 * @param {object} row 当前行
 * @param {number} index 当前行索引
 * @param {boolean} checked 当前行是否选中
 * @returns {string} 合并并去重后的类名
 */
export function resolveTableRowClassName(rowClassName, row, index, checked) {
  const customClassName = typeof rowClassName === 'function'
    ? rowClassName(row, index)
    : rowClassName
  const classes = String(customClassName || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)

  if (checked)
    classes.push(AI_TABLE_CHECKED_ROW_CLASS)

  return [...new Set(classes)].join(' ')
}
