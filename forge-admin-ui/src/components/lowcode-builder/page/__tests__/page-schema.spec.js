import { describe, expect, it } from 'vitest'
import {
  buildGridSyncModelSchema,
  createDefaultListGridLayout,
  syncGridLayoutWithModel,
} from '../page-schema'

describe('default list columns', () => {
  it('excludes audit/system fields from the default CRUD list columns', () => {
    const modelSchema = {
      appType: 'SIMPLE',
      fields: [
        { field: 'id', columnName: 'id' },
        { field: 'customerName', columnName: 'customer_name', listVisible: true },
        { field: 'amount', columnName: 'amount', listVisible: true },
        { field: 'createBy', columnName: 'create_by' },
        { field: 'createTime', columnName: 'create_time' },
        { field: 'createDept', columnName: 'create_dept' },
        { field: 'updateBy', columnName: 'update_by' },
        { field: 'updateTime', columnName: 'update_time' },
      ],
    }
    const layout = createDefaultListGridLayout(modelSchema, { layoutType: 'simple-crud' })
    const crud = layout.items.find(item => item.blockType === 'AiCrudPage')
    expect(crud.fieldRefs).toContain('customerName')
    expect(crud.fieldRefs).toContain('amount')
    expect(crud.fieldRefs).toContain('id')
    expect(crud.fieldRefs).not.toContain('createBy')
    expect(crud.fieldRefs).not.toContain('createTime')
    expect(crud.fieldRefs).not.toContain('createDept')
    expect(crud.fieldRefs).not.toContain('updateBy')
    expect(crud.fieldRefs).not.toContain('updateTime')
  })
})

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
