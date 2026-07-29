import { describe, expect, it } from 'vitest'
import { buildModelCode } from '../namingUtils'

describe('form-first naming utils', () => {
  it('keeps composed model codes within the database length contract', () => {
    const modelCode = buildModelCode(
      'procurement_warehouse',
      'customer_inventory_transaction_detail_record_archive',
    )

    expect(modelCode.length).toBeLessThanOrEqual(48)
    expect(modelCode).toMatch(/^procurement_warehouse_/)
  })

  it('does not duplicate an existing suite prefix', () => {
    const modelCode = buildModelCode(
      'procurement_warehouse',
      'procurement_warehouse_customer_inventory_record',
    )

    expect(modelCode.length).toBeLessThanOrEqual(48)
    expect(modelCode).toMatch(/^procurement_warehouse_customer/)
    expect(modelCode).not.toMatch(/^procurement_warehouse_procurement_warehouse/)
  })
})
