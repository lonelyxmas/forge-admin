import { describe, expect, it } from 'vitest'
import { resolveSelectionLabelFields } from '../selection-label-fields'

describe('selection label field isolation', () => {
  it.each([
    ['user', 'applicantId', 'applicantName'],
    ['org', 'departmentId', 'departmentName'],
  ])('never writes a %s label back to its primary ID field', (selectionType, fieldName, expectedLabelField) => {
    const fields = resolveSelectionLabelFields({
      field: fieldName,
      props: {
        labelValueField: fieldName,
        targetField: fieldName,
      },
    }, selectionType)

    expect(fields).not.toContain(fieldName)
    expect(fields).toContain(expectedLabelField)
  })
})
