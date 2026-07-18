import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AiTable from '../AiTable.vue'
import { AI_TABLE_CHECKED_ROW_CLASS } from '../table-state-utils'

const NDataTableStub = {
  name: 'NDataTable',
  props: ['data', 'rowClassName'],
  template: '<div class="n-data-table-stub" />',
}

describe('aiTable checked row state', () => {
  it('passes a merged checked row class resolver to NDataTable', () => {
    const rows = [
      { id: 1, status: 'enabled' },
      { id: 2, status: 'disabled' },
    ]
    const wrapper = mount(AiTable, {
      props: {
        columns: [{ key: 'status', title: '状态' }],
        dataSource: rows,
        checkedRowKeys: [2],
        rowClassName: row => `status-${row.status}`,
        showToolbar: false,
        pagination: false,
      },
      global: {
        directives: {
          tableScrollEnhance: {},
        },
        stubs: {
          NDataTable: NDataTableStub,
        },
      },
    })

    const rowClassName = wrapper.findComponent(NDataTableStub).props('rowClassName')
    expect(rowClassName(rows[0], 0)).toBe('status-enabled')
    expect(rowClassName(rows[1], 1)).toBe(`status-disabled ${AI_TABLE_CHECKED_ROW_CLASS}`)
  })
})
