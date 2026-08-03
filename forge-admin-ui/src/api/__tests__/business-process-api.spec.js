import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  businessProcessDesigner,
  businessProcessDetail,
  businessProcessPage,
  copyBusinessProcess,
  createBusinessProcess,
  deleteBusinessProcess,
  saveBusinessProcessSchema,
  updateBusinessProcess,
  updateBusinessProcessStatus,
  validateBusinessProcess,
} from '../business-process'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils', () => ({ request: requestMocks }))

const processId = '1900000000000003001'
const applicationId = '1900000000000000001'
const schemaHash = 'a'.repeat(64)
const schema = {
  schemaVersion: '1.0',
  processCode: 'purchase_submit_approval',
  subject: { objectId: '1900000000000001001', objectCode: 'sample_purchase_order' },
  nodes: [],
  edges: [],
  policies: {},
  dependencies: {},
}

describe('business process control-plane API', () => {
  beforeEach(() => {
    Object.values(requestMocks).forEach(mock => mock.mockReset())
  })

  it('uses encrypted endpoints and carries the server CAS hash', () => {
    businessProcessPage({ applicationId, pageNum: 1, pageSize: 10 })
    businessProcessDetail(processId)
    createBusinessProcess({ applicationId })
    copyBusinessProcess(processId, {})
    updateBusinessProcess({ id: processId, processName: '采购审批' })
    businessProcessDesigner(processId)
    saveBusinessProcessSchema(processId, {
      businessProcessJson: schema,
      expectedSchemaHash: schemaHash,
    })
    validateBusinessProcess(processId)
    updateBusinessProcessStatus(processId, 0)
    deleteBusinessProcess(processId)

    expect(requestMocks.get).toHaveBeenCalledWith('/ai/business/process/page', {
      params: { applicationId, pageNum: 1, pageSize: 10 },
      encrypt: true,
    })
    expect(requestMocks.get).toHaveBeenCalledWith(`/ai/business/process/${processId}/designer`, { encrypt: true })
    expect(requestMocks.put).toHaveBeenCalledWith(`/ai/business/process/${processId}/schema`, {
      businessProcessJson: schema,
      expectedSchemaHash: schemaHash,
    }, { encrypt: true })
    expect(requestMocks.post).toHaveBeenCalledWith(`/ai/business/process/${processId}/validate`, null, { encrypt: true })
    expect(requestMocks.put).toHaveBeenCalledWith(`/ai/business/process/${processId}/status`, null, {
      params: { status: 0 },
      encrypt: true,
    })
    expect(requestMocks.delete).toHaveBeenCalledWith(`/ai/business/process/${processId}`, { encrypt: true })
  })
})
