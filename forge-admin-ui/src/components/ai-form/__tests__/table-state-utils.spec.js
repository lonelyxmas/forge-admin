import { describe, expect, it } from 'vitest'
import { AI_TABLE_CHECKED_ROW_CLASS, resolveTableRowClassName } from '../table-state-utils'

describe('resolveTableRowClassName', () => {
  const row = { id: 1, status: 'enabled' }

  it('adds the checked class while preserving a string row class', () => {
    expect(resolveTableRowClassName('business-row', row, 0, true))
      .toBe(`business-row ${AI_TABLE_CHECKED_ROW_CLASS}`)
  })

  it('evaluates a row class function with the current row and index', () => {
    const rowClassName = (currentRow, index) => currentRow.status === 'enabled' && index === 2
      ? 'enabled-row'
      : ''

    expect(resolveTableRowClassName(rowClassName, row, 2, true))
      .toBe(`enabled-row ${AI_TABLE_CHECKED_ROW_CLASS}`)
  })

  it('does not add the checked class for unchecked rows', () => {
    expect(resolveTableRowClassName('business-row', row, 0, false)).toBe('business-row')
  })

  it('does not duplicate an existing checked class', () => {
    expect(resolveTableRowClassName(AI_TABLE_CHECKED_ROW_CLASS, row, 0, true))
      .toBe(AI_TABLE_CHECKED_ROW_CLASS)
  })
})
