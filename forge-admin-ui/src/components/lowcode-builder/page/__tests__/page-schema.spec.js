import { describe, expect, it } from 'vitest'
import {
  buildGridSyncModelSchema,
  syncGridLayoutWithModel,
} from '../page-schema'

describe('page grid field synchronization', () => {
  it('keeps current component fields when the stable page model has no fields', () => {
    const modelSchema = buildGridSyncModelSchema(
      { configKey: '', fields: [] },
      [{ field: 'customerName', label: '客户名称', listVisible: true }],
    )
    const layout = syncGridLayoutWithModel({
      cols: 12,
      rowHeight: 32,
      gap: 8,
      designWidth: 1366,
      layoutType: 'simple-crud',
      items: [{
        id: 'crud_1',
        blockType: 'AiCrudPage',
        label: '客户列表',
        gridX: 0,
        gridY: 0,
        gridW: 12,
        gridH: 12,
        fieldRefs: ['customerName'],
        props: {
          searchFieldRefs: ['customerName'],
          fieldSettings: { customerName: { visible: true } },
        },
      }],
    }, modelSchema)

    expect(layout.items[0].fieldRefs).toEqual(['customerName'])
    expect(layout.items[0].props.searchFieldRefs).toEqual(['customerName'])
    expect(layout.items[0].props.fieldSettings.customerName.visible).toBe(true)
  })
})
